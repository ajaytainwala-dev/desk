package desk.core.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Persisted editor tab / cursor state for a workspace session.
 */
public final class OpenDocumentState {

  private final String id;
  private final Path path;
  private final int cursorLine;
  private final int cursorColumn;
  private final int scrollLine;

  public OpenDocumentState(String id, Path path, int cursorLine, int cursorColumn, int scrollLine) {
    this.id = Objects.requireNonNull(id, "id");
    this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    this.cursorLine = Math.max(0, cursorLine);
    this.cursorColumn = Math.max(0, cursorColumn);
    this.scrollLine = Math.max(0, scrollLine);
  }

  public String id() {
    return id;
  }

  public Path path() {
    return path;
  }

  public int cursorLine() {
    return cursorLine;
  }

  public int cursorColumn() {
    return cursorColumn;
  }

  public int scrollLine() {
    return scrollLine;
  }
}
