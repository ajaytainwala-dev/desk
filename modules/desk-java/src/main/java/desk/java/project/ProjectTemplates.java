package desk.java.project;

import desk.java.JavaVersionPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Creates new Java projects from templates. Default Java baseline is 25 LTS.
 */
public final class ProjectTemplates {

  public enum Template {
    BASIC_APP("basic", "Basic Java application"),
    CONSOLE("console", "Console application"),
    LIBRARY("library", "Java library"),
    MAVEN_APP("maven", "Maven application"),
    GRADLE_APP("gradle", "Gradle application"),
    TESTING("testing", "Unit testing project");

    private final String id;
    private final String description;

    Template(String id, String description) {
      this.id = id;
      this.description = description;
    }

    public String id() {
      return id;
    }

    public String description() {
      return description;
    }

    public static Template fromId(String id) {
      if (id == null || id.isBlank()) {
        return BASIC_APP;
      }
      String n = id.trim().toLowerCase(Locale.ROOT);
      for (Template t : values()) {
        if (t.id.equals(n) || t.name().equalsIgnoreCase(n)) {
          return t;
        }
      }
      throw new IllegalArgumentException("Unknown template: " + id);
    }
  }

  public record CreateRequest(
      Path parentDir,
      String name,
      String groupId,
      String artifactId,
      String packageName,
      int javaVersion,
      Template template,
      String mainClass) {
  }

  private ProjectTemplates() {
  }

  public static Path create(CreateRequest request) throws IOException {
    Objects.requireNonNull(request, "request");
    String name = sanitizeName(request.name());
    Path root = request.parentDir().resolve(name).toAbsolutePath().normalize();
    if (Files.exists(root)) {
      throw new IOException("Directory already exists: " + root);
    }
    Files.createDirectories(root);

    int javaVersion = request.javaVersion() > 0
        ? request.javaVersion()
        : JavaVersionPolicy.DEFAULT_PROJECT_RELEASE;
    String group = blankTo(request.groupId(), "com.example");
    String artifact = blankTo(request.artifactId(), name.toLowerCase(Locale.ROOT));
    String pkg = blankTo(request.packageName(), group + "." + artifact.replace("-", ""));
    String main = blankTo(request.mainClass(), "Main");
    Template template = request.template() != null ? request.template() : Template.BASIC_APP;

    Path pkgDir = root.resolve("src/main/java").resolve(pkg.replace('.', '/'));
    Files.createDirectories(pkgDir);
    Files.createDirectories(root.resolve("src/test/java").resolve(pkg.replace('.', '/')));

    String mainSource = """
        package %s;

        public final class %s {
          private %s() {}

          public static void main(String[] args) {
            System.out.println("Hello from %s");
          }
        }
        """.formatted(pkg, main, main, name);
    Files.writeString(pkgDir.resolve(main + ".java"), mainSource, StandardCharsets.UTF_8);

    if (template == Template.TESTING || template == Template.MAVEN_APP
        || template == Template.GRADLE_APP || template == Template.LIBRARY) {
      String testSource = """
          package %s;

          import org.junit.jupiter.api.Test;
          import static org.junit.jupiter.api.Assertions.assertTrue;

          class %sTest {
            @Test
            void smoke() {
              assertTrue(true);
            }
          }
          """.formatted(pkg, main);
      Files.writeString(
          root.resolve("src/test/java").resolve(pkg.replace('.', '/')).resolve(main + "Test.java"),
          testSource,
          StandardCharsets.UTF_8);
    }

    switch (template) {
      case MAVEN_APP, LIBRARY, TESTING -> writeMavenPom(root, group, artifact, javaVersion,
          template == Template.LIBRARY);
      case GRADLE_APP -> writeGradle(root, group, artifact, javaVersion, pkg + "." + main);
      case BASIC_APP, CONSOLE -> writePlainReadme(root, name, javaVersion);
      default -> writePlainReadme(root, name, javaVersion);
    }

    Files.writeString(root.resolve(".gitignore"),
        "target/\nbuild/\n.desk/\ndesk-out/\n.idea/\n*.iml\n.gradle/\n",
        StandardCharsets.UTF_8);

    // Workspace marker
    Files.createDirectories(root.resolve(".desk"));
    Files.writeString(root.resolve(".desk/settings.json"),
        "{\n  \"java.version\": " + javaVersion + ",\n  \"project.name\": \"" + name + "\"\n}\n",
        StandardCharsets.UTF_8);

    return root;
  }

  private static void writeMavenPom(Path root, String group, String artifact, int javaVersion,
      boolean library) throws IOException {
    String packaging = library ? "jar" : "jar";
    String pom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>0.1.0-SNAPSHOT</version>
          <packaging>%s</packaging>
          <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.release>%d</maven.compiler.release>
            <java.version>%d</java.version>
          </properties>
          <dependencies>
            <dependency>
              <groupId>org.junit.jupiter</groupId>
              <artifactId>junit-jupiter</artifactId>
              <version>5.11.3</version>
              <scope>test</scope>
            </dependency>
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
              </plugin>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
              </plugin>
            </plugins>
          </build>
        </project>
        """.formatted(group, artifact, packaging, javaVersion, javaVersion);
    Files.writeString(root.resolve("pom.xml"), pom, StandardCharsets.UTF_8);
  }

  private static void writeGradle(Path root, String group, String artifact, int javaVersion,
      String mainClass) throws IOException {
    String build = """
        plugins {
          id 'java'
          id 'application'
        }

        group = '%s'
        version = '0.1.0-SNAPSHOT'

        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(%d)
          }
        }

        repositories {
          mavenCentral()
        }

        dependencies {
          testImplementation 'org.junit.jupiter:junit-jupiter:5.11.3'
        }

        application {
          mainClass = '%s'
        }

        tasks.named('test') {
          useJUnitPlatform()
        }
        """.formatted(group, javaVersion, mainClass);
    Files.writeString(root.resolve("build.gradle"), build, StandardCharsets.UTF_8);
    Files.writeString(root.resolve("settings.gradle"),
        "rootProject.name = '" + artifact + "'\n", StandardCharsets.UTF_8);
  }

  private static void writePlainReadme(Path root, String name, int javaVersion) throws IOException {
    Files.writeString(root.resolve("README.md"),
        "# " + name + "\n\nPlain Java project (Java " + javaVersion + ").\n\n"
            + "Build with: `desk build`\nRun with: `desk run`\n",
        StandardCharsets.UTF_8);
  }

  private static String sanitizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Project name required");
    }
    String cleaned = name.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
    if (cleaned.isEmpty()) {
      throw new IllegalArgumentException("Invalid project name: " + name);
    }
    return cleaned;
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
