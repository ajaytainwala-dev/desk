package desk.java.lsp;

import desk.java.CompileResult;
import desk.java.JavaException;
import desk.java.JavaSourceCompiler;
import desk.java.JdkSelector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnostics-only fallback using {@code javac}. Completion/navigation are
 * explicitly unsupported — callers must not treat empty lists as success.
 */
public final class JavacLanguageClient implements LanguageClient {

  private static final Pattern DIAG = Pattern.compile(
      "(ERROR|WARNING|MANDATORY_WARNING|NOTE|OTHER):\\s*(.*)\\s*\\(line\\s+(\\d+)\\)");
  private static final Pattern JAVAC = Pattern.compile(
      "^(.+\\.java):(\\d+):\\s*(error|warning):\\s*(.*)$", Pattern.MULTILINE);

  private Path workspaceRoot;
  private volatile boolean running;

  @Override
  public void start(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
    this.running = true;
  }

  @Override
  public void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public CompletableFuture<List<LspDiagnostic>> diagnose(Path file, String content) {
    return CompletableFuture.supplyAsync(() -> {
      if (!running) {
        return List.of();
      }
      Path tmpDir = null;
      try {
        tmpDir = Files.createTempDirectory("desk-lsp-");
        Path src = tmpDir.resolve(file.getFileName() != null ? file.getFileName().toString() : "Snippet.java");
        Files.writeString(src, content, StandardCharsets.UTF_8);
        Path out = tmpDir.resolve("out");
        Files.createDirectories(out);
        Path jdk = workspaceRoot != null
            ? JdkSelector.current(workspaceRoot).map(j -> j.home()).orElse(null)
            : null;
        CompileResult result = JavaSourceCompiler.compile(src, out, List.of(), jdk);
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        String diagText = result.errorOutput() != null ? result.errorOutput() : "";
        if (!diagText.isBlank()) {
          for (String line : diagText.split("\\R")) {
            Matcher m = DIAG.matcher(line.trim());
            if (m.find()) {
              diagnostics.add(new LspDiagnostic(
                  file, Integer.parseInt(m.group(3)), 1,
                  Integer.parseInt(m.group(3)), 1, m.group(1), m.group(2).trim(), "javac"));
              continue;
            }
            Matcher j = JAVAC.matcher(line.trim());
            if (j.find()) {
              String sev = "warning".equalsIgnoreCase(j.group(3)) ? "WARNING" : "ERROR";
              diagnostics.add(new LspDiagnostic(
                  file, Integer.parseInt(j.group(2)), 1,
                  Integer.parseInt(j.group(2)), 1, sev, j.group(4), "javac"));
            }
          }
        }
        return diagnostics;
      } catch (Exception e) {
        return List.of(new LspDiagnostic(file, 1, 1, 1, 1, "ERROR", e.getMessage(), "javac"));
      } finally {
        if (tmpDir != null) {
          deleteRecursive(tmpDir);
        }
      }
    });
  }

  @Override
  public CompletableFuture<List<CompletionItem>> complete(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported("completion"));
  }

  @Override
  public CompletableFuture<List<Location>> definition(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported("definition"));
  }

  @Override
  public CompletableFuture<List<Location>> references(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported("references"));
  }

  @Override
  public CompletableFuture<List<CodeAction>> codeActions(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported("codeAction"));
  }

  private static JavaException unsupported(String feature) {
    return new JavaException(
        "javac fallback does not support " + feature
            + ". Install Eclipse JDT LS and set DESK_JAVA_LS, or ensure 'jdtls' is on PATH.");
  }

  private static void deleteRecursive(Path path) {
    try {
      if (Files.isDirectory(path)) {
        try (var stream = Files.list(path)) {
          for (Path child : stream.toList()) {
            deleteRecursive(child);
          }
        }
      }
      Files.deleteIfExists(path);
    } catch (Exception ignored) {
      // best effort
    }
  }
}
