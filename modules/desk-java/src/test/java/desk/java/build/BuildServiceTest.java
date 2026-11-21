package desk.java.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildServiceTest {

  @TempDir
  Path temp;

  @Test
  @DisplayName("parses javac-style diagnostics")
  void parsesJavacDiagnostics() {
    String output = "src/Main.java:3: error: ';' expected\n";
    List<BuildDiagnostic> diags = BuildService.parseDiagnostics(
        output, temp, BuildSystem.JAVAC);
    assertEquals(1, diags.size());
    assertEquals(3, diags.get(0).line());
    assertEquals(BuildDiagnostic.Severity.ERROR, diags.get(0).severity());
  }

  @Test
  @DisplayName("javac build compiles plain project")
  void compilesPlainProject() throws Exception {
    Path root = temp.resolve("plain");
    Files.createDirectories(root.resolve("src/main/java"));
    Files.writeString(root.resolve("src/main/java/Hello.java"), """
        public class Hello {
          public static void main(String[] args) {
            System.out.println("hi");
          }
        }
        """);
    Path jdk = desk.java.JdkDiscovery.preferredJdk();
    assertNotNull(jdk);
    BuildResult result = BuildService.execute(
        new BuildRequest(root, BuildRequest.Goal.COMPILE, jdk, false));
    assertTrue(result.success(), result.output());
    assertEquals(BuildSystem.JAVAC, result.system());
  }
}
