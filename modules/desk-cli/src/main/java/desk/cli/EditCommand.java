package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The "edit" command: opens a file in the Rust-based TUI editor.
 *
 * <p>This command delegates to the {@code desk-tui} binary, a
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
    int exitCode = TuiLauncher.launch(file.toString());
    return exitCode == 0 ? CommandResult.success() : CommandResult.failure(exitCode,
        "editor exited with code " + exitCode);
  }
}
