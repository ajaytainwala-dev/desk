package desk.java.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of automatic project structure detection.
 */
public final class ProjectDescriptor {

  private final Path root;
  private final ProjectType type;
  private final String buildSystem;
  private final Integer detectedJavaVersion;
  private final Path buildFile;
  private final List<Path> modules;
  private final boolean hasTests;

  public ProjectDescriptor(
      Path root,
      ProjectType type,
      String buildSystem,
      Integer detectedJavaVersion,
      Path buildFile,
      List<Path> modules,
      boolean hasTests) {
    this.root = Objects.requireNonNull(root, "root");
    this.type = Objects.requireNonNull(type, "type");
    this.buildSystem = buildSystem;
    this.detectedJavaVersion = detectedJavaVersion;
    this.buildFile = buildFile;
    this.modules = List.copyOf(modules != null ? modules : List.of());
    this.hasTests = hasTests;
  }

  public Path root() {
    return root;
  }

  public ProjectType type() {
    return type;
  }

  public String buildSystem() {
    return buildSystem;
  }

  public Optional<Integer> detectedJavaVersion() {
    return Optional.ofNullable(detectedJavaVersion);
  }

  public Optional<Path> buildFile() {
    return Optional.ofNullable(buildFile);
  }

  public List<Path> modules() {
    return modules;
  }

  public boolean hasTests() {
    return hasTests;
  }

  public String summary() {
    return type + " (" + buildSystem + ") at " + root
        + detectedJavaVersion().map(v -> " [Java " + v + "]").orElse("");
  }
}
