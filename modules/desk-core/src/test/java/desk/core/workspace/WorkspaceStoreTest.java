package desk.core.workspace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceStoreTest {

  @TempDir
  Path temp;

  @Test
  @DisplayName("open creates .desk and remembers recent")
  void openAndRecent() {
    Path root = temp.resolve("ws");
    assertDoesNotThrow(() -> Files.createDirectories(root));
    Workspace ws = WorkspaceStore.open(root);
    assertEquals(root.toAbsolutePath().normalize(), ws.root());
    assertTrue(Files.isDirectory(ws.deskDir()));

    WorkspaceStore.saveSession(ws.withDocument(
        WorkspaceStore.newDocumentState(root.resolve("Main.java"), 2, 4, 0)));
    assertTrue(Files.isRegularFile(ws.sessionFile()));

    var recent = WorkspaceStore.listRecent();
    assertTrue(recent.stream().anyMatch(r -> r.root().equals(ws.root())));
  }

  @Test
  @DisplayName("document store keeps single identity per path")
  void documentStoreIdentity() throws Exception {
    Path file = temp.resolve("A.java");
    Files.writeString(file, "class A {}");
    desk.core.document.DocumentStore store = new desk.core.document.DocumentStore();
    var d1 = store.openFile(file);
    var d2 = store.openFile(file);
    assertEquals(d1.id(), d2.id());
    assertEquals(1, store.size());
  }
}
