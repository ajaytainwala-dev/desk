package desk.core.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreTest {

  @TempDir
  Path temp;

  @Test
  void openFileDeduplicatesByPath() throws Exception {
    Path file = temp.resolve("A.java");
    Files.writeString(file, "class A {}");
    DocumentStore store = new DocumentStore();
    Document a = store.openFile(file);
    Document b = store.openFile(file);
    assertSame(a, b);
    assertEquals(1, store.size());
  }

  @Test
  void untitledSaveAsPersistsAndIndexes() throws Exception {
    DocumentStore store = new DocumentStore();
    Document doc = store.createUntitled("class U {}");
    assertTrue(doc.isDirty() || !doc.content().isEmpty());
    ((DefaultDocument) doc).setModified(true);
    Path dest = temp.resolve("U.java");
    Document saved = store.saveAs(doc.id(), dest);
    assertEquals(dest.toAbsolutePath().normalize(), saved.path().toAbsolutePath().normalize());
    assertFalse(saved.isDirty());
    assertEquals("class U {}", Files.readString(dest));
    assertSame(saved, store.getByPath(dest).orElseThrow());
  }

  @Test
  void saveAllReportsUntitled() {
    DocumentStore store = new DocumentStore();
    Document doc = store.createUntitled("x");
    ((DefaultDocument) doc).setModified(true);
    DocumentStore.SaveAllResult result = store.saveAll();
    assertFalse(result.allClean());
    assertEquals(1, result.unsavedUntitled().size());
    assertEquals(doc.id(), result.unsavedUntitled().get(0));
  }

  @Test
  void saveUntitledWithoutPathThrows() {
    DocumentStore store = new DocumentStore();
    Document doc = store.createUntitled("x");
    assertThrows(IllegalStateException.class, () -> store.save(doc.id()));
  }

  @Test
  void saveAsReindexesRename() throws Exception {
    Path a = temp.resolve("A.java");
    Files.writeString(a, "class A {}");
    DocumentStore store = new DocumentStore();
    Document doc = store.openFile(a);
    ((DefaultDocument) doc).setContent("class A2 {}");
    Path b = temp.resolve("B.java");
    store.saveAs(doc.id(), b);
    assertTrue(store.getByPath(a).isEmpty());
    assertTrue(store.getByPath(b).isPresent());
    assertEquals("class A2 {}", Files.readString(b));
  }
}
