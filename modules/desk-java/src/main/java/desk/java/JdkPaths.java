package desk.java;

import java.nio.file.Path;

/**
 * Well-known Desk paths for managed JDKs and toolchain state.
 */
public final class JdkPaths {

  private JdkPaths() {
  }

  public static Path deskHome() {
    String override = System.getenv("DESK_HOME");
    if (override != null && !override.isBlank()) {
      return Path.of(override.trim());
    }
    String userHome = System.getProperty("user.home");
    if (userHome == null) {
      return Path.of(".desk-home");
    }
    return Path.of(userHome, ".desk");
  }

  public static Path managedJdksRoot() {
    return deskHome().resolve("jdks");
  }

  public static Path userConfigDir() {
    String home = System.getProperty("user.home");
    if (home == null) {
      return Path.of(".config", "desk");
    }
    return Path.of(home, ".config", "desk");
  }

  public static Path userDefaultJdkFile() {
    return userConfigDir().resolve("jdk.json");
  }
}
