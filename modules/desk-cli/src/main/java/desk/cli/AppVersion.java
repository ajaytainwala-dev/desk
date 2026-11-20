package desk.cli;

/**
 * Central application version. Defined here in one place so the version is
 * consistent across the CLI, JAR manifest, and documentation.
 */
public final class AppVersion {

  public static final String VERSION = "0.1.0";
  public static final String NAME = "desk";

  private AppVersion() {
    // utility class
  }

  public static String fullVersion() {
    return NAME + " " + VERSION;
  }
}
