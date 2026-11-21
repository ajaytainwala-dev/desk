package desk.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JdkSelectorTest {

  @TempDir
  Path temp;

  @Test
  @DisplayName("workspace pin does not change system JAVA_HOME")
  void workspacePinIsolated() throws Exception {
    String before = System.getenv("JAVA_HOME");
    Path jdk = JdkDiscovery.preferredJdk();
    assertNotNull(jdk, "Need a JDK for this test");

    Path projectA = temp.resolve("project-a");
    Path projectB = temp.resolve("project-b");
    Files.createDirectories(projectA);
    Files.createDirectories(projectB);

    JdkInstallation used = JdkSelector.useForWorkspace(projectA, jdk.toString());
    assertEquals(jdk.toAbsolutePath().normalize(), used.home().toAbsolutePath().normalize());
    assertTrue(Files.isRegularFile(projectA.resolve(".desk/jdk.json")));
    assertFalse(Files.exists(projectB.resolve(".desk/jdk.json")));

    assertEquals(before, System.getenv("JAVA_HOME"));
  }

  @Test
  @DisplayName("extractJsonString reads home field")
  void extractJson() {
    String json = "{ \"home\": \"/opt/jdk-25\", \"version\": \"25\" }";
    assertEquals("/opt/jdk-25", JdkSelector.extractJsonString(json, "home"));
  }
}
