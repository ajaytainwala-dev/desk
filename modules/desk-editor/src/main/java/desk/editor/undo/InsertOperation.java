package desk.editor.undo;

import desk.editor.buffer.GapBuffer;

/**
 * Insert text operation.
 */
public record InsertOperation(int offset, String text) implements EditOperation {

  @Override
  public void apply(GapBuffer buffer) {
    buffer.insert(offset, text);
  }

  @Override
  public void unapply(GapBuffer buffer) {
    buffer.delete(offset, text.length());
  }

  @Override
  public String description() {
    return "Insert \"" + (text.length() > 20 ? text.substring(0, 17) + "..." : text) + "\"";
  }
}
