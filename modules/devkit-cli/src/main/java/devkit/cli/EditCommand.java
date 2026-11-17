package devkit.cli;

import devkit.core.filesystem.FileSystem;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;
import devkit.editor.Editor;
import devkit.editor.components.EditorRenderer;
import devkit.terminal.Terminal;
import devkit.terminal.TerminalFactory;
import devkit.terminal.input.Key;
import devkit.terminal.input.KeyEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The "edit" command: opens a file in the terminal editor.
 *
 * <p>The editor renders the file content to the terminal and processes
 * keyboard input until the user quits (Ctrl+Q) or saves (Ctrl+S).</p>
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

    // Read existing content if the file exists
    String content = "";
    if (FileSystem.isFile(file) && FileSystem.isReadable(file)) {
      try {
        content = FileSystem.readString(file);
      } catch (Exception e) {
        System.err.println("error: unable to read file " + file + ": " + e.getMessage());
        return CommandResult.failure(1, "unable to read file");
      }
    }

    Terminal terminal = null;
    try {
      terminal = TerminalFactory.createTerminal();
      terminal.enterRawMode();

      Editor editor = new Editor(content);
      EditorRenderer renderer = new EditorRenderer(terminal, editor);

      boolean running = true;
      while (running) {
        renderer.render();
        KeyEvent key = terminal.readKey();
        if (key == null) {
          break;
        }

        running = handleKey(terminal, editor, file, key);
      }

      terminal.exitRawMode();
      terminal = null; // prevent double close
      return CommandResult.success();
    } catch (Exception e) {
      LOG.error("editor failed", e);
      System.err.println("error: editor failed: " + e.getMessage());
      return CommandResult.failure(1, "editor failed");
    } finally {
      if (terminal != null && terminal.isRawMode()) {
        try {
          terminal.exitRawMode();
        } catch (Exception ignored) {
          // best effort
        }
      }
    }
  }

  /**
   * Handles a key event in the editor. Returns {@code false} to quit.
   */
  private boolean handleKey(Terminal terminal, Editor editor, Path file, KeyEvent key) {
    if (key.isCtrl()) {
      switch (key.character()) {
        case 'q':
        case 'Q':
          return false; // quit
        case 's':
        case 'S':
          saveFile(editor, file);
          return true;
        default:
          return true;
      }
    }

    switch (key.key()) {
      case ENTER:
        editor.insertNewline();
        return true;
      case BACKSPACE:
        editor.deleteBackward();
        return true;
      case DELETE:
        editor.deleteForward();
        return true;
      case HOME:
        editor.moveToLineStart();
        return true;
      case END:
        editor.moveToLineEnd();
        return true;
      case ARROW_UP:
        editor.moveUp();
        return true;
      case ARROW_DOWN:
        editor.moveDown();
        return true;
      case ARROW_LEFT:
        editor.moveBackward();
        return true;
      case ARROW_RIGHT:
        editor.moveForward();
        return true;
      case ESCAPE:
        return true; // ignore
      case CHARACTER:
        editor.insertChar(key.character());
        return true;
      case UNKNOWN:
      default:
        return true;
    }
  }

  private void saveFile(Editor editor, Path file) {
    try {
      FileSystem.writeString(file, editor.buffer().toString());
      editor.setModified(false);
      LOG.info("file saved: {}", file);
    } catch (Exception e) {
      LOG.error("unable to save file {}", file, e);
      System.err.println("error: unable to save file " + file + ": " + e.getMessage());
    }
  }
}
