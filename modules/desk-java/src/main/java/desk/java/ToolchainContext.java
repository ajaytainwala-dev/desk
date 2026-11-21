package desk.java;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Separates Desk's own runtime JVM from the project JDK and source/target
 * compatibility settings.
 */
public final class ToolchainContext {

  private final Path workspaceRoot;
  private final JdkInstallation projectJdk;
  private final int sourceCompatibility;
  private final int targetCompatibility;
  private final JdkVersion deskRuntimeVersion;

  public ToolchainContext(
      Path workspaceRoot,
      JdkInstallation projectJdk,
      int sourceCompatibility,
      int targetCompatibility,
      JdkVersion deskRuntimeVersion) {
    this.workspaceRoot = workspaceRoot;
    this.projectJdk = Objects.requireNonNull(projectJdk, "projectJdk");
    this.sourceCompatibility = sourceCompatibility;
    this.targetCompatibility = targetCompatibility;
    this.deskRuntimeVersion = deskRuntimeVersion;
  }

  public static ToolchainContext forWorkspace(Path workspaceRoot) {
    JdkInstallation project = JdkSelector.current(workspaceRoot)
        .orElseThrow(() -> new JavaException("No JDK available for workspace"));
    int release = project.version()
        .map(JdkVersion::feature)
        .orElse(JavaVersionPolicy.DEFAULT_PROJECT_RELEASE);
    JdkVersion runtime = JdkVersion.parse(System.getProperty("java.version")).orElse(null);
    return new ToolchainContext(workspaceRoot, project, release, release, runtime);
  }

  public Optional<Path> workspaceRoot() {
    return Optional.ofNullable(workspaceRoot);
  }

  /** JDK selected for this workspace/project (compiler + launcher). */
  public JdkInstallation projectJdk() {
    return projectJdk;
  }

  /** Source compatibility level for the project. */
  public int sourceCompatibility() {
    return sourceCompatibility;
  }

  /** Target bytecode compatibility level for the project. */
  public int targetCompatibility() {
    return targetCompatibility;
  }

  /** Version of the JVM currently executing Desk itself. */
  public Optional<JdkVersion> deskRuntimeVersion() {
    return Optional.ofNullable(deskRuntimeVersion);
  }

  public Path javac() {
    return projectJdk.javacExecutable()
        .orElseThrow(() -> new JavaException("Project JDK has no javac: " + projectJdk.home()));
  }

  public Path java() {
    return projectJdk.javaExecutable()
        .orElseThrow(() -> new JavaException("Project JDK has no java: " + projectJdk.home()));
  }

  public String describe() {
    StringBuilder sb = new StringBuilder();
    sb.append("Project JDK: ").append(projectJdk.summaryLine()).append('\n');
    sb.append("Source compatibility: ").append(sourceCompatibility).append('\n');
    sb.append("Target compatibility: ").append(targetCompatibility).append('\n');
    sb.append("Desk runtime: ")
        .append(deskRuntimeVersion != null ? deskRuntimeVersion : System.getProperty("java.version"))
        .append(" (").append(System.getProperty("java.home")).append(')');
    return sb.toString();
  }
}
