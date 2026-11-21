package desk.core.document;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A single open document: its contents, path, encoding, and modification
 * state.
 *
 * <p>The document is independent of any UI or editor component. UI layers
 * should reference documents by {@link #id()} via {@link DocumentStore}
 * instead of keeping divergent text copies.</p>
 */
public interface Document {

  /**
   * Stable identity for this open document within a {@link DocumentStore}.
   */
  String id();

  /**
   * The file path, or {@code null} for an unsaved new document.
   */
  Path path();

  /**
   * The document title shown in the UI.
   */
  String title();

  /**
   * The current text content.
   */
  String content();

  /**
   * The character encoding used to read/write this document.
   */
  String encoding();

  /**
   * Whether the line ending is CRLF.
   */
  boolean isCrlf();

  /**
   * Whether the document has unsaved changes.
   */
  boolean isModified();

  /**
   * Alias for {@link #isModified()}.
   */
  default boolean isDirty() {
    return isModified();
  }

  /**
   * The instant the document was last saved, or {@code null} if never saved.
   */
  Instant lastSaved();

  /**
   * The line number (1-based) of the cursor, or {@code null} if unset.
   */
  Integer cursorLine();

  /**
   * The column of the cursor (1-based), or {@code null} if unset.
   */
  Integer cursorColumn();

  /**
   * Clears the dirty flag after a successful save.
   */
  void markSaved();
}
