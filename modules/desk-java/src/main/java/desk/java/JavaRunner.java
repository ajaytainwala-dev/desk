package desk.java;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Runs compiled Java programs using a JDK {@code java} launcher.
 * Prefer the workspace/project JDK when provided.
 */
public final class JavaRunner {

  private JavaRunner() {
  }

  public static RunResult run(String className, List<Path> classpath, List<String> args) {
    return run(className, classpath, args, null);
  }

  public static RunResult run(String className, List<Path> classpath, List<String> args,
      Path projectJdkHome) {
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(classpath, "classpath");

    Path jdkHome = projectJdkHome;
    if (jdkHome == null) {
      jdkHome = JdkSelector.current(null)
          .map(JdkInstallation::home)
          .orElseGet(JdkDiscovery::preferredJdk);
    }
    if (jdkHome == null) {
      throw new JavaException("No JDK found. Install a JDK or set JAVA_HOME.");
    }

    Path javaExe = jdkHome.resolve("bin").resolve("java" + exeSuffix());
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

    Map<String, String> env = new LinkedHashMap<>();
    env.put("JAVA_HOME", jdkHome.toAbsolutePath().toString());

    ProcessIo.Result proc = ProcessIo.run(command, null, env, 5, TimeUnit.MINUTES);
    return new RunResult(proc.exitCode(), proc.stdout(), proc.stderr());
  }

  private static String joinClasspath(List<Path> classpath) {
    StringBuilder sb = new StringBuilder();
    String sep = java.io.File.pathSeparator;
    for (int i = 0; i < classpath.size(); i++) {
      if (i > 0) {
        sb.append(sep);
      }
      sb.append(classpath.get(i));
    }
    return sb.toString();
  }

  private static String exeSuffix() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
        ? ".exe" : "";
  }
}
