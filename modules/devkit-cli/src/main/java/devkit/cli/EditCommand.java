package devkit.cli;

import devkit.core.filesystem.FileSystem;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The "edit" command: opens a file in the Rust-based TUI editor.
 *
 * <p>This command delegates to the {@code devkit-editor-rs} binary, a
 * high-performance terminal user interface built with Ratatui and Crossterm.
 * The binary handles all rendering, input, and file I/O directly.</p>
 */
public class EditCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(EditCommand.class);

  @Override
  public String name() {
    return "edit";
  }

  @Override
  public String description() {
    return "Open a file in the terminal editor.";
  }

  @Override
  public String usage() {
    return "<file>";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      StringBuilder out = new StringBuilder();
      printHelp(out);
      System.out.print(out);
      return CommandResult.success();
    }

    Path file = Paths.get(args[0]);
    LOG.info("edit requested for file: {}", file);

    // Ensure the file exists (create empty if not)
    if (!FileSystem.isFile(file)) {
      try {
        FileSystem.writeString(file, "");
      } catch (Exception e) {
        System.err.println("error: unable to create file " + file + ": " + e.getMessage());
        return CommandResult.failure(1, "unable to create file");
      }
    }

    // Locate the Rust TUI binary
    Path editorBinary = locateEditorBinary();
    if (editorBinary == null) {
      System.err.println("error: rust TUI editor binary not found.");
      System.err.println("       Build it with: cargo build --release --manifest-path modules/devkit-editor-rs/Cargo.toml");
      return CommandResult.failure(1, "editor binary not found");
    }

    // Launch the Rust TUI editor
    try {
      ProcessBuilder pb = new ProcessBuilder(editorBinary.toString(), file.toString());
      pb.inheritIO(); // Connect stdio to the parent process
      Process process = pb.start();
      int exitCode = process.waitFor();
      LOG.info("editor exited with code: {}", exitCode);
      return exitCode == 0 ? CommandResult.success() : CommandResult.failure(exitCode, "editor exited with code " + exitCode);
    } catch (IOException | InterruptedException e) {
      LOG.error("editor failed", e);
      Thread.currentThread().interrupt();
      System.err.println("error: editor failed: " + e.getMessage());
      return CommandResult.failure(1, "editor failed");
    }
  }

  /**
   * Locates the Rust TUI editor binary. Checks several locations in order:
   * <ol>
   *   <li>{@code modules/devkit-editor-rs/target/release/devkit-editor-rs(.exe)}</li>
   *   <li>Beside the CLI jar (for portable distributions)</li>
   * </ol>
   */
  private Path locateEditorBinary() {
    // Check the release build directory
    Path releaseBuild = Paths.get("modules/devkit-editor-rs/target/release")
        .resolve(isWindows() ? "devkit-editor-rs.exe" : "devkit-editor-rs");
    if (FileSystem.isFile(releaseBuild)) {
      return releaseBuild.toAbsolutePath();
    }

    // Check debug build directory
    Path debugBuild = Paths.get("modules/devkit-editor-rs/target/debug")
        .resolve(isWindows() ? "devkit-editor-rs.exe" : "devkit-editor-rs");
    if (FileSystem.isFile(debugBuild)) {
      return debugBuild.toAbsolutePath();
    }

    // Check beside the current working directory
    Path besideCwd = Paths.get(".")
        .resolve(isWindows() ? "devkit-editor-rs.exe" : "devkit-editor-rs");
    if (FileSystem.isFile(besideCwd)) {
      return besideCwd.toAbsolutePath();
    }

    return null;
  }

  private static boolean isWindows() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\");
  }
}
