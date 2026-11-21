package desk.java;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles Java source. Prefers the project/workspace JDK's {@code javac}
 * executable when provided; falls back to in-process {@code ToolProvider}
 * only when no external JDK is available.
 */
public final class JavaSourceCompiler {

  private JavaSourceCompiler() {
  }

  public static CompileResult compile(Path sourceFile, Path outputDir, List<Path> classpath) {
    return compile(sourceFile, outputDir, classpath, null);
  }

  /**
   * @param projectJdkHome workspace/project JDK home, or {@code null} to use
   *                       {@link JdkSelector}/{@link JdkDiscovery} preference
   */
  public static CompileResult compile(Path sourceFile, Path outputDir,
      List<Path> classpath, Path projectJdkHome) {
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(outputDir, "outputDir");

    if (!Files.isRegularFile(sourceFile)) {
      throw new JavaException("Source file does not exist: " + sourceFile);
    }

    Path jdkHome = projectJdkHome;
    if (jdkHome == null) {
      jdkHome = JdkSelector.current(null)
          .map(JdkInstallation::home)
          .orElseGet(JdkDiscovery::preferredJdk);
    }
    if (jdkHome == null) {
      throw new JavaException("No JDK found. Install a JDK or set JAVA_HOME.");
    }

    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new JavaException("Cannot create output directory " + outputDir, e);
    }

    Path javac = jdkHome.resolve("bin").resolve("javac" + exeSuffix());
    if (Files.isRegularFile(javac)) {
      return compileWithExternalJavac(javac, jdkHome, sourceFile, outputDir, classpath);
    }

    // Fallback: Desk runtime ToolProvider (may differ from preferred JDK).
    return compileWithToolProvider(sourceFile, outputDir, classpath);
  }

  private static CompileResult compileWithExternalJavac(
      Path javac, Path jdkHome, Path sourceFile, Path outputDir, List<Path> classpath) {
    List<String> command = new ArrayList<>();
    command.add(javac.toString());
    command.add("-d");
    command.add(outputDir.toString());
    command.add("-encoding");
    command.add(StandardCharsets.UTF_8.name());
    if (classpath != null && !classpath.isEmpty()) {
      command.add("-cp");
      command.add(joinClasspath(classpath));
    }
    command.add(sourceFile.toString());

    Map<String, String> env = new LinkedHashMap<>();
    env.put("JAVA_HOME", jdkHome.toAbsolutePath().toString());

    ProcessIo.Result proc = ProcessIo.run(command, sourceFile.getParent(), env, 5, TimeUnit.MINUTES);
    String diagnostics = proc.stderr().isBlank() ? proc.stdout() : proc.stderr();
    boolean success = proc.exitCode() == 0 && !proc.timedOut();
    return new CompileResult(success, proc.stdout(), diagnostics, outputDir);
  }

  private static CompileResult compileWithToolProvider(
      Path sourceFile, Path outputDir, List<Path> classpath) {
    javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new JavaException("No system JavaCompiler available. "
          + "Ensure a JDK (not just a JRE) is installed.");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options = new ArrayList<>();
    options.add("-d");
    options.add(outputDir.toString());
    options.add("-encoding");
    options.add(StandardCharsets.UTF_8.name());
    if (classpath != null && !classpath.isEmpty()) {
      options.add("-cp");
      options.add(joinClasspath(classpath));
    }

    try (StandardJavaFileManager fileManager =
             compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(sourceFile.toFile());
      javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
          new PrintWriter(new StringWriter()),
          fileManager,
          diagnostics,
          options,
          null,
          compilationUnits);
      Boolean result = task.call();
      StringWriter errorWriter = new StringWriter();
      for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
        errorWriter.append(d.getKind().toString()).append(": ")
            .append(d.getMessage(Locale.ROOT))
            .append(" (line ").append(String.valueOf(d.getLineNumber()))
            .append(")\n");
      }
      boolean success = result != null && result;
      return new CompileResult(success, "", errorWriter.toString(), outputDir);
    } catch (IOException e) {
      throw new JavaException("Compilation I/O error: " + e.getMessage(), e);
    }
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
