package desk.editor.buffer;

/**
 * A gap buffer text buffer.
 *
 * <p>A gap buffer stores text in a character array with a "gap" (a region of
 * unused space) at the current cursor position. This makes insertions and
 * deletions near the cursor efficient (O(gap size) to move the gap, O(1) to
 * insert/delete once the gap is positioned).</p>
 *
 * <p>Trade-offs compared to alternatives:</p>
 * <ul>
 *   <li><b>Gap buffer</b> (chosen): Simple, fast for typical editing where
 *     changes cluster near the cursor. Moving the gap across the document is
 *     O(n), but cursor movement is usually local.</li>
 *   <li><b>Piece table</b>: Better for large documents and persistent undo,
 *     but more complex to implement.</li>
 *   <li><b>Rope</b>: Good for very large documents and concurrent editing,
 *     but higher overhead and complexity.</li>
 * </ul>
 *
 * For a terminal editor targeting typical source files, a gap buffer offers
 * the best balance of simplicity and performance.
 */
public final class GapBuffer {

  private char[] data;
  private int gapStart;
  private int gapEnd;
  private int length; // number of real characters (excluding gap)

  /**
   * Creates an empty gap buffer with the given initial capacity.
   */
  public GapBuffer(int initialCapacity) {
    this.data = new char[initialCapacity];
    this.gapStart = 0;
    this.gapEnd = initialCapacity;
    this.length = 0;
  }

  public GapBuffer() {
    this(1024);
  }

  /**
   * Creates a gap buffer initialized with the given text.
   */
  public GapBuffer(String text) {
    this(text == null ? "" : text, true);
  }

  private GapBuffer(String text, boolean unused) {
    int capacity = Math.max(text.length() + 16, 1024);
    this.data = new char[capacity];
    this.gapStart = text.length();
    this.gapEnd = capacity;
    this.length = text.length();
    if (text.length() > 0) {
      text.getChars(0, text.length(), this.data, 0);
    }
  }

  public static GapBuffer from(String text) {
    return new GapBuffer(text, true);
  }

  /**
   * Returns the number of characters in the buffer (excluding the gap).
   */
  public int length() {
    return length;
  }

