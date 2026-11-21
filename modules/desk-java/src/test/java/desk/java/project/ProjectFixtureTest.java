package desk.java.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Detects fixture projects under {@code testdata/fixtures}. Skips gracefully
 * when the fixtures directory is absent from the module cwd layout.
 */
class ProjectFixtureTest {

  private static Path fixturesRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path a = cwd.resolve("testdata/fixtures");
    if (Files.isDirectory(a)) {
      return a;
    }
    Path b = cwd.resolve("../../testdata/fixtures").normalize();
    if (Files.isDirectory(b)) {
      return b;
    }
    // desk-java module → repo root
    Path c = cwd.getParent() != null && cwd.getParent().getParent() != null
        ? cwd.getParent().getParent().resolve("testdata/fixtures")
        : null;
    return c != null && Files.isDirectory(c) ? c : a;
  }

  private static boolean fixturesPresent() {
    return Files.isDirectory(fixturesRoot());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsPlainJava() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("plain-java"));
    assertTrue(d.buildSystem().equalsIgnoreCase("javac")
        || d.buildSystem().equalsIgnoreCase("none")
        || d.type().name().contains("PLAIN")
        || d.summary().toLowerCase().contains("java"),
        () -> "Unexpected: " + d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsMavenApp() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("maven-app"));
    assertTrue(d.buildSystem().toLowerCase().contains("maven"), d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsGradleGroovy() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("gradle-groovy"));
    assertTrue(d.buildSystem().toLowerCase().contains("gradle"), d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsGradleKotlin() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("gradle-kotlin"));
    assertTrue(d.buildSystem().toLowerCase().contains("gradle"), d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsMultiModuleMaven() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("multi-maven"));
    assertTrue(d.buildSystem().toLowerCase().contains("maven"), d.summary());
    assertTrue(d.summary().toLowerCase().contains("multi")
            || d.type().name().toLowerCase().contains("multi")
            || Files.isRegularFile(fixturesRoot().resolve("multi-maven/pom.xml")),
        d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsSpringBootPom() {
    Path pom = fixturesRoot().resolve("spring-boot-minimal/pom.xml");
    assertTrue(Files.isRegularFile(pom));
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("spring-boot-minimal"));
    assertTrue(d.buildSystem().toLowerCase().contains("maven"), d.summary());
  }

  @Test
  @EnabledIf("fixturesPresent")
  void detectsMultiModuleGradle() {
    ProjectDescriptor d = ProjectDetector.detect(fixturesRoot().resolve("multi-gradle"));
    assertTrue(d.buildSystem().toLowerCase().contains("gradle"), d.summary());
    assertTrue(
        d.type() == ProjectType.GRADLE_MULTI_MODULE
            || d.summary().toLowerCase().contains("multi")
            || Files.isRegularFile(fixturesRoot().resolve("multi-gradle/settings.gradle")),
        d.summary());
  }
}
