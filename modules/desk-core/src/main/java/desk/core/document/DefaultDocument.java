package desk.core.document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link Document} implementation with mutable content and dirty state.
 */
public class DefaultDocument implements Document {

  private final String id;
  private Path path;
  private final String title;
  private String content;
  private final String encoding;
  private final boolean crlf;
  private boolean modified;
  private Instant lastSaved;
  private Integer cursorLine;
  private Integer cursorColumn;

  public DefaultDocument(Path path, String title, String content, String encoding,
                         boolean crlf, boolean modified, Instant lastSaved) {
    this(UUID.randomUUID().toString(), path, title, content, encoding, crlf, modified, lastSaved);
  }

  public DefaultDocument(String id, Path path, String content) {
    this(id, path,
        path != null && path.getFileName() != null ? path.getFileName().toString() : "Untitled",
        content != null ? content : "",
        "UTF-8",
        content != null && content.contains("\r\n"),
        false,
        path != null ? Instant.now() : null);
  }

  public DefaultDocument(String id, Path path, String title, String content, String encoding,
                         boolean crlf, boolean modified, Instant lastSaved) {
    this.id = id != null ? id : UUID.randomUUID().toString();
    this.path = path;
    this.title = title;
    this.content = content;
    this.encoding = encoding;
    this.crlf = crlf;
    this.modified = modified;
    this.lastSaved = lastSaved;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public Path path() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public String content() {
    return content;
  }

  @Override
  public String encoding() {
    return encoding;
  }

  @Override
  public boolean isCrlf() {
    return crlf;
  }

  @Override
  public boolean isModified() {
    return modified;
  }

  @Override
  public Instant lastSaved() {
    return lastSaved;
  }

  @Override
  public Integer cursorLine() {
    return cursorLine;
  }

  @Override
  public Integer cursorColumn() {
    return cursorColumn;
  }

  public void setContent(String content) {
    this.content = content;
    this.modified = true;
  }

  public void setModified(boolean modified) {
    this.modified = modified;
  }

  public void setCursor(int line, int column) {
    this.cursorLine = line;
    this.cursorColumn = column;
  }

  @Override
  public void markSaved() {
    this.modified = false;
    this.lastSaved = Instant.now();
  }

  /** @deprecated use {@link #markSaved()} */
  @Deprecated
  public void markSaved(Instant instant) {
    this.modified = false;
    this.lastSaved = instant != null ? instant : Instant.now();
  }

  @Override
  public String toString() {
    return "DefaultDocument{id=" + id + ", title=" + title + ", path=" + path
        + ", modified=" + modified + "}";
  }
}
