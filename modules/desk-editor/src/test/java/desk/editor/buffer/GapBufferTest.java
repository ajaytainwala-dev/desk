package desk.editor.buffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GapBufferTest {

  @Test
  void emptyBuffer() {
    GapBuffer buffer = new GapBuffer();
    assertEquals(0, buffer.length());
    assertTrue(buffer.isEmpty());
    assertEquals("", buffer.toString());
  }

  @Test
  void fromString() {
    GapBuffer buffer = GapBuffer.from("hello");
    assertEquals(5, buffer.length());
    assertEquals("hello", buffer.toString());
  }

  @Test
  void charAt() {
    GapBuffer buffer = GapBuffer.from("abc");
    assertEquals('a', buffer.charAt(0));
    assertEquals('b', buffer.charAt(1));
    assertEquals('c', buffer.charAt(2));
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.charAt(3));
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.charAt(-1));
  }

  @Test
  void insert() {
    GapBuffer buffer = GapBuffer.from("hello");
    buffer.insert(5, " world");
    assertEquals("hello world", buffer.toString());
    assertEquals(11, buffer.length());
  }

  @Test
  void insertAtStart() {
    GapBuffer buffer = GapBuffer.from("world");
    buffer.insert(0, "hello ");
    assertEquals("hello world", buffer.toString());
  }

  @Test
  void insertInMiddle() {
    GapBuffer buffer = GapBuffer.from("helo");
    buffer.insert(3, "l");
    assertEquals("hello", buffer.toString());
  }

  @Test
  void delete() {
    GapBuffer buffer = GapBuffer.from("hello world");
    buffer.delete(5, 6);
    assertEquals("hello", buffer.toString());
  }

  @Test
  void deleteSingle() {
    GapBuffer buffer = GapBuffer.from("hello");
    buffer.delete(2);
    assertEquals("helo", buffer.toString());
  }

  @Test
  void replace() {
    GapBuffer buffer = GapBuffer.from("hello world");
    buffer.replace(6, 11, "there");
    assertEquals("hello there", buffer.toString());
  }

  @Test
  void replaceWithEmpty() {
    GapBuffer buffer = GapBuffer.from("hello world");
    buffer.replace(5, 11, "");
    assertEquals("hello", buffer.toString());
  }

  @Test
  void clear() {
    GapBuffer buffer = GapBuffer.from("hello");
    buffer.clear();
    assertEquals(0, buffer.length());
    assertTrue(buffer.isEmpty());
  }

  @Test
  void lines() {
    GapBuffer buffer = GapBuffer.from("line1\nline2\nline3");
    assertEquals(3, buffer.getLineCount());
    assertEquals("line1", buffer.getLine(0));
    assertEquals("line2", buffer.getLine(1));
    assertEquals("line3", buffer.getLine(2));
    assertEquals("", buffer.getLine(3));
  }

  @Test
  void lineStartEnd() {
    GapBuffer buffer = GapBuffer.from("abc\ndef\nghi");
    assertEquals(0, buffer.lineStart(0));
    assertEquals(3, buffer.lineEnd(0));
    assertEquals(4, buffer.lineStart(1));
    assertEquals(7, buffer.lineEnd(1));
    assertEquals(8, buffer.lineStart(2));
    assertEquals(11, buffer.lineEnd(2));
  }

  @Test
  void getLineIndex() {
    GapBuffer buffer = GapBuffer.from("a\nb\nc");
    assertEquals(0, buffer.getLineIndex(0));
    assertEquals(1, buffer.getLineIndex(2));
    assertEquals(2, buffer.getLineIndex(4));
  }

  @Test
  void getColumn() {
    GapBuffer buffer = GapBuffer.from("abc\ndef");
    assertEquals(0, buffer.getColumn(0));
    assertEquals(2, buffer.getColumn(2));
    assertEquals(0, buffer.getColumn(4));
    assertEquals(2, buffer.getColumn(6));
  }

  @Test
  void offsetFor() {
    GapBuffer buffer = GapBuffer.from("abc\ndefg");
    assertEquals(0, buffer.offsetFor(0, 0));
    assertEquals(2, buffer.offsetFor(0, 2));
    assertEquals(4, buffer.offsetFor(1, 0));
    assertEquals(6, buffer.offsetFor(1, 2));
  }

  @Test
  void largeInsertAndDelete() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      sb.append('x');
    }
    String large = sb.toString();
    GapBuffer buffer = new GapBuffer();
    buffer.insert(large);
    assertEquals(10000, buffer.length());
    assertEquals(large, buffer.toString());
    buffer.delete(5000, 1000);
    assertEquals(9000, buffer.length());
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 5000; i++) expected.append('x');
    for (int i = 6000; i < 10000; i++) expected.append('x');
    assertEquals(expected.toString(), buffer.toString());
  }
}
