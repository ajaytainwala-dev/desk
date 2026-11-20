package desk.editor.selection;

/**
 * A text selection with start and end offsets. The selection is directional:
 * start is the anchor (where selection began), end is the cursor position.
 */
public final class Selection {

  private final int start;
  private final int end;

  public Selection(int start, int end) {
    this.start = start;
    this.end = end;
  }

  /**
   * Returns an empty selection (no text selected).
   */
  public static Selection empty(int offset) {
    return new Selection(offset, offset);
  }

  public int start() {
    return start;
  }

  public int end() {
    return end;
  }

  /**
   * Returns the smaller of start and end.
   */
  public int min() {
    return Math.min(start, end);
  }

  /**
   * Returns the larger of start and end.
   */
  public int max() {
    return Math.max(start, end);
  }

  /**
   * Returns whether the selection is empty.
   */
  public boolean isEmpty() {
    return start == end;
  }

  /**
   * Returns the length of the selection.
   */
  public int length() {
    return Math.abs(end - start);
  }

  /**
   * Returns whether the given offset is within this selection.
   */
  public boolean contains(int offset) {
    int lo = min();
    int hi = max();
    return offset >= lo && offset <= hi;
  }

  @Override
  public String toString() {
    return String.format("Selection{start=%d, end=%d, length=%d}", start, end, length());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Selection that = (Selection) o;
    return start == that.start && end == that.end;
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(start);
    result = 31 * result + Integer.hashCode(end);
    return result;
  }
}
