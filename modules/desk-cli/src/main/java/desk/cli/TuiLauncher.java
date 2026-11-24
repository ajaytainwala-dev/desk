package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates and launches the Rust {@code desk-tui} binary (Ratatui/Crossterm).
 *
 * <p>The Java CLI launcher is also named {@code desk} on Unix. Candidates that are
 * shell scripts or otherwise not a native binary are rejected so the CLI cannot
 * re-exec itself in an infinite "launching desk TUI" loop.</p>
 */
public final class TuiLauncher {

  private static final Logger LOG = LoggerFactory.getLogger(TuiLauncher.class);

  private TuiLauncher() {
  }

  public static int launch(String... extraArgs) {
    Path editorBinary = locateEditorBinary();
    if (editorBinary == null) {
      System.err.println("error: desk TUI binary not found.");
      System.err.println("       Install desk-tui next to the CLI, set DESK_TUI, or build with:");
      System.err.println("       cargo build --release --manifest-path modules/desk-tui/Cargo.toml");
      return 1;
    }

    List<String> command = new ArrayList<>();
    command.add(editorBinary.toString());
    if (extraArgs != null) {
      for (String arg : extraArgs) {
        if (arg != null && !arg.isBlank()) {
          command.add(arg);
        }
      }
    }

    try {
      LOG.info("launching desk TUI: {}", editorBinary);
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();
      Process process = pb.start();
      return process.waitFor();
    } catch (IOException e) {
      LOG.error("desk TUI failed", e);
      System.err.println("error: desk TUI failed: " + e.getMessage());
      return 1;
    } catch (InterruptedException e) {
      LOG.error("desk TUI interrupted", e);
      Thread.currentThread().interrupt();
      System.err.println("error: desk TUI failed: " + e.getMessage());
      return 1;
    }
  }

  /**
   * Locates a native Desk TUI binary ({@code desk-tui} preferred, then {@code desk}).
   */
  public static Path locateEditorBinary() {
    String envPath = System.getenv("DESK_TUI");
    if (envPath != null && !envPath.isBlank()) {
      Path fromEnv = Paths.get(envPath);
      if (isNativeTuiBinary(fromEnv)) {
        return fromEnv.toAbsolutePath().normalize();
      }
      LOG.warn("DESK_TUI is set but is not a usable TUI binary: {}", fromEnv);
    }

    Path newest = null;
    for (Path candidate : candidatePaths()) {
      if (!isNativeTuiBinary(candidate)) {
        continue;
      }
      if (newest == null) {
        newest = candidate;
        continue;
      }
      try {
        if (Files.getLastModifiedTime(candidate).toMillis()
            >= Files.getLastModifiedTime(newest).toMillis()) {
          newest = candidate;
        }
      } catch (IOException e) {
        newest = candidate;
      }
    }
    return newest == null ? null : newest.toAbsolutePath().normalize();
  }

  static List<Path> candidatePaths() {
    Set<Path> candidates = new LinkedHashSet<>();
    String tuiName = isWindows() ? "desk-tui.exe" : "desk-tui";
    String legacyName = isWindows() ? "desk.exe" : "desk";

    for (Path dir : searchDirectories()) {
      candidates.add(dir.resolve(tuiName));
      candidates.add(dir.resolve(legacyName));
    }
    return new ArrayList<>(candidates);
  }

  private static List<Path> searchDirectories() {
    Set<Path> dirs = new LinkedHashSet<>();

    // Dev / checkout layouts
    dirs.add(Paths.get("modules/desk-tui/target/release"));
    dirs.add(Paths.get("modules/desk-tui/target/release-next/release"));
    dirs.add(Paths.get("modules/desk-tui/target/debug"));
    dirs.add(Paths.get("bin"));
    dirs.add(Paths.get("."));

    // Next to the running CLI JAR (portable: lib/../bin)
    Path jarDir = locateRunningJarDirectory();
    if (jarDir != null) {
      dirs.add(jarDir.resolveSibling("bin"));
      dirs.add(jarDir);
      Path parent = jarDir.getParent();
      if (parent != null) {
        dirs.add(parent.resolve("bin"));
      }
    }

    // CLI setup share layout (~/.local/share/desk/portable/bin)
    String xdg = System.getenv("XDG_DATA_HOME");
    Path dataHome = (xdg != null && !xdg.isBlank())
        ? Paths.get(xdg)
        : Paths.get(System.getProperty("user.home"), ".local", "share");
    dirs.add(dataHome.resolve("desk").resolve("portable").resolve("bin"));
    dirs.add(dataHome.resolve("desk").resolve("bin"));

    if (isWindows()) {
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        dirs.add(Paths.get(localAppData, "Desk", "portable", "bin"));
        dirs.add(Paths.get(localAppData, "Desk", "bin"));
      }
    }

    return new ArrayList<>(dirs);
  }

  /**
   * Returns true when {@code path} is a regular file that looks like a native
   * executable (ELF / Mach-O / PE), not a shell/batch launcher script.
   */
  static boolean isNativeTuiBinary(Path path) {
    if (path == null || !FileSystem.isFile(path)) {
      return false;
    }
    if (!Files.isReadable(path)) {
      return false;
    }
    try (InputStream in = Files.newInputStream(path)) {
      byte[] header = in.readNBytes(8);
      if (header.length == 0) {
        return false;
      }
      // Reject shebang scripts (`#!/bin/sh`, etc.) and `@echo` batch wrappers.
      if (header[0] == '#' && header.length > 1 && header[1] == '!') {
        return false;
      }
      String asText = new String(header, StandardCharsets.US_ASCII);
      if (asText.regionMatches(true, 0, "@echo", 0, 5)
          || asText.regionMatches(true, 0, "rem ", 0, 4)) {
        return false;
      }
      // ELF
      if (header.length >= 4
          && header[0] == 0x7f
          && header[1] == 'E'
          && header[2] == 'L'
          && header[3] == 'F') {
        return true;
      }
      // PE / Windows (MZ)
      if (header.length >= 2 && header[0] == 'M' && header[1] == 'Z') {
        return true;
      }
      // Mach-O (32/64, endian variants) and Java class files are uncommon here;
      // accept common Mach-O magics used on macOS.
      if (header.length >= 4) {
        int magic = ((header[0] & 0xff) << 24)
            | ((header[1] & 0xff) << 16)
            | ((header[2] & 0xff) << 8)
            | (header[3] & 0xff);
        if (magic == 0xFEEDFACE || magic == 0xFEEDFACF
            || magic == 0xCEFAEDFE || magic == 0xCFFAEDFE
            || magic == 0xCAFEBABE || magic == 0xBEBAFECA) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      LOG.debug("cannot inspect candidate TUI binary {}: {}", path, e.toString());
      return false;
    }
  }

  private static Path locateRunningJarDirectory() {
    try {
      URL location = TuiLauncher.class.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) {
        return null;
      }
      Path path = Paths.get(location.toURI());
      if (Files.isRegularFile(path)) {
        return path.getParent();
      }
      if (Files.isDirectory(path)) {
        return path;
      }
    } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
      LOG.debug("cannot resolve running jar directory: {}", e.toString());
    }
    return null;
  }

  private static boolean isWindows() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\");
  }
}
