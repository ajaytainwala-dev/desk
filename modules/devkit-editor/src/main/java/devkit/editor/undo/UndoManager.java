package devkit.editor.undo;

import devkit.editor.buffer.GapBuffer;

import java.util.ArrayDeque;

/**
 * Manages undo and redo stacks for the editor.
 */
public class UndoManager {

  private final ArrayDeque<EditOperation> undoStack = new ArrayDeque<>();
  private final ArrayDeque<EditOperation> redoStack = new ArrayDeque<>();
  private int maxSize = 1000;

  public UndoManager() {
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  /**
   * Records an edit operation and applies it to the buffer.
   */
  public void execute(EditOperation operation, GapBuffer buffer) {
    operation.apply(buffer);
    undoStack.push(operation);
    if (undoStack.size() > maxSize) {
      undoStack.removeLast();
    }
    redoStack.clear();
  }

  /**
   * Undoes the last operation.
   * Returns true if an operation was undone.
   */
  public boolean undo(GapBuffer buffer) {
    if (undoStack.isEmpty()) {
      return false;
    }
    EditOperation operation = undoStack.pop();
    operation.unapply(buffer);
    redoStack.push(operation);
    return true;
  }

  /**
   * Redoes the last undone operation.
   * Returns true if an operation was redone.
   */
  public boolean redo(GapBuffer buffer) {
    if (redoStack.isEmpty()) {
      return false;
    }
    EditOperation operation = redoStack.pop();
    operation.apply(buffer);
    undoStack.push(operation);
    return true;
  }

  public void clear() {
    undoStack.clear();
    redoStack.clear();
  }

  public int undoStackSize() {
    return undoStack.size();
  }

  public int redoStackSize() {
    return redoStack.size();
  }
}
