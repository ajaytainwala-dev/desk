package desk.java.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTemplatesSecurityTest {

  @TempDir
  Path temp;

  @Test
  void rejectsDotDotName() {
    assertThrows(Exception.class, () -> ProjectTemplates.create(
        new ProjectTemplates.CreateRequest(
            temp, "..", null, null, null, 25,
            ProjectTemplates.Template.BASIC_APP, "Main")));
  }

  @Test
  void rejectsEmbeddedDotDot() {
    assertThrows(Exception.class, () -> ProjectTemplates.create(
        new ProjectTemplates.CreateRequest(
            temp, "foo/../bar", null, null, null, 25,
            ProjectTemplates.Template.BASIC_APP, "Main")));
  }
}
