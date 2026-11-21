package desk.java.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDetectorTest {

  @TempDir
  Path temp;

  @Test
  @DisplayName("detects Maven project and Java version")
  void detectsMaven() throws Exception {
    Path root = temp.resolve("maven-app");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>ex</groupId>
          <artifactId>app</artifactId>
          <version>1.0</version>
          <properties>
            <java.version>25</java.version>
          </properties>
        </project>
        """);
    ProjectDescriptor d = ProjectDetector.detect(root);
    assertEquals("maven", d.buildSystem());
    assertEquals(ProjectType.MAVEN, d.type());
    assertEquals(25, d.detectedJavaVersion().orElseThrow());
  }

  @Test
  @DisplayName("detects Gradle toolchain version")
  void detectsGradle() throws Exception {
    Path root = temp.resolve("gradle-app");
    Files.createDirectories(root);
    Files.writeString(root.resolve("build.gradle"), """
        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(25)
          }
        }
        """);
    Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'app'\n");
    ProjectDescriptor d = ProjectDetector.detect(root);
    assertEquals("gradle", d.buildSystem());
    assertEquals(25, d.detectedJavaVersion().orElseThrow());
  }

  @Test
  @DisplayName("creates maven template with Java 25 default")
  void createsTemplate() throws Exception {
    Path created = ProjectTemplates.create(new ProjectTemplates.CreateRequest(
        temp,
        "demo",
        "com.example",
        "demo",
        "com.example.demo",
        0,
        ProjectTemplates.Template.MAVEN_APP,
        "Main"));
    assertTrue(Files.isRegularFile(created.resolve("pom.xml")));
    String pom = Files.readString(created.resolve("pom.xml"));
    assertTrue(pom.contains("<java.version>25</java.version>"));
    assertTrue(Files.isRegularFile(created.resolve("src/main/java/com/example/demo/Main.java")));
  }
}
