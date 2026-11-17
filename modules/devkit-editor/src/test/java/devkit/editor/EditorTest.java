package devkit.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorTest {

  @Test
  void initialState() {
    Editor editor = new Editor();
    assertEquals(0, editor.buffer().length());
    assertEquals(0, editor.cursor().offset());
    assertTrue(editor.selection().isEmpty());
    assertFalse(editor.isModified());
  }

  @Test
  void insertAndCursor() {
    Editor editor = new Editor();
    editor.insertString("hello");
    assertEquals("hello", editor.buffer().toString());
    assertEquals(5, editor.cursor().offset());
    assertTrue(editor.isModified());
  }

  @Test
  void insertNewline() {
    Editor editor = new Editor();
    editor.insertString("line1");
    editor.insertNewline();
    editor.insertString("line2");
    assertEquals("line1\nline2", editor.buffer().toString());
    assertEquals(1, editor.currentLineIndex());
    assertEquals("line2", editor.currentLine());
  }

  @Test
  void moveCursor() {
    Editor editor = new Editor();
    editor.insertString("hello");
    editor.moveToLineStart();
    assertEquals(0, editor.cursor().offset());
    editor.moveForward();
    assertEquals(1, editor.cursor().offset());
    editor.moveToLineEnd();
    assertEquals(5, editor.cursor().offset());
  }

  @Test
  void backspace() {
    Editor editor = new Editor();
    editor.insertString("hello");
    editor.setCursorOffset(3);
    editor.deleteBackward();
    assertEquals("helo", editor.buffer().toString());
    assertEquals(2, editor.cursor().offset());
  }

  @Test
  void deleteForward() {
    Editor editor = new Editor();
    editor.insertString("hello");
    editor.setCursorOffset(2);
    editor.deleteForward();
    assertEquals("helo", editor.buffer().toString());
    assertEquals(2, editor.cursor().offset());
  }

  @Test
  void selection() {
    Editor editor = new Editor();
    editor.insertString("hello world");
    editor.select(0, 5);
    assertEquals("hello", editor.getSelectedText());
    assertFalse(editor.selection().isEmpty());
  }

  @Test
  void replaceSelection() {
    Editor editor = new Editor();
    editor.insertString("hello world");
    editor.select(0, 5);
    editor.replaceSelection("goodbye");
    assertEquals("goodbye world", editor.buffer().toString());
    assertTrue(editor.selection().isEmpty());
  }

  @Test
  void deleteSelection() {
    Editor editor = new Editor();
    editor.insertString("hello world");
    editor.select(0, 5);
    editor.deleteSelection();
    assertEquals(" world", editor.buffer().toString());
    assertEquals(0, editor.cursor().offset());
  }

  @Test
  void undo() {
    Editor editor = new Editor();
    editor.insertString("hello");
    assertTrue(editor.undoManager().canUndo());
    assertTrue(editor.undo());
    assertEquals("", editor.buffer().toString());
    assertFalse(editor.undoManager().canUndo());
  }

  @Test
  void redo() {
    Editor editor = new Editor();
    editor.insertString("hello");
    editor.undo();
    assertTrue(editor.undoManager().canRedo());
    assertTrue(editor.redo());
    assertEquals("hello", editor.buffer().toString());
  }

  @Test
  void setContent() {
    Editor editor = new Editor();
    editor.insertString("old content");
    editor.setContent("new content");
    assertEquals("new content", editor.buffer().toString());
    assertFalse(editor.isModified());
    assertEquals(0, editor.cursor().offset());
  }

  @Test
  void multiLineNavigation() {
    Editor editor = new Editor();
    editor.insertString("line1\nline2\nline3");
    editor.setCursorOffset(6); // start of "line2"
    editor.moveUp();
    assertEquals(0, editor.cursor().offset()); // start of "line1"
    editor.moveDown();
    assertEquals(6, editor.cursor().offset()); // back to start of "line2"
  }
}
