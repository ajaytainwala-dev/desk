package desk.java.build;

import desk.java.JdkDiscovery;
import desk.java.JdkInstallation;
import desk.java.JdkSelector;
import desk.java.JavaException;
import desk.java.ProcessIo;
import desk.java.project.ProjectDescriptor;
import desk.java.project.ProjectDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates Maven, Gradle, or javac for a workspace. Does not reimplement
 * those build systems — it invokes them with argument arrays.
 */
public final class BuildService {

  private static final Pattern JAVAC_ERROR = Pattern.compile(
      "^(.+\\.java):(\\d+):\\s*(error|warning):\\s*(.*)$");
  private static final Pattern MAVEN_ERROR = Pattern.compile(
      "\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.*)$");

  private BuildService() {
  }

  public static BuildResult execute(BuildRequest request) {
    long start = System.currentTimeMillis();
    Path root = request.workspaceRoot().toAbsolutePath().normalize();
    ProjectDescriptor project = ProjectDetector.detect(root);
    Path jdkHome = request.projectJdkHome();
    if (jdkHome == null) {
      jdkHome = JdkSelector.current(root)
          .map(JdkInstallation::home)
          .orElseGet(JdkDiscovery::preferredJdk);
    }

    return switch (project.buildSystem()) {
      case "maven" -> runMaven(root, request.goal(), jdkHome, start);
      case "gradle" -> runGradle(root, request.goal(), jdkHome, start);
      default -> runJavac(root, request.goal(), jdkHome, start);
    };
  }

  private static BuildResult runMaven(Path root, BuildRequest.Goal goal, Path jdkHome, long start) {
    List<String> cmd = new ArrayList<>();
    Path mvnw = root.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
    if (Files.isRegularFile(mvnw)) {
      cmd.add(mvnw.toAbsolutePath().toString());
    } else {
      cmd.add(isWindows() ? "mvn.cmd" : "mvn");
    }
    cmd.add("-B");
    switch (goal) {
      case CLEAN -> cmd.add("clean");
      case COMPILE -> cmd.add("compile");
      case TEST -> cmd.add("test");
      case PACKAGE -> cmd.add("package");
      case RUN -> {
        cmd.add("package");
        cmd.add("-DskipTests");
      }
      default -> cmd.add("compile");
    }
    return run(cmd, root, jdkHome, BuildSystem.MAVEN, start);
  }

  private static BuildResult runGradle(Path root, BuildRequest.Goal goal, Path jdkHome, long start) {
    List<String> cmd = new ArrayList<>();
    Path gradlew = root.resolve(isWindows() ? "gradlew.bat" : "gradlew");
    if (Files.isRegularFile(gradlew)) {
      cmd.add(gradlew.toAbsolutePath().toString());
    } else {
      cmd.add(isWindows() ? "gradle.bat" : "gradle");
    }
    switch (goal) {
      case CLEAN -> cmd.add("clean");
      case COMPILE -> cmd.add("classes");
      case TEST -> cmd.add("test");
      case PACKAGE -> cmd.add("build");
      case RUN -> cmd.add("run");
      default -> cmd.add("classes");
    }
    return run(cmd, root, jdkHome, BuildSystem.GRADLE, start);
  }

