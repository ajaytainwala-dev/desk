package desk.editor.components;

import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;
import desk.editor.Editor;
import desk.editor.buffer.GapBuffer;
import desk.lexer.JavaLexer;
import desk.lexer.tokens.TokenStream;
import desk.syntax.highlighter.Highlighter;
import desk.syntax.highlighter.TokenStyle;
import desk.syntax.themes.DraculaTheme;
import desk.terminal.Terminal;
import desk.terminal.TerminalDimensions;
import desk.terminal.colors.TerminalColor;

import java.util.List;

/**
 * Renders an {@link Editor} to a {@link Terminal} with a modern, high-level
 * TUI design.
 *
 * <p>The layout includes:
 * <ul>
 *   <li>A title bar showing the file name and editor state.</li>
 *   <li>A line-number gutter with a vertical separator.</li>
 *   <li>The main content area with syntax highlighting.</li>
 *   <li>A status bar showing cursor position, file info, and key hints.</li>
 * </ul>
 * </p>
 *
 * <p>The renderer uses the Dracula dark theme by default for syntax
 * highlighting and UI colors.</p>
 */
public class EditorRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(EditorRenderer.class);

  private final Terminal terminal;
  private final Editor editor;
  private final Highlighter highlighter;
  private final DraculaTheme theme = new DraculaTheme();

  public EditorRenderer(Terminal terminal, Editor editor) {
    this.terminal = terminal;
    this.editor = editor;
    this.highlighter = new Highlighter(theme);
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

    // Layout: title bar (1 row) + text area + status bar (1 row)
    int textAreaHeight = Math.max(1, height - 2);

    GapBuffer buffer = editor.buffer();
    int lineCount = buffer.getLineCount();
    int cursorLine = editor.currentLineIndex();

    // Compute viewport: ensure cursor line is visible
    int topLine = computeViewport(cursorLine, lineCount, textAreaHeight);

    // Layout constants
    int lineNumberWidth = Math.max(1, String.valueOf(lineCount).length());
    int gutterWidth = lineNumberWidth + 1; // line number + separator space
    int separatorCol = gutterWidth;
    int contentStart = gutterWidth + 1; // +1 for the vertical separator

    // --- Title bar ---
    renderTitleBar(width, separatorCol);

    // --- Text area ---
    for (int i = 0; i < textAreaHeight; i++) {
      int lineIndex = topLine + i;
      int screenRow = i + 1; // offset by title bar

      if (lineIndex >= lineCount) {
        // Empty line below end of file: draw a tilde like vim
        terminal.setColor(TerminalColor.BRIGHT_BLACK, TerminalColor.DEFAULT_BACKGROUND);
        terminal.putString(0, screenRow, "~");
        continue;
      }

      // Line number (gutter)
      boolean isCursorLine = (lineIndex == cursorLine);
      terminal.setColor(
          isCursorLine ? TerminalColor.BRIGHT_CYAN : TerminalColor.BRIGHT_BLACK,
          TerminalColor.DEFAULT_BACKGROUND);
      String lineNum = String.format("%" + lineNumberWidth + "d", lineIndex + 1);
      terminal.putString(0, screenRow, lineNum);
      terminal.putString(lineNumberWidth, screenRow, " ");

      // Vertical separator
      terminal.setColor(TerminalColor.BRIGHT_BLACK, TerminalColor.DEFAULT_BACKGROUND);
      terminal.putString(separatorCol, screenRow, "│");

      // Line content with syntax highlighting
      renderLineContent(screenRow, contentStart, width - contentStart, lineIndex, isCursorLine);
    }

    // --- Status bar ---
    renderStatusBar(width, height - 1);

    // --- Position cursor ---
    int cursorScreenRow = (cursorLine - topLine) + 1; // +1 for title bar
    int cursorScreenCol = contentStart + editor.currentColumn();
    if (cursorScreenRow >= 1 && cursorScreenRow < height - 1
        && cursorScreenCol >= 0 && cursorScreenCol < width) {
      terminal.showCursor();
      terminal.setCursorPosition(cursorScreenCol, cursorScreenRow);
    } else {
      terminal.hideCursor();
    }

    terminal.refresh();
  }

  /**
   * Computes the top line of the viewport so the cursor line stays visible
   * and preferably centered.
   */
  private int computeViewport(int cursorLine, int lineCount, int textAreaHeight) {
    if (lineCount <= textAreaHeight) {
      return 0;
    }
    int idealTop = cursorLine - textAreaHeight / 2;
    int maxTop = lineCount - textAreaHeight;
    return Math.max(0, Math.min(idealTop, maxTop));
  }

  /**
   * Renders the title bar at the top of the screen.
   */
  private void renderTitleBar(int width, int separatorCol) {
    terminal.setColor(TerminalColor.BRIGHT_WHITE, TerminalColor.BRIGHT_BLACK);
    // Fill the entire title bar with background color
    StringBuilder bg = new StringBuilder();
    for (int i = 0; i < width; i++) {
      bg.append(' ');
    }
    terminal.putString(0, 0, bg.toString());

    // File name and status
    String fileName = editor.fileNameOrNoName();
    String modified = editor.isModified() ? " ●" : "";
    String title = "DEVKIT  " + fileName + modified;

    terminal.setColor(TerminalColor.BRIGHT_WHITE, TerminalColor.BRIGHT_BLACK);
    terminal.putString(1, 0, title);

    // Right side: mode indicator
    String mode = " INSERT ";
    int modeCol = width - mode.length() - 1;
    if (modeCol > separatorCol + 1) {
      terminal.putString(modeCol, 0, mode);
    }

    // Horizontal separator line
    terminal.setColor(TerminalColor.BRIGHT_BLACK, TerminalColor.DEFAULT_BACKGROUND);
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < width; i++) {
      line.append(i == separatorCol ? '┤' : '─');
    }
    terminal.putString(0, 1, line.toString());
  }

  /**
   * Renders a single line of content with syntax highlighting.
   */
  private void renderLineContent(int screenRow, int contentStart, int maxWidth,
      int lineIndex, boolean isCursorLine) {
    String lineText = editor.buffer().getLine(lineIndex);
    if (lineText == null || lineText.isEmpty()) {
      return;
    }

    // Highlight the line using the lexer + highlighter
    TokenStream stream = new JavaLexer(lineText).tokenize();
    List<desk.syntax.highlighter.HighlightedToken> highlighted =
        highlighter.highlight(stream);

    int col = contentStart;
    for (desk.syntax.highlighter.HighlightedToken ht : highlighted) {
      TokenStyle style = ht.style();
      TerminalColor fg = styleToColor(style);
      if (fg == null) {
        fg = isCursorLine ? TerminalColor.BRIGHT_CYAN : TerminalColor.DEFAULT_FOREGROUND;
      }
      terminal.setColor(fg, TerminalColor.DEFAULT_BACKGROUND);

      String value = ht.token().value();
      if (col + value.length() > contentStart + maxWidth) {
        value = value.substring(0, contentStart + maxWidth - col);
      }
      if (!value.isEmpty()) {
        terminal.putString(col, screenRow, value);
      }
      col += value.length();
    }
  }

  /**
   * Converts a {@link TokenStyle} to a {@link TerminalColor}.
   */
  private TerminalColor styleToColor(TokenStyle style) {
    if (style == null) {
      return null;
    }
    String hex = style.foreground();
    if (hex == null || hex.isEmpty()) {
      return null;
    }
    // Parse #RRGGBB
    try {
      if (hex.startsWith("#") && hex.length() == 7) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return TerminalColor.rgb(r, g, b);
      }
    } catch (NumberFormatException e) {
      // fall through to default
    }
    return null;
  }

  /**
   * Renders the status bar at the bottom of the screen.
   */
  private void renderStatusBar(int width, int statusRow) {
    terminal.setColor(TerminalColor.BLACK, TerminalColor.BRIGHT_WHITE);
    // Fill the status bar
    StringBuilder bg = new StringBuilder();
    for (int i = 0; i < width; i++) {
      bg.append(' ');
    }
    terminal.putString(0, statusRow, bg.toString());

    // Left side: file info
    String left = " " + editor.fileNameOrNoName()
        + "  |  Lines: " + editor.buffer().getLineCount()
        + "  |  " + (editor.isModified() ? "Modified" : "Clean");
    terminal.setColor(TerminalColor.BLACK, TerminalColor.BRIGHT_WHITE);
    terminal.putString(0, statusRow, left);

    // Center: cursor position
    String center = " Ln " + (editor.currentLineIndex() + 1)
        + ", Col " + (editor.currentColumn() + 1);
    int centerCol = (width - center.length()) / 2;
    if (centerCol > left.length() + 1) {
      terminal.putString(centerCol, statusRow, center);
    }

    // Right side: key hints
    String right = " Ctrl+S Save  Ctrl+Q Quit ";
    int rightCol = width - right.length() - 1;
    if (rightCol > centerCol + center.length() + 1) {
      terminal.putString(rightCol, statusRow, right);
    }

    // Horizontal separator above status bar
    terminal.setColor(TerminalColor.BRIGHT_BLACK, TerminalColor.DEFAULT_BACKGROUND);
    StringBuilder sep = new StringBuilder();
    for (int i = 0; i < width; i++) {
      sep.append('─');
    }
    terminal.putString(0, statusRow - 1, sep.toString());
  }
}
