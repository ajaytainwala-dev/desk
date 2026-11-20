package desk.editor.viewport;

/**
 * The visible portion of a text buffer: which lines are shown, the horizontal
 * scroll offset, and the cursor position within the viewport.
 */
public final class Viewport {

  private final int topLine;      // first visible line (0-based)
  private final int leftColumn;   // horizontal scroll offset (0-based)
  private final int height;       // number of visible lines
  private final int width;        // number of visible columns

  public Viewport(int topLine, int leftColumn, int height, int width) {
    this.topLine = topLine;
    this.leftColumn = leftColumn;
    this.height = height;
    this.width = width;
  }

  public int topLine() {
    return topLine;
  }

  public int leftColumn() {
    return leftColumn;
  }

  public int height() {
    return height;
  }

  public int width() {
    return width;
  }

  public int bottomLine() {
    return topLine + height - 1;
  }

  /**
   * Returns whether the given line is visible in this viewport.
   */
  public boolean isLineVisible(int lineIndex) {
    return lineIndex >= topLine && lineIndex <= bottomLine();
  }

  @Override
  public String toString() {
    return String.format("Viewport{topLine=%d, leftColumn=%d, height=%d, width=%d}",
        topLine, leftColumn, height, width);
  }
}
