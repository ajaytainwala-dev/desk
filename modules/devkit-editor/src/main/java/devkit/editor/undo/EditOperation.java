package devkit.editor.undo;

import devkit.editor.buffer.GapBuffer;

/**
 * An edit operation that can be undone or redone.
 */
public interface EditOperation {

  /**
   * Applies this operation to the given buffer.
   */
  void apply(GapBuffer buffer);

  /**
   * Reverses this operation on the given buffer.
   */
  void unapply(GapBuffer buffer);

  /**
   * Returns a human-readable description of this operation.
   */
  String description();
}
