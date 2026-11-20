package desk.editor.undo;

import desk.editor.buffer.GapBuffer;

/**
 * Delete text operation.
 */
public record DeleteOperation(int offset, String deletedText) implements EditOperation {

  @Override
  public void apply(GapBuffer buffer) {
    buffer.delete(offset, deletedText.length());
  }

  @Override
  public void unapply(GapBuffer buffer) {
    buffer.insert(offset, deletedText);
  }

  @Override
  public String description() {
    return "Delete " + deletedText.length() + " char(s)";
  }
}
