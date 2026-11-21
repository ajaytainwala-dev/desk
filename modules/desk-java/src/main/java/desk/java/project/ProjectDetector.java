package desk.java.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects plain / Maven / Gradle (including multi-module) Java projects
 * without forcing a Desk-specific layout.
 */
public final class ProjectDetector {

  private static final Pattern MAVEN_RELEASE = Pattern.compile(
      "<maven\\.compiler\\.(?:release|source|target)>\\s*([0-9]+)\\s*<");
  private static final Pattern MAVEN_JAVA_VERSION = Pattern.compile(
      "<java\\.version>\\s*([0-9]+)\\s*<");
  private static final Pattern GRADLE_RELEASE = Pattern.compile(
      "(?:sourceCompatibility|targetCompatibility|JavaVersion\\.VERSION_)\\s*[=:]?\\s*['\"]?([0-9]+)");
  private static final Pattern GRADLE_TOOLCHAIN = Pattern.compile(
      "languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\(([0-9]+)\\)");

  private ProjectDetector() {
  }

  public static ProjectDescriptor detect(Path root) {
    Path normalized = root.toAbsolutePath().normalize();
    if (!Files.isDirectory(normalized)) {
      throw new IllegalArgumentException("Not a directory: " + normalized);
    }

    Path pom = normalized.resolve("pom.xml");
    Path gradleGroovy = normalized.resolve("build.gradle");
    Path gradleKotlin = normalized.resolve("build.gradle.kts");
    Path settingsGradle = normalized.resolve("settings.gradle");
    Path settingsGradleKts = normalized.resolve("settings.gradle.kts");

    boolean hasTests = hasTestTree(normalized);

    if (Files.isRegularFile(pom)) {
      List<Path> modules = readMavenModules(pom, normalized);
      Integer javaVer = readMavenJavaVersion(pom).orElse(null);
      ProjectType type = modules.size() > 1 ? ProjectType.MAVEN_MULTI_MODULE : ProjectType.MAVEN;
      if (modules.isEmpty() && hasTests) {
        type = ProjectType.JAVA_TESTING;
      }
      return new ProjectDescriptor(normalized, type, "maven", javaVer, pom, modules, hasTests);
    }

    if (Files.isRegularFile(gradleGroovy) || Files.isRegularFile(gradleKotlin)
        || Files.isRegularFile(settingsGradle) || Files.isRegularFile(settingsGradleKts)) {
      Path buildFile = Files.isRegularFile(gradleKotlin) ? gradleKotlin
          : Files.isRegularFile(gradleGroovy) ? gradleGroovy
          : Files.isRegularFile(settingsGradleKts) ? settingsGradleKts : settingsGradle;
      List<Path> modules = readGradleModules(normalized);
      Integer javaVer = readGradleJavaVersion(buildFile).orElse(null);
      ProjectType type = modules.size() > 1 ? ProjectType.GRADLE_MULTI_MODULE : ProjectType.GRADLE;
      return new ProjectDescriptor(normalized, type, "gradle", javaVer, buildFile, modules, hasTests);
    }

    boolean hasSrc = Files.isDirectory(normalized.resolve("src"))
        || hasJavaFiles(normalized);
    ProjectType type = hasSrc
        ? (hasTests ? ProjectType.JAVA_TESTING : ProjectType.PLAIN_JAVA)
        : ProjectType.PLAIN_JAVA;
    if (Files.isRegularFile(normalized.resolve("module-info.java"))
        || Files.isDirectory(normalized.resolve("src/main/java"))) {
      type = ProjectType.JAVA_APPLICATION;
    }
    return new ProjectDescriptor(normalized, type, "javac", null, null, List.of(), hasTests);
  }

  private static boolean hasTestTree(Path root) {
    return Files.isDirectory(root.resolve("src/test"))
        || Files.isDirectory(root.resolve("test"));
  }

  private static boolean hasJavaFiles(Path root) {
    try (Stream<Path> stream = Files.walk(root, 3)) {
      return stream.anyMatch(p -> p.getFileName() != null
          && p.getFileName().toString().endsWith(".java"));
    } catch (IOException e) {
      return false;
    }
  }

  private static List<Path> readMavenModules(Path pom, Path root) {
    List<Path> modules = new ArrayList<>();
    modules.add(root);
    try {
      String text = Files.readString(pom, StandardCharsets.UTF_8);
      Matcher m = Pattern.compile("<module>\\s*([^<]+)\\s*</module>").matcher(text);
      while (m.find()) {
        modules.add(root.resolve(m.group(1).trim()).normalize());
      }
    } catch (IOException ignored) {
      // ignore
    }
    return modules;
  }

  private static List<Path> readGradleModules(Path root) {
    List<Path> modules = new ArrayList<>();
    modules.add(root);
    for (String name : List.of("settings.gradle", "settings.gradle.kts")) {
      Path settings = root.resolve(name);
      if (!Files.isRegularFile(settings)) {
        continue;
      }
      try {
        String text = Files.readString(settings, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("include\\s*\\(?\\s*['\"]([^'\"]+)['\"]").matcher(text);
        while (m.find()) {
          String mod = m.group(1).replace(':', '/');
          if (mod.startsWith("/")) {
            mod = mod.substring(1);
          }
          modules.add(root.resolve(mod).normalize());
        }
      } catch (IOException ignored) {
        // ignore
      }
    }
    return modules;
  }

  private static Optional<Integer> readMavenJavaVersion(Path pom) {
    try {
      String text = Files.readString(pom, StandardCharsets.UTF_8);
      Matcher m = MAVEN_JAVA_VERSION.matcher(text);
      if (m.find()) {
        return Optional.of(Integer.parseInt(m.group(1)));
      }
      m = MAVEN_RELEASE.matcher(text);
      if (m.find()) {
        return Optional.of(Integer.parseInt(m.group(1)));
      }
    } catch (Exception ignored) {
      // ignore
    }
    return Optional.empty();
  }

  private static Optional<Integer> readGradleJavaVersion(Path buildFile) {
    if (buildFile == null || !Files.isRegularFile(buildFile)) {
      return Optional.empty();
    }
    try {
      String text = Files.readString(buildFile, StandardCharsets.UTF_8);
      Matcher m = GRADLE_TOOLCHAIN.matcher(text);
      if (m.find()) {
        return Optional.of(Integer.parseInt(m.group(1)));
      }
      m = GRADLE_RELEASE.matcher(text);
      if (m.find()) {
        return Optional.of(Integer.parseInt(m.group(1)));
      }
    } catch (Exception ignored) {
      // ignore
    }
    return Optional.empty();
  }
}
