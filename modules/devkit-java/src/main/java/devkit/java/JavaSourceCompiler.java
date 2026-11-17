package devkit.java;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles Java source code using the platform's {@code javax.tools.JavaCompiler}.
 *
 * <p>Uses the discovered JDK's {@code javac} compiler. If no JDK is found,
 * compilation fails with a clear error message.</p>
 */
public final class JavaSourceCompiler {

  private JavaSourceCompiler() {
    // utility class
  }

  /**
   * Compiles a Java source file to class files in the given output directory.
   *
   * @param sourceFile the Java source file to compile (must not be {@code null})
   * @param outputDir  the directory to write class files to (must not be {@code null})
   * @param classpath  additional classpath entries (may be {@code null} or empty)
   * @return a {@link CompileResult} describing the outcome
   * @throws JavaException if compilation cannot be performed
   */
  public static CompileResult compile(Path sourceFile, Path outputDir,
      List<Path> classpath) {
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(outputDir, "outputDir");

    if (!Files.isRegularFile(sourceFile)) {
      throw new JavaException("Source file does not exist: " + sourceFile);
    }

    Path jdkHome = JdkDiscovery.preferredJdk();
    if (jdkHome == null) {
      throw new JavaException("No JDK found. Install a JDK or set JAVA_HOME.");
    }

    javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new JavaException("No system JavaCompiler available. "
          + "Ensure a JDK (not just a JRE) is installed.");
    }

    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new JavaException("Cannot create output directory " + outputDir, e);
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
             compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                 StandardCharsets.UTF_8)) {

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
}