  private static BuildResult runJavac(Path root, BuildRequest.Goal goal, Path jdkHome, long start) {
    if (goal == BuildRequest.Goal.CLEAN) {
      Path out = root.resolve("desk-out");
      deleteRecursiveQuiet(out);
      return new BuildResult(true, 0, BuildSystem.JAVAC, "Cleaned desk-out\n", List.of(),
          System.currentTimeMillis() - start);
    }
    if (jdkHome == null) {
      throw new JavaException("No JDK available for javac build");
    }
    Path javac = jdkHome.resolve("bin").resolve("javac" + (isWindows() ? ".exe" : ""));
    Path out = root.resolve("desk-out");
    try {
      Files.createDirectories(out);
    } catch (Exception e) {
      throw new JavaException("Cannot create desk-out: " + e.getMessage(), e);
    }
    List<Path> sources = findJavaSources(root);
    if (sources.isEmpty()) {
      return new BuildResult(false, 1, BuildSystem.JAVAC, "No .java sources found\n",
          List.of(new BuildDiagnostic(null, 0, 0, BuildDiagnostic.Severity.ERROR,
              "No .java sources found", "javac", null)),
          System.currentTimeMillis() - start);
    }
    if (goal == BuildRequest.Goal.TEST || goal == BuildRequest.Goal.PACKAGE
        || goal == BuildRequest.Goal.RUN) {
      // compile first; run/test are best-effort for plain projects
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(javac.toString());
    cmd.add("-d");
    cmd.add(out.toString());
    cmd.add("-encoding");
    cmd.add("UTF-8");
    for (Path src : sources) {
      cmd.add(src.toString());
    }
    BuildResult compiled = run(cmd, root, jdkHome, BuildSystem.JAVAC, start);
    if (!compiled.success() || goal == BuildRequest.Goal.COMPILE
        || goal == BuildRequest.Goal.PACKAGE) {
      return compiled;
    }
    if (goal == BuildRequest.Goal.RUN) {
      Path java = jdkHome.resolve("bin").resolve("java" + (isWindows() ? ".exe" : ""));
      String main = guessMainClass(sources);
      if (main == null) {
        return new BuildResult(false, 1, BuildSystem.JAVAC,
            compiled.output() + "\nUnable to detect main class\n",
            compiled.diagnostics(), System.currentTimeMillis() - start);
      }
      List<String> runCmd = List.of(java.toString(), "-cp", out.toString(), main);
      return run(runCmd, root, jdkHome, BuildSystem.JAVAC, start);
    }
    return compiled;
  }

  private static BuildResult run(List<String> command, Path cwd, Path jdkHome,
      BuildSystem system, long start) {
    Map<String, String> env = new java.util.LinkedHashMap<>();
    if (jdkHome != null) {
      env.put("JAVA_HOME", jdkHome.toAbsolutePath().toString());
      String path = System.getenv().getOrDefault("PATH", "");
      env.put("PATH", jdkHome.resolve("bin") + (isWindows() ? ";" : ":") + path);
    }
    ProcessIo.Result proc = ProcessIo.run(command, cwd, env, 30, TimeUnit.MINUTES);
    String combined = proc.stdout();
    if (proc.stderr() != null && !proc.stderr().isBlank()) {
      combined = combined + proc.stderr();
    }
    if (proc.timedOut()) {
      combined = combined + "\nerror: build timed out\n";
    }
    List<BuildDiagnostic> diagnostics = parseDiagnostics(combined, cwd, system);
    return new BuildResult(proc.exitCode() == 0 && !proc.timedOut(), proc.exitCode(),
        system, combined, diagnostics, System.currentTimeMillis() - start);
  }

  static List<BuildDiagnostic> parseDiagnostics(String output, Path root, BuildSystem system) {
    List<BuildDiagnostic> list = new ArrayList<>();
    for (String line : output.split("\\R")) {
      Matcher javac = JAVAC_ERROR.matcher(line);
      if (javac.find()) {
        Path file = root.resolve(javac.group(1)).normalize();
        if (!file.startsWith(root) && Path.of(javac.group(1)).isAbsolute()) {
          file = Path.of(javac.group(1));
        }
        BuildDiagnostic.Severity sev = "warning".equalsIgnoreCase(javac.group(3))
            ? BuildDiagnostic.Severity.WARNING
            : BuildDiagnostic.Severity.ERROR;
        list.add(new BuildDiagnostic(file, Integer.parseInt(javac.group(2)), 1, sev,
            javac.group(4), system.name().toLowerCase(Locale.ROOT), null));
        continue;
      }
      Matcher maven = MAVEN_ERROR.matcher(line);
      if (maven.find()) {
        Path file = Path.of(maven.group(1));
        list.add(new BuildDiagnostic(file, Integer.parseInt(maven.group(2)),
            Integer.parseInt(maven.group(3)), BuildDiagnostic.Severity.ERROR,
            maven.group(4), "maven", null));
      }
    }
    return list;
  }

  private static List<Path> findJavaSources(Path root) {
    List<Path> sources = new ArrayList<>();
    Path main = root.resolve("src/main/java");
    Path scan = Files.isDirectory(main) ? main : root;
    try (var stream = Files.walk(scan, Files.isDirectory(main) ? 20 : 4)) {
      stream.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".java"))
          .filter(p -> {
            String s = p.toString().replace('\\', '/');
            return !s.contains("/desk-out/") && !s.contains("/target/") && !s.contains("/.desk/");
          })
          .forEach(sources::add);
    } catch (Exception ignored) {
      // ignore
    }
    return sources;
  }

  private static String guessMainClass(List<Path> sources) {
    for (Path src : sources) {
      try {
        String text = Files.readString(src);
        if (text.contains("public static void main")) {
          String pkg = null;
          for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("package ")) {
              pkg = t.substring(8).replace(";", "").trim();
            }
          }
          String name = src.getFileName().toString().replace(".java", "");
          return pkg != null ? pkg + "." + name : name;
        }
      } catch (Exception ignored) {
        // ignore
      }
    }
    return null;
  }

  private static void deleteRecursiveQuiet(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (var walk = Files.walk(path)) {
      walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (Exception ignored) {
          // ignore
        }
      });
    } catch (Exception ignored) {
      // ignore
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
