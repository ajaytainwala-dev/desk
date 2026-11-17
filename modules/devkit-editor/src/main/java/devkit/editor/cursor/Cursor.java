package devkit.editor.cursor;

/**
 * Cursor position in a text buffer, expressed as a character offset and
 * derived line/column coordinates.
 */
public final class Cursor {

  private int offset;
  private int line;   // 0-based
  private int column; // 0-based within line

  public Cursor() {
    this(0, 0, 0);
  }

  public Cursor(int offset, int line, int column) {
    this.offset = offset;
    this.line = line;
    this.column = column;
  }

  public int offset() {
    return offset;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public void setColumn(int column) {
    this.column = column;
  }

  public void setPosition(int offset, int line, int column) {
    this.offset = offset;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return String.format("Cursor{offset=%d, line=%d, column=%d}", offset, line, column);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Cursor cursor = (Cursor) o;
    return offset == cursor.offset && line == cursor.line && column == cursor.column;
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(offset);
    result = 31 * result + Integer.hashCode(line);
    result = 31 * result + Integer.hashCode(column);
    return result;
  }
}
