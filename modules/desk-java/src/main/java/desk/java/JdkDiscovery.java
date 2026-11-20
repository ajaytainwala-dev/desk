package desk.java;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Discovers installed JDKs on the host system.
 *
 * <p>Checks common JDK locations and the {@code JAVA_HOME} environment
 * variable. Returns a list of JDK home directories that contain a valid
 * {@code bin/javac} executable.</p>
 */
public final class JdkDiscovery {

  private JdkDiscovery() {
    // utility class
  }

  /**
   * Returns a list of discovered JDK home directories.
   *
   * <p>On Windows, checks {@code JAVA_HOME} and common locations under
   * {@code C:\Program Files\Java} and {@code C:\Program Files (x86)\Java}.
   * On Unix-like systems, checks {@code JAVA_HOME} and common locations
   * under {@code /usr/lib/jvm} and {@code /usr/local}.</p>
   */
  public static List<Path> discover() {
    List<Path> jdks = new ArrayList<>();

    // Check JAVA_HOME first.
    String javaHome = System.getenv("JAVA_HOME");
    if (javaHome != null) {
      Path home = Path.of(javaHome);
      if (isValidJdk(home)) {
        jdks.add(home);
      }
    }

    // Check common Windows locations.
    if (isWindows()) {
      String programFiles = System.getenv("ProgramFiles");
      if (programFiles != null) {
        checkJavaDir(jdks, Path.of(programFiles, "Java"));
        checkJavaDir(jdks, Path.of(programFiles, "Java", "jdk"));
      }
      String programFilesX86 = System.getenv("ProgramFiles(x86)");
      if (programFilesX86 != null) {
        checkJavaDir(jdks, Path.of(programFilesX86, "Java"));
        checkJavaDir(jdks, Path.of(programFilesX86, "Java", "jdk"));
      }
    } else {
      // Unix-like locations.
      checkJavaDir(jdks, Path.of("/usr/lib/jvm"));
      checkJavaDir(jdks, Path.of("/usr/local"));
      checkJavaDir(jdks, Path.of("/opt"));
    }

    return jdks;
  }

  /**
   * Returns the preferred JDK home: the first discovered JDK, or {@code null}
   * if none found.
   */
  public static Path preferredJdk() {
    List<Path> jdks = discover();
    return jdks.isEmpty() ? null : jdks.get(0);
  }

  private static void checkJavaDir(List<Path> jdks, Path dir) {
    if (dir == null || !java.nio.file.Files.isDirectory(dir)) {
      return;
    }
    try (var stream = java.nio.file.Files.list(dir)) {
      stream.forEach(child -> {
        if (isValidJdk(child)) {
          jdks.add(child);
        }
      });
    } catch (Exception ignored) {
      // ignore directory listing errors
    }
  }

  private static boolean isValidJdk(Path home) {
    if (home == null || !java.nio.file.Files.isDirectory(home)) {
      return false;
    }
    Path javac = home.resolve("bin").resolve("javac" + exeSuffix());
    return java.nio.file.Files.isRegularFile(javac);
  }

  private static boolean isWindows() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\");
  }

  private static String exeSuffix() {
    return isWindows() ? ".exe" : "";
  }
}
