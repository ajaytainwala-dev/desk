package desk.core.document;

import desk.core.filesystem.FileSystem;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical in-memory registry of open documents.
 *
 * <p>UI components should reference documents by id rather than holding
 * independent text copies.</p>
 */
public final class DocumentStore {

  private final Map<String, Document> documents = new LinkedHashMap<>();
  private final Map<Path, String> pathIndex = new LinkedHashMap<>();

  public synchronized Document openFile(Path path) {
    Objects.requireNonNull(path, "path");
    Path normalized = path.toAbsolutePath().normalize();
    String existingId = pathIndex.get(normalized);
    if (existingId != null) {
      Document existing = documents.get(existingId);
      if (existing != null) {
        return existing;
      }
    }
    String content = FileSystem.readString(normalized);
    String id = UUID.randomUUID().toString();
    Document doc = new DefaultDocument(id, normalized, content);
    documents.put(id, doc);
    pathIndex.put(normalized, id);
    return doc;
  }

  public synchronized Document createUntitled(String initialContent) {
    String id = UUID.randomUUID().toString();
    Document doc = new DefaultDocument(id, null, initialContent != null ? initialContent : "");
    documents.put(id, doc);
    return doc;
  }

  public synchronized Optional<Document> get(String id) {
    return Optional.ofNullable(documents.get(id));
  }

  public synchronized Optional<Document> getByPath(Path path) {
    String id = pathIndex.get(path.toAbsolutePath().normalize());
    return id == null ? Optional.empty() : Optional.ofNullable(documents.get(id));
  }

  public synchronized void close(String id) {
    Document removed = documents.remove(id);
    if (removed != null && removed.path() != null) {
      pathIndex.remove(removed.path().toAbsolutePath().normalize());
    }
  }

  public synchronized Collection<Document> all() {
    return Map.copyOf(documents).values();
  }

  public synchronized int size() {
    return documents.size();
  }

  public synchronized void save(String id) {
    Document doc = documents.get(id);
    if (doc == null) {
      throw new IllegalArgumentException("Unknown document: " + id);
    }
    Path path = doc.path();
    if (path == null) {
      throw new IllegalStateException("Untitled document has no path");
    }
    FileSystem.writeString(path, doc.content());
    doc.markSaved();
  }

  public synchronized void saveAll() {
    for (Document doc : documents.values()) {
      if (doc.isDirty() && doc.path() != null) {
        FileSystem.writeString(doc.path(), doc.content());
        doc.markSaved();
      }
    }
  }
}
