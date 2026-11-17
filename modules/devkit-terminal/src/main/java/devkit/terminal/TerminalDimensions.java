package devkit.terminal;

/**
 * The dimensions of the terminal screen: number of columns and rows.
 */
public final class TerminalDimensions {

  private final int columns;
  private final int rows;

  public TerminalDimensions(int columns, int rows) {
    this.columns = columns;
    this.rows = rows;
  }

  public int columns() {
    return columns;
  }

  public int rows() {
    return rows;
  }

  @Override
  public String toString() {
    return "TerminalDimensions{columns=" + columns + ", rows=" + rows + "}";
  }
}
