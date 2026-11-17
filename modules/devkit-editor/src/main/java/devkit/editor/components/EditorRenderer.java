package devkit.editor.components;

import devkit.editor.Editor;
import devkit.editor.buffer.GapBuffer;
import devkit.terminal.Terminal;
import devkit.terminal.TerminalDimensions;
import devkit.terminal.colors.TerminalColor;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

/**
 * Renders an {@link Editor} to a {@link Terminal}.
 *
 * <p>The renderer draws the buffer content with line numbers, a status bar,
 * and positions the cursor. It is independent of the input handling loop.</p>
 */
public class EditorRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(EditorRenderer.class);

  private final Terminal terminal;
  private final Editor editor;

  public EditorRenderer(Terminal terminal, Editor editor) {
    this.terminal = terminal;
    this.editor = editor;
  }

  /**
   * Renders the current editor state to the terminal.
   */
  public void render() {
    TerminalDimensions dims = terminal.getDimensions();
    int width = dims.columns();
    int height = dims.rows();

    if (width <= 0 || height <= 0) {
      return;
    }

    terminal.clearScreen();
    terminal.hideCursor();

    // Reserve the last row for the status bar
    int textAreaHeight = Math.max(1, height - 1);

    GapBuffer buffer = editor.buffer();
    int lineCount = buffer.getLineCount();
    int cursorLine = editor.currentLineIndex();

    // Compute viewport: ensure cursor line is visible
    int topLine = Math.max(0, Math.min(cursorLine - textAreaHeight / 2,
        Math.max(0, lineCount - textAreaHeight)));

    // Draw line numbers and content
    int lineNumberWidth = Math.max(1, (int) Math.log10(Math.max(1, lineCount)) + 1);
    int contentStart = lineNumberWidth + 1;

    for (int i = 0; i < textAreaHeight; i++) {
      int lineIndex = topLine + i;
      if (lineIndex >= lineCount) {
        break;
      }

      // Line number
      terminal.setColor(TerminalColor.BRIGHT_BLACK, TerminalColor.DEFAULT_BACKGROUND);
      String lineNum = String.format("%" + lineNumberWidth + "d", lineIndex + 1);
      terminal.putString(0, i, lineNum);
      terminal.putString(lineNumberWidth, i, " ");

      // Line content
      String lineText = buffer.getLine(lineIndex);
      if (lineText != null && !lineText.isEmpty()) {
        terminal.setColor(TerminalColor.DEFAULT_FOREGROUND, TerminalColor.DEFAULT_BACKGROUND);
        // Truncate to fit width
        if (lineText.length() > width - contentStart) {
          lineText = lineText.substring(0, width - contentStart);
        }
        terminal.putString(contentStart, i, lineText);
      }
    }

    // Status bar
    terminal.setColor(TerminalColor.BLACK, TerminalColor.BRIGHT_WHITE);
    String status = buildStatusBar(width);
    terminal.putString(0, height - 1, status);

    // Position cursor
    int cursorScreenRow = cursorLine - topLine;
    int cursorScreenCol = contentStart + editor.currentColumn();
    if (cursorScreenRow >= 0 && cursorScreenRow < textAreaHeight
        && cursorScreenCol >= 0 && cursorScreenCol < width) {
      terminal.showCursor();
      terminal.setCursorPosition(cursorScreenCol, cursorScreenRow);
    }

    terminal.refresh();
  }

  private String buildStatusBar(int width) {
    StringBuilder sb = new StringBuilder();
    sb.append("DevKit Editor");
    sb.append(" | ");
    sb.append(editor.isModified() ? "[Modified]" : "[Clean]");
    sb.append(" | ");
    sb.append("Ln ").append(editor.currentLineIndex() + 1)
        .append(", Col ").append(editor.currentColumn() + 1);
    sb.append(" | ");
    sb.append("Ctrl+S Save | Ctrl+Q Quit");

    // Pad to width
    while (sb.length() < width) {
      sb.append(' ');
    }
    if (sb.length() > width) {
      sb.setLength(width);
    }
    return sb.toString();
  }
}
