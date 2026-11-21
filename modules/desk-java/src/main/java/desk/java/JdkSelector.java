package desk.java;

import desk.core.filesystem.FileSystem;
import desk.core.filesystem.FilesystemException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves which JDK applies to a workspace without mutating global
 * {@code JAVA_HOME}.
 *
 * <p>Precedence:</p>
 * <ol>
 *   <li>Workspace pin ({@code .desk/jdk.json})</li>
 *   <li>User default ({@code ~/.config/desk/jdk.json})</li>
 *   <li>Preferred discovered JDK</li>
 *   <li>Current JVM (last resort)</li>
 * </ol>
 */
public final class JdkSelector {

  private JdkSelector() {
  }

  public static Optional<JdkInstallation> current(Path workspaceRoot) {
    if (workspaceRoot != null) {
      Optional<JdkInstallation> pinned = readPin(workspaceJdkFile(workspaceRoot));
      if (pinned.isPresent()) {
        return pinned;
      }
    }
    Optional<JdkInstallation> user = readPin(JdkPaths.userDefaultJdkFile());
    if (user.isPresent()) {
      return user;
    }
    Optional<JdkInstallation> preferred = JdkDiscovery.preferredInstallation();
    if (preferred.isPresent()) {
      return preferred;
    }
    return JdkDiscovery.inspect(Path.of(System.getProperty("java.home")));
  }

  /**
   * Pins a JDK for a workspace only. Does not change system Java.
   */
  public static JdkInstallation useForWorkspace(Path workspaceRoot, String versionOrPath) {
    Objects.requireNonNull(workspaceRoot, "workspaceRoot");
    JdkInstallation selected = resolveSelection(versionOrPath);
    writePin(workspaceJdkFile(workspaceRoot), selected, true);
    return selected;
  }

  /**
   * Sets the user-level default JDK selection (not the OS {@code JAVA_HOME}).
   */
  public static JdkInstallation useUserDefault(String versionOrPath) {
    JdkInstallation selected = resolveSelection(versionOrPath);
    writePin(JdkPaths.userDefaultJdkFile(), selected, false);
    return selected;
  }

  public static void clearWorkspacePin(Path workspaceRoot) {
    Path file = workspaceJdkFile(workspaceRoot);
    try {
      Files.deleteIfExists(file);
    } catch (Exception e) {
      throw new JavaException("Unable to clear workspace JDK pin: " + e.getMessage(), e);
    }
  }

  public static Path workspaceJdkFile(Path workspaceRoot) {
    if (workspaceRoot == null) {
      throw new JavaException("No workspace root");
    }
    return workspaceRoot.resolve(".desk").resolve("jdk.json");
  }

  public static JdkInstallation resolveSelection(String versionOrPath) {
    Objects.requireNonNull(versionOrPath, "versionOrPath");
    String token = versionOrPath.trim();
    if (token.isEmpty()) {
      throw new JavaException("JDK version or path required");
    }

    Path asPath = Path.of(token);
    if (Files.isDirectory(asPath) || token.contains("/") || token.contains("\\")
        || token.length() > 3 && token.charAt(1) == ':') {
      JdkInstallation probed = JdkDiscovery.inspect(asPath)
          .orElseThrow(() -> new JavaException("Not a valid Java home: " + token));
      if (!probed.valid() || !probed.isDevelopmentKit()) {
        throw new JavaException("Path is not a usable JDK (need bin/javac): " + token);
      }
      return probed;
    }

    JdkVersion requested = JdkVersion.parse(token)
        .orElseThrow(() -> new JavaException("Unrecognized JDK version: " + token));

    return JdkDiscovery.findByFeature(requested.feature())
        .filter(JdkInstallation::isDevelopmentKit)
        .orElseThrow(() -> new JavaException(
            "No JDK " + requested.feature() + " found. Run 'desk java detect' or install one."));
  }

  private static Optional<JdkInstallation> readPin(Path pinFile) {
    if (pinFile == null || !Files.isRegularFile(pinFile)) {
      return Optional.empty();
    }
    try {
      String json = FileSystem.readString(pinFile);
      String home = extractJsonString(json, "home");
      if (home == null || home.isBlank()) {
        return Optional.empty();
      }
      Optional<JdkInstallation> install = JdkDiscovery.findByHome(Path.of(home));
      if (install.isPresent() && install.get().valid()) {
        return install;
      }
      // Re-probe even if not in discovery list
      return JdkDiscovery.inspect(Path.of(home)).filter(JdkInstallation::valid);
    } catch (FilesystemException e) {
      return Optional.empty();
    }
  }

  private static void writePin(Path pinFile, JdkInstallation selected, boolean workspace) {
    try {
      Path parent = pinFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String json = "{\n"
          + "  \"home\": " + jsonQuote(selected.home().toAbsolutePath().normalize().toString()) + ",\n"
          + "  \"version\": " + jsonQuote(selected.displayVersion()) + ",\n"
          + "  \"vendor\": " + jsonQuote(selected.vendor()) + ",\n"
          + "  \"scope\": " + jsonQuote(workspace ? "workspace" : "user") + ",\n"
          + "  \"feature\": " + selected.version().map(JdkVersion::feature).orElse(0) + "\n"
          + "}\n";
      FileSystem.writeString(pinFile, json);
    } catch (Exception e) {
      throw new JavaException("Unable to write JDK pin " + pinFile + ": " + e.getMessage(), e);
    }
  }

  static String extractJsonString(String json, String key) {
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) {
      return null;
    }
    int firstQuote = json.indexOf('"', colon + 1);
    if (firstQuote < 0) {
      return null;
    }
    int secondQuote = json.indexOf('"', firstQuote + 1);
    if (secondQuote < 0) {
      return null;
    }
    return json.substring(firstQuote + 1, secondQuote)
        .replace("\\\\", "\\")
        .replace("\\\"", "\"");
  }

  private static String jsonQuote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
