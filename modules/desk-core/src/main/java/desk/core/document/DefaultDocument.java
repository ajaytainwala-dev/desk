package desk.core.document;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Default {@link Document} implementation.
 */
public class DefaultDocument implements Document {

  private final Path path;
  private final String title;
  private String content;
  private final String encoding;
  private final boolean crlf;
  private boolean modified;
  private final Instant lastSaved;
  private Integer cursorLine;
  private Integer cursorColumn;

  public DefaultDocument(Path path, String title, String content, String encoding,
                         boolean crlf, boolean modified, Instant lastSaved) {
    this.path = path;
    this.title = title;
    this.content = content;
    this.encoding = encoding;
    this.crlf = crlf;
    this.modified = modified;
    this.lastSaved = lastSaved;
  }

  @Override
  public Path path() {
    return path;
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

  public void markSaved(Instant instant) {
    this.modified = false;
    // lastSaved is final; a saved snapshot would be created by the document
    // manager instead. Here we just clear the dirty flag.
  }

  @Override
  public String toString() {
    return "DefaultDocument{title=" + title + ", path=" + path + ", modified=" + modified + "}";
  }
}