  /**
   * Returns the character at the given index (0-based).
   *
   * @throws IndexOutOfBoundsException if the index is out of range.
   */
  public char charAt(int index) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException("Index " + index + " out of range [0," + length + ")");
    }
    if (index < gapStart) {
      return data[index];
    } else {
      return data[index + gapSize()];
    }
  }

  /**
   * Returns the entire buffer contents as a string.
   */
  public String toString() {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(charAt(i));
    }
    return sb.toString();
  }

  /**
   * Returns the text from {@code start} (inclusive) to {@code end} (exclusive).
   */
  public String substring(int start, int end) {
    if (start < 0 || end > length || start > end) {
      throw new IndexOutOfBoundsException("Invalid range [" + start + "," + end + ") for length " + length);
    }
    StringBuilder sb = new StringBuilder(end - start);
    for (int i = start; i < end; i++) {
      sb.append(charAt(i));
    }
    return sb.toString();
  }

  /**
   * Returns the text of the given line (0-based). Returns empty string if the
   * line does not exist.
   */
  public String getLine(int lineIndex) {
    int start = lineStart(lineIndex);
    if (start < 0) {
      return "";
    }
    int end = lineEnd(lineIndex);
    return substring(start, end);
  }

  /**
   * Returns the number of lines in the buffer.
   */
  public int getLineCount() {
    int count = 1;
    for (int i = 0; i < length; i++) {
      if (charAt(i) == '\n') {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns the start offset (0-based) of the given line (0-based), or -1 if
   * the line does not exist.
   */
  public int lineStart(int lineIndex) {
    if (lineIndex < 0) {
      return -1;
    }
    int currentLine = 0;
    int i = 0;
    while (i < length) {
      if (currentLine == lineIndex) {
        return i;
      }
      if (charAt(i) == '\n') {
        currentLine++;
      }
      i++;
    }
    return (currentLine == lineIndex) ? i : -1;
  }

  /**
   * Returns the end offset (exclusive) of the given line (0-based), or -1 if
   * the line does not exist.
   */
  public int lineEnd(int lineIndex) {
    int start = lineStart(lineIndex);
    if (start < 0) {
      return -1;
    }
    int i = start;
    while (i < length && charAt(i) != '\n') {
      i++;
    }
    return i;
  }

  /**
   * Returns the line index (0-based) containing the given offset.
   */
  public int getLineIndex(int offset) {
    if (offset < 0 || offset > length) {
      throw new IndexOutOfBoundsException("Offset " + offset + " out of range [0," + length + "]");
    }
    int line = 0;
    for (int i = 0; i < offset; i++) {
      if (charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }

  /**
   * Returns the column (0-based) of the given offset within its line.
   */
  public int getColumn(int offset) {
    int lineStart = lineStart(getLineIndex(offset));
    return offset - lineStart;
  }

  /**
   * Returns the offset (0-based) for the given line and column.
   */
  public int offsetFor(int lineIndex, int column) {
    int start = lineStart(lineIndex);
    if (start < 0) {
      return length;
    }
    int end = lineEnd(lineIndex);
    int target = start + Math.min(column, end - start);
    return target;
  }

  private int gapSize() {
    return gapEnd - gapStart;
  }

  private void ensureGap(int needed) {
    if (gapSize() >= needed) {
      return;
    }
    // Grow the buffer, keeping the gap in the same position.
    int newCapacity = Math.max(data.length * 2, data.length + needed + 16);
    char[] newData = new char[newCapacity];
    // Copy characters before the gap
    System.arraycopy(data, 0, newData, 0, gapStart);
    // Copy characters after the gap, placing them after the gap in the new buffer
    int afterGapStart = gapEnd;
    int afterGapLength = data.length - gapEnd;
    int newGapEnd = newCapacity - afterGapLength;
    System.arraycopy(data, afterGapStart, newData, newGapEnd, afterGapLength);
    this.data = newData;
    // gapStart stays the same; gapEnd is adjusted to maintain gap size + growth
    this.gapEnd = newGapEnd;
  }

  /**
   * Moves the gap to the given offset (0-based).
   */
  private void moveGapTo(int offset) {
    if (offset < 0 || offset > length) {
      throw new IndexOutOfBoundsException("Offset " + offset + " out of range [0," + length + "]");
    }
    if (offset == gapStart) {
      return;
    }
    if (offset < gapStart) {
      // Move gap left: characters [offset, gapStart) shift right to just before gapEnd.
      int shift = gapStart - offset;
      ensureGap(shift);
      System.arraycopy(data, offset, data, gapEnd - shift, shift);
      gapStart = offset;
      gapEnd = gapEnd - shift;
    } else {
      // Move gap right: characters [gapEnd, offset) shift left to just after gapStart.
      int shift = offset - gapEnd;
      ensureGap(shift);
      System.arraycopy(data, gapEnd, data, gapStart, shift);
      gapStart = gapStart + shift;
      gapEnd = offset;
    }
  }

  /**
   * Inserts a single character at the current gap position, then advances
   * the gap past the inserted character.
   */
  public void insert(char ch) {
    ensureGap(1);
    data[gapStart] = ch;
    gapStart++;
    length++;
  }

  /**
   * Inserts a string at the current gap position, then advances the gap
   * past the inserted text.
   */
  public void insert(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    ensureGap(text.length());
    text.getChars(0, text.length(), data, gapStart);
    gapStart += text.length();
    length += text.length();
  }

  /**
   * Inserts a string at the given offset, then moves the gap to after the
   * inserted text.
   */
  public void insert(int offset, String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    moveGapTo(offset);
    insert(text);
  }

  /**
   * Deletes {@code count} characters starting at the given offset.
   */
  public void delete(int offset, int count) {
    if (count <= 0) {
      return;
    }
    if (offset < 0 || offset + count > length) {
      throw new IndexOutOfBoundsException("Cannot delete " + count + " chars at " + offset + " in length " + length);
    }
    moveGapTo(offset);
    gapEnd += count;
    length -= count;
  }

  /**
   * Deletes the character at the given offset.
   */
  public void delete(int offset) {
    delete(offset, 1);
  }

  /**
   * Replaces text from {@code start} (inclusive) to {@code end} (exclusive)
   * with the given replacement text.
   */
  public void replace(int start, int end, String replacement) {
    if (start < 0 || end > length || start > end) {
      throw new IndexOutOfBoundsException("Invalid range [" + start + "," + end + ") for length " + length);
    }
    delete(start, end - start);
    insert(start, replacement);
  }

  /**
   * Clears the entire buffer.
   */
  public void clear() {
    gapStart = 0;
    gapEnd = data.length;
    length = 0;
  }

  /**
   * Checks whether the buffer is empty.
   */
  public boolean isEmpty() {
    return length == 0;
  }
}
