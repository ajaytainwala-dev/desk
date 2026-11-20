package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates and launches the Rust {@code desk} TUI binary (Ratatui/Crossterm).
 */
public final class TuiLauncher {

  private static final Logger LOG = LoggerFactory.getLogger(TuiLauncher.class);

  private TuiLauncher() {
  }

  public static int launch(String... extraArgs) {
    Path editorBinary = locateEditorBinary();
    if (editorBinary == null) {
      System.err.println("error: desk TUI binary not found.");
      System.err.println("       Build it with: cargo build --release --manifest-path modules/desk-tui/Cargo.toml");
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
    } catch (IOException | InterruptedException e) {
      LOG.error("desk TUI failed", e);
      Thread.currentThread().interrupt();
      System.err.println("error: desk TUI failed: " + e.getMessage());
      return 1;
    }
  }

  /**
   * Locates {@code desk} / {@code desk.exe} built from {@code modules/desk-tui}.
   */
  public static Path locateEditorBinary() {
    String exeName = isWindows() ? "desk.exe" : "desk";
    Path[] candidates = {
        Paths.get("modules/desk-tui/target/release").resolve(exeName),
        Paths.get("modules/desk-tui/target/release-next/release").resolve(exeName),
        Paths.get("modules/desk-tui/target/debug").resolve(exeName),
        Paths.get("bin").resolve(exeName),
        Paths.get(".").resolve(exeName),
    };

    String envPath = System.getenv("DESK_TUI");
    if (envPath != null && !envPath.isBlank()) {
      Path fromEnv = Paths.get(envPath);
      if (FileSystem.isFile(fromEnv)) {
        return fromEnv.toAbsolutePath();
      }
    }

    Path newest = null;
    for (Path candidate : candidates) {
      if (!FileSystem.isFile(candidate)) {
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
    return newest == null ? null : newest.toAbsolutePath();
  }

  private static boolean isWindows() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\");
  }
}