package devkit.editor;

import devkit.editor.buffer.GapBuffer;
import devkit.editor.cursor.Cursor;
import devkit.editor.selection.Selection;
import devkit.editor.undo.UndoManager;
import devkit.editor.viewport.Viewport;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

/**
 * The main text editor model. Holds the buffer, cursor, selection, viewport,
 * and undo manager. Provides editing operations that update all state
 * consistently.
 *
 * <p>The editor is independent of any UI rendering - it only manages the
 * text state. Rendering is handled by separate components.</p>
 */
public class Editor {

  private static final Logger LOG = LoggerFactory.getLogger(Editor.class);

  private final GapBuffer buffer;
  private final Cursor cursor;
    private Selection selection;
  private final UndoManager undoManager;
  private boolean modified = false;
  private String fileName = null;

  public Editor() {
    this.buffer = new GapBuffer();
    this.cursor = new Cursor();
    this.selection = Selection.empty(0);
    this.undoManager = new UndoManager();
  }

  public Editor(String initialText) {
    this();
    buffer.clear();
    if (initialText != null && !initialText.isEmpty()) {
      buffer.insert(initialText);
    }
  }

  public GapBuffer buffer() {
    return buffer;
  }

  public Cursor cursor() {
    return cursor;
  }

  public Selection selection() {
    return selection;
  }

  public UndoManager undoManager() {
    return undoManager;
  }

  public boolean isModified() {
    return modified;
  }

  public void setModified(boolean modified) {
    this.modified = modified;
  }

  /**
   * Returns the file name associated with this editor, or {@code null} if
   * no file is associated.
   */
  public String fileName() {
    return fileName;
  }

  /**
   * Sets the file name associated with this editor.
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Returns the file name for display, or "[No Name]" if no file is
   * associated.
   */
  public String fileNameOrNoName() {
    return fileName != null ? fileName : "[No Name]";
  }

  /**
   * Returns the character at the cursor position, or 0 if at end of buffer.
   */
  public char charAtCursor() {
    int offset = cursor.offset();
    if (offset < buffer.length()) {
      return buffer.charAt(offset);
    }
    return 0;
  }

  /**
   * Returns the text of the line containing the cursor.
   */
  public String currentLine() {
    int lineIndex = buffer.getLineIndex(cursor.offset());
    return buffer.getLine(lineIndex);
  }

  /**
   * Returns the line index (0-based) containing the cursor.
   */
  public int currentLineIndex() {
    return buffer.getLineIndex(cursor.offset());
  }

  /**
   * Returns the column (0-based) of the cursor within its line.
   */
  public int currentColumn() {
    return buffer.getColumn(cursor.offset());
  }

  /**
   * Inserts a character at the cursor position.
   */
  public void insertChar(char ch) {
    int offset = cursor.offset();
    undoManager.execute(new devkit.editor.undo.InsertOperation(offset, String.valueOf(ch)), buffer);
    cursor.setOffset(cursor.offset() + 1);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Inserted char at offset {}", offset);
  }

  /**
   * Inserts a string at the cursor position.
   */
  public void insertString(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    int offset = cursor.offset();
    undoManager.execute(new devkit.editor.undo.InsertOperation(offset, text), buffer);
    cursor.setOffset(cursor.offset() + text.length());
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Inserted {} chars at offset {}", text.length(), offset);
  }

  /**
   * Deletes the character at the cursor position (forward delete).
   */
  public void deleteForward() {
    int offset = cursor.offset();
    if (offset >= buffer.length()) {
      return;
    }
    char ch = buffer.charAt(offset);
    undoManager.execute(new devkit.editor.undo.DeleteOperation(offset, String.valueOf(ch)), buffer);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Deleted char at offset {}", offset);
  }

  /**
   * Deletes the character before the cursor (backspace).
   */
  public void deleteBackward() {
    int offset = cursor.offset();
    if (offset <= 0) {
      return;
    }
    int deleteOffset = offset - 1;
    char ch = buffer.charAt(deleteOffset);
    undoManager.execute(new devkit.editor.undo.DeleteOperation(deleteOffset, String.valueOf(ch)), buffer);
    cursor.setOffset(cursor.offset() - 1);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Deleted char before cursor at offset {}", deleteOffset);
  }

  /**
   * Inserts a newline at the cursor position.
   */
  public void insertNewline() {
    int offset = cursor.offset();
    undoManager.execute(new devkit.editor.undo.InsertOperation(offset, "\n"), buffer);
    cursor.setOffset(cursor.offset() + 1);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Inserted newline at offset {}", offset);
  }

  /**
   * Moves the cursor forward by one character.
   */
  public void moveForward() {
    if (cursor.offset() < buffer.length()) {
      cursor.setOffset(cursor.offset() + 1);
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
    }
  }

  /**
   * Moves the cursor backward by one character.
   */
  public void moveBackward() {
    if (cursor.offset() > 0) {
      cursor.setOffset(cursor.offset() - 1);
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
    }
  }

