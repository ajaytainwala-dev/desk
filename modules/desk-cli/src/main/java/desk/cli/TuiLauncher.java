package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates and launches the Rust {@code desk-tui} editor binary (Ratatui/Crossterm).
 *
 * <p>The CLI launcher script is also named {@code desk}; this class must never
 * treat that shell/batch wrapper as the TUI or it will recurse forever.</p>
 */
public final class TuiLauncher {

  private static final Logger LOG = LoggerFactory.getLogger(TuiLauncher.class);

  private TuiLauncher() {
  }

  public static int launch(String... extraArgs) {
    Path editorBinary = locateEditorBinary();
    if (editorBinary == null) {
      System.err.println("error: desk TUI binary not found.");
      System.err.println("       Install desk-tui from the release, set DESK_TUI, or build with:");
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
   * Locates the Rust TUI binary. Prefers {@code desk-tui} / {@code DESK_TUI},
   * then a native {@code desk} binary — never a shell/batch launcher.
   */
  public static Path locateEditorBinary() {
    String envPath = System.getenv("DESK_TUI");
    if (envPath != null && !envPath.isBlank()) {
      Path fromEnv = Paths.get(envPath);
      if (isUsableTuiBinary(fromEnv)) {
        return fromEnv.toAbsolutePath().normalize();
      }
    }

    Path newest = null;
    for (Path candidate : candidatePaths()) {
      if (!isUsableTuiBinary(candidate)) {
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
    Set<Path> paths = new LinkedHashSet<>();
    String tuiName = isWindows() ? "desk-tui.exe" : "desk-tui";
    String deskName = isWindows() ? "desk.exe" : "desk";

    // Preferred stable name next to common install / build layouts
    for (String name : List.of(tuiName, deskName)) {
      paths.add(Paths.get("modules/desk-tui/target/release").resolve(name));
      paths.add(Paths.get("modules/desk-tui/target/release-next/release").resolve(name));
      paths.add(Paths.get("modules/desk-tui/target/debug").resolve(name));
      paths.add(Paths.get("bin").resolve(name));
      paths.add(Paths.get(".").resolve(name));
    }

    Path home = Paths.get(System.getProperty("user.home", "."));
    String xdg = System.getenv("XDG_DATA_HOME");
    Path dataHome = (xdg != null && !xdg.isBlank())
        ? Paths.get(xdg)
        : home.resolve(".local").resolve("share");
    Path localAppData = System.getenv("LOCALAPPDATA") != null
        ? Paths.get(System.getenv("LOCALAPPDATA"))
        : null;

    for (String name : List.of(tuiName, deskName)) {
      paths.add(dataHome.resolve("desk").resolve("bin").resolve(name));
      paths.add(dataHome.resolve("desk").resolve("portable").resolve("bin").resolve(name));
      if (localAppData != null) {
        paths.add(localAppData.resolve("Desk").resolve("bin").resolve(name));
        paths.add(localAppData.resolve("Desk").resolve("portable").resolve("bin").resolve(name));
      }
    }

    // Relative to the running uber-JAR: lib/… → ../bin/desk-tui
    Path jarDir = codeSourceDirectory();
    if (jarDir != null) {
      Path distRoot = jarDir.getFileName() != null
          && "lib".equalsIgnoreCase(jarDir.getFileName().toString())
          ? jarDir.getParent()
          : jarDir;
      if (distRoot != null) {
        for (String name : List.of(tuiName, deskName)) {
          paths.add(distRoot.resolve("bin").resolve(name));
          paths.add(distRoot.resolve(name));
        }
      }
    }

    return List.copyOf(paths);
  }

  /**
   * True when {@code path} is a real native TUI binary (ELF / Mach-O / PE),
   * not a {@code #!} shell launcher or {@code @echo} batch wrapper.
   */
  static boolean isUsableTuiBinary(Path path) {
    if (path == null || !FileSystem.isFile(path)) {
      return false;
    }
    try {
      if (!Files.isExecutable(path) && !isWindows()) {
        // Some filesystems omit +x; still accept native binaries.
        if (!looksLikeNativeBinary(path)) {
          return false;
        }
      }
      return looksLikeNativeBinary(path);
    } catch (IOException e) {
      LOG.debug("cannot inspect TUI candidate {}: {}", path, e.toString());
      return false;
    }
  }

  static boolean looksLikeNativeBinary(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      byte[] magic = in.readNBytes(4);
      if (magic.length < 2) {
        return false;
      }
      // Reject shebang scripts (#!/bin/sh launcher)
      if (magic[0] == '#' && magic[1] == '!') {
        return false;
      }
      // Reject Windows batch wrappers (@echo / REM)
      if (magic[0] == '@' || (magic[0] == 'R' && magic.length >= 3 && magic[1] == 'E' && magic[2] == 'M')) {
        return false;
      }
      if (magic.length < 4) {
        return false;
      }
      // ELF
      if (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
        return true;
      }
      // MZ / PE
      if (magic[0] == 'M' && magic[1] == 'Z') {
        return true;
      }
      int be = ((magic[0] & 0xff) << 24)
          | ((magic[1] & 0xff) << 16)
          | ((magic[2] & 0xff) << 8)
          | (magic[3] & 0xff);
      int le = Integer.reverseBytes(be);
      // Mach-O 32/64 and fat
      return be == 0xFEEDFACE || be == 0xFEEDFACF || be == 0xCAFEBABE
          || le == 0xFEEDFACE || le == 0xFEEDFACF || be == 0xBEBAFECA;
    }
  }

  private static Path codeSourceDirectory() {
    try {
      CodeSource cs = TuiLauncher.class.getProtectionDomain().getCodeSource();
      if (cs == null) {
        return null;
      }
      URL loc = cs.getLocation();
      if (loc == null) {
        return null;
      }
      Path path = Paths.get(loc.toURI());
      if (Files.isRegularFile(path)) {
        return path.getParent();
      }
      return path;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private static boolean isWindows() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\");
  }
}
