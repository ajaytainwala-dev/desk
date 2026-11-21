package desk.core.document;

import desk.core.filesystem.FileSystem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical in-memory registry of open documents.
 *
 * <p>UI components should reference documents by id rather than holding
 * independent text copies. Untitled documents must be saved via
 * {@link #saveAs} — {@link #saveAll} never silently drops them.</p>
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
    return List.copyOf(documents.values());
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
      throw new IllegalStateException(
          "Untitled document has no path — use saveAs(id, path) instead");
    }
    FileSystem.writeString(path, doc.content());
    doc.markSaved();
  }

  /**
   * Saves an untitled (or existing) document under a new path and updates
   * the path index so identity stays stable while the path changes.
   */
  public synchronized Document saveAs(String id, Path newPath) {
    Objects.requireNonNull(newPath, "newPath");
    Document doc = documents.get(id);
    if (doc == null) {
      throw new IllegalArgumentException("Unknown document: " + id);
    }
    Path normalized = newPath.toAbsolutePath().normalize();
    String conflict = pathIndex.get(normalized);
    if (conflict != null && !conflict.equals(id)) {
      throw new IllegalStateException("Another open document already uses path: " + normalized);
    }
    if (doc instanceof DefaultDocument dd) {
      Path old = dd.path();
      if (old != null) {
        pathIndex.remove(old.toAbsolutePath().normalize());
      }
      FileSystem.writeString(normalized, dd.content());
      dd.setPath(normalized);
      dd.markSaved();
      pathIndex.put(normalized, id);
      return dd;
    }
    throw new IllegalStateException("Document implementation does not support saveAs");
  }

  /**
   * Saves all dirty documents that have paths. Dirty untitled documents are
   * listed in {@link SaveAllResult#unsavedUntitled()} and are never silently
   * discarded.
   */
  public synchronized SaveAllResult saveAll() {
    List<String> saved = new ArrayList<>();
    List<String> unsavedUntitled = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (Document doc : documents.values()) {
      if (!doc.isDirty()) {
        continue;
      }
      if (doc.path() == null) {
        unsavedUntitled.add(doc.id());
        continue;
      }
      try {
        FileSystem.writeString(doc.path(), doc.content());
        doc.markSaved();
        saved.add(doc.id());
      } catch (RuntimeException e) {
        errors.add(doc.id() + ": " + e.getMessage());
      }
    }
    return new SaveAllResult(saved, unsavedUntitled, errors);
  }

  public record SaveAllResult(
      List<String> savedIds,
      List<String> unsavedUntitled,
      List<String> errors) {
    public boolean allClean() {
      return unsavedUntitled.isEmpty() && errors.isEmpty();
    }
  }
}
