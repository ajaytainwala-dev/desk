package desk.java;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A discovered Java installation with identity metadata.
 *
 * <p>Separates runtime vs development kit, and records how the installation
 * was found so doctors and selectors can explain decisions.</p>
 */
public final class JdkInstallation {

  public enum Kind {
    JDK,
    JRE,
    UNKNOWN
  }

  public enum Source {
    JAVA_HOME,
    PATH,
    WINDOWS_REGISTRY,
    WINDOWS_PROGRAM_FILES,
    MACOS_JVMS,
    LINUX_SYSTEM,
    SDKMAN,
    DESK_MANAGED,
    VENDOR_DIR,
    CURRENT_JVM,
    OTHER
  }

  private final Path home;
  private final JdkVersion version;
  private final String vendor;
  private final String architecture;
  private final Kind kind;
  private final Source source;
  private final boolean valid;
  private final List<String> errors;
  private final Path javaExecutable;
  private final Path javacExecutable;

  public JdkInstallation(
      Path home,
      JdkVersion version,
      String vendor,
      String architecture,
      Kind kind,
      Source source,
      boolean valid,
      List<String> errors,
      Path javaExecutable,
      Path javacExecutable) {
    this.home = Objects.requireNonNull(home, "home");
    this.version = version;
    this.vendor = vendor != null ? vendor : "unknown";
    this.architecture = architecture != null ? architecture : "unknown";
    this.kind = kind != null ? kind : Kind.UNKNOWN;
    this.source = source != null ? source : Source.OTHER;
    this.valid = valid;
    this.errors = List.copyOf(errors != null ? errors : List.of());
    this.javaExecutable = javaExecutable;
    this.javacExecutable = javacExecutable;
  }

  public Path home() {
    return home;
  }

  public Optional<JdkVersion> version() {
    return Optional.ofNullable(version);
  }

  public String vendor() {
    return vendor;
  }

  public String architecture() {
    return architecture;
  }

  public Kind kind() {
    return kind;
  }

  public Source source() {
    return source;
  }

  public boolean valid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }

  public Optional<Path> javaExecutable() {
    return Optional.ofNullable(javaExecutable);
  }

  public Optional<Path> javacExecutable() {
    return Optional.ofNullable(javacExecutable);
  }

  public boolean isDevelopmentKit() {
    return kind == Kind.JDK && javacExecutable != null;
  }

  public String displayVersion() {
    return version != null ? version.toString() : "unknown";
  }

  public String summaryLine() {
    StringBuilder sb = new StringBuilder();
    sb.append("Java ").append(displayVersion());
    if (version != null && version.isLts()) {
      sb.append(" LTS");
    } else if (version != null) {
      sb.append(" (non-LTS)");
    }
    sb.append("  ").append(vendor);
    sb.append("  ").append(architecture);
    sb.append("  ").append(kind);
    sb.append("  [").append(source).append(']');
    if (!valid) {
      sb.append("  INVALID");
    }
    sb.append("  ").append(home);
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JdkInstallation that)) {
      return false;
    }
    return home.toAbsolutePath().normalize().equals(that.home.toAbsolutePath().normalize());
  }

  @Override
  public int hashCode() {
    return home.toAbsolutePath().normalize().hashCode();
  }
}