  /**
   * Moves the cursor to the start of the current line.
   */
  public void moveToLineStart() {
    int lineIndex = buffer.getLineIndex(cursor.offset());
    int start = buffer.lineStart(lineIndex);
    cursor.setOffset(start);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
  }

  /**
   * Moves the cursor to the end of the current line.
   */
  public void moveToLineEnd() {
    int lineIndex = buffer.getLineIndex(cursor.offset());
    int end = buffer.lineEnd(lineIndex);
    cursor.setOffset(end);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
  }

  /**
   * Moves the cursor up one line.
   */
  public void moveUp() {
    int lineIndex = buffer.getLineIndex(cursor.offset());
    if (lineIndex > 0) {
      int currentColumn = buffer.getColumn(cursor.offset());
      int targetLine = lineIndex - 1;
      int targetOffset = buffer.offsetFor(targetLine, currentColumn);
      cursor.setOffset(targetOffset);
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
    }
  }

  /**
   * Moves the cursor down one line.
   */
  public void moveDown() {
    int lineIndex = buffer.getLineIndex(cursor.offset());
    int lineCount = buffer.getLineCount();
    if (lineIndex < lineCount - 1) {
      int currentColumn = buffer.getColumn(cursor.offset());
      int targetLine = lineIndex + 1;
      int targetOffset = buffer.offsetFor(targetLine, currentColumn);
      cursor.setOffset(targetOffset);
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
    }
  }

  /**
   * Moves the cursor to the given offset.
   */
  public void setCursorOffset(int offset) {
    if (offset < 0 || offset > buffer.length()) {
      throw new IndexOutOfBoundsException("Offset " + offset + " out of range [0," + buffer.length() + "]");
    }
    cursor.setOffset(offset);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
  }

  /**
   * Selects text from the given start offset to the given end offset.
   */
  public void select(int start, int end) {
    if (start < 0 || start > buffer.length() || end < 0 || end > buffer.length()) {
      throw new IndexOutOfBoundsException("Invalid selection range [" + start + "," + end + "]");
    }
    this.selection = new Selection(start, end);
  }

  /**
   * Clears the current selection.
   */
  public void clearSelection() {
    this.selection = Selection.empty(cursor.offset());
  }

  /**
   * Returns the selected text, or null if nothing is selected.
   */
  public String getSelectedText() {
    if (selection.isEmpty()) {
      return null;
    }
    return buffer.substring(selection.min(), selection.max());
  }

  /**
   * Replaces the selected text with the given string. If no selection,
   * inserts at the cursor.
   */
  public void replaceSelection(String text) {
    if (text == null) {
      text = "";
    }
    if (selection.isEmpty()) {
      insertString(text);
      return;
    }
    int start = selection.min();
    int end = selection.max();
    String deleted = buffer.substring(start, end);
    undoManager.execute(new devkit.editor.undo.DeleteOperation(start, deleted), buffer);
    undoManager.execute(new devkit.editor.undo.InsertOperation(start, text), buffer);
    cursor.setOffset(start + text.length());
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
  }

  /**
   * Deletes the selected text, or the character before cursor if nothing
   * selected (backspace).
   */
  public void deleteSelection() {
    if (selection.isEmpty()) {
      deleteBackward();
      return;
    }
    int start = selection.min();
    int end = selection.max();
    String deleted = buffer.substring(start, end);
    undoManager.execute(new devkit.editor.undo.DeleteOperation(start, deleted), buffer);
    cursor.setOffset(start);
    updateCursorLineColumn();
    selection = Selection.empty(cursor.offset());
    modified = true;
    LOG.debug("Deleted selection of {} chars", deleted.length());
  }

  /**
   * Undoes the last edit operation.
   */
  public boolean undo() {
    boolean result = undoManager.undo(buffer);
    if (result) {
      // Clamp cursor to buffer length after undo (buffer may have shrunk)
      if (cursor.offset() > buffer.length()) {
        cursor.setOffset(buffer.length());
      }
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
      modified = undoManager.undoStackSize() > 0 || modified;
    }
    return result;
  }

  /**
   * Redoes the last undone operation.
   */
  public boolean redo() {
    boolean result = undoManager.redo(buffer);
    if (result) {
      updateCursorLineColumn();
      selection = Selection.empty(cursor.offset());
      modified = true;
    }
    return result;
  }

  /**
   * Sets the buffer content (e.g., when opening a file).
   */
  public void setContent(String content) {
    buffer.clear();
    if (content != null && !content.isEmpty()) {
      buffer.insert(content);
    }
    cursor.setOffset(0);
    updateCursorLineColumn();
    selection = Selection.empty(0);
    modified = false;
    LOG.debug("Set content, {} chars", content == null ? 0 : content.length());
  }

  /**
   * Creates a viewport from the current terminal dimensions.
   */
  public Viewport createViewport(int width, int height) {
    return new Viewport(0, 0, height, width);
  }

  private void updateCursorLineColumn() {
    cursor.setLine(buffer.getLineIndex(cursor.offset()));
    cursor.setColumn(buffer.getColumn(cursor.offset()));
  }
}
