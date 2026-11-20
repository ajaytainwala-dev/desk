package desk.java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs compiled Java programs using the platform's {@code java} launcher.
 *
 * <p>Uses the discovered JDK's {@code java} command. If no JDK is found,
 * execution fails with a clear error message.</p>
 */
public final class JavaRunner {

  private JavaRunner() {
    // utility class
  }

  /**
   * Runs a compiled Java class.
   *
   * @param className  the fully qualified class name to run (must not be {@code null})
   * @param classpath  classpath entries including the output directory (may be empty)
   * @param args       program arguments (may be {@code null} or empty)
   * @return a {@link RunResult} describing the outcome
   * @throws JavaException if execution cannot be performed
   */
  public static RunResult run(String className, List<Path> classpath,
      List<String> args) {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(classpath, "classpath");

    Path jdkHome = JdkDiscovery.preferredJdk();
    if (jdkHome == null) {
      throw new JavaException("No JDK found. Install a JDK or set JAVA_HOME.");
    }

    Path javaExe = jdkHome.resolve("bin")
        .resolve("java" + exeSuffix());

    if (!java.nio.file.Files.isRegularFile(javaExe)) {
      throw new JavaException("Java launcher not found: " + javaExe);
    }

    List<String> command = new ArrayList<>();
    command.add(javaExe.toString());
    command.add("-cp");
    command.add(joinClasspath(classpath));
    command.add(className);
    if (args != null) {
      command.addAll(args);
    }

    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);
      Process process = pb.start();

      String output = readStream(process.getInputStream());
      String errorOutput = readStream(process.getErrorStream());

      int exitCode = process.waitFor();
      return new RunResult(exitCode, output, errorOutput);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JavaException("Execution failed: " + e.getMessage(), e);
    }
  }

  /**
   * Reads all text from an input stream.
   */
  private static String readStream(java.io.InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Joins classpath entries using the platform path separator.
   */
  private static String joinClasspath(List<Path> classpath) {
    StringBuilder sb = new StringBuilder();
    String sep = java.io.File.pathSeparator;
    for (int i = 0; i < classpath.size(); i++) {
      if (i > 0) {
        sb.append(sep);
      }
      sb.append(classpath.get(i).toString());
    }
    return sb.toString();
  }

  private static String exeSuffix() {
    return java.nio.file.FileSystems.getDefault().getSeparator().equals("\\")
        ? ".exe" : "";
  }
}
