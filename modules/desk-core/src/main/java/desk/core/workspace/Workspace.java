package desk.core.workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An open Desk workspace rooted at a directory.
 */
public final class Workspace {

  private final Path root;
  private final String name;
  private final Instant openedAt;
  private final List<OpenDocumentState> openDocuments;
  private final String activeDocumentId;

  public Workspace(
      Path root,
      String name,
      Instant openedAt,
      List<OpenDocumentState> openDocuments,
      String activeDocumentId) {
    this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    this.name = name != null ? name : this.root.getFileName() != null
        ? this.root.getFileName().toString()
        : this.root.toString();
    this.openedAt = openedAt != null ? openedAt : Instant.now();
    this.openDocuments = List.copyOf(openDocuments != null ? openDocuments : List.of());
    this.activeDocumentId = activeDocumentId;
  }

  public Path root() {
    return root;
  }

  public String name() {
    return name;
  }

  public Instant openedAt() {
    return openedAt;
  }

  public List<OpenDocumentState> openDocuments() {
    return openDocuments;
  }

  public Optional<String> activeDocumentId() {
    return Optional.ofNullable(activeDocumentId);
  }

  public Path deskDir() {
    return root.resolve(".desk");
  }

  public Path settingsFile() {
    return deskDir().resolve("settings.json");
  }

  public Path sessionFile() {
    return deskDir().resolve("session.json");
  }

  public Path jdkPinFile() {
    return deskDir().resolve("jdk.json");
  }

  public Workspace withOpenDocuments(List<OpenDocumentState> docs, String activeId) {
    return new Workspace(root, name, openedAt, docs, activeId);
  }

  public Workspace withDocument(OpenDocumentState doc) {
    List<OpenDocumentState> next = new ArrayList<>();
    boolean replaced = false;
    for (OpenDocumentState existing : openDocuments) {
      if (existing.id().equals(doc.id()) || existing.path().equals(doc.path())) {
        next.add(doc);
        replaced = true;
      } else {
        next.add(existing);
      }
    }
    if (!replaced) {
      next.add(doc);
    }
    return withOpenDocuments(next, doc.id());
  }
}
