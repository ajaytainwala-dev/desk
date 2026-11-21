package desk.java.lsp;

import desk.java.CompileResult;
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
 * Fallback language intelligence using {@code javac} diagnostics when a full
 * language server is unavailable. Not a substitute for JDT LS, but provides
 * real, tested diagnostics without fake placeholders.
 */
public final class JavacLanguageClient implements LanguageClient {

  private static final Pattern DIAG = Pattern.compile(
      "(ERROR|WARNING|MANDATORY_WARNING|NOTE|OTHER):\\s*(.*)\\s*\\(line\\s+(\\d+)\\)");

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
      try {
        Path tmpDir = Files.createTempDirectory("desk-lsp-");
        Path src = tmpDir.resolve(file.getFileName() != null ? file.getFileName().toString() : "Snippet.java");
        Files.writeString(src, content, StandardCharsets.UTF_8);
        Path out = tmpDir.resolve("out");
        Files.createDirectories(out);
        // Prefer workspace JDK when available (environment for external tools);
        // compilation still uses ToolProvider of the Desk JVM as baseline.
        if (workspaceRoot != null) {
          JdkSelector.current(workspaceRoot).ifPresent(j ->
              System.setProperty("desk.java.project.home", j.home().toString()));
        }
        CompileResult result = JavaSourceCompiler.compile(src, out, List.of());
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        String diagText = result.errorOutput() != null ? result.errorOutput() : "";
        if (!result.success() && !diagText.isBlank()) {
          for (String line : diagText.split("\\R")) {
            Matcher m = DIAG.matcher(line.trim());
            if (m.find()) {
              diagnostics.add(new LspDiagnostic(
                  file,
                  Integer.parseInt(m.group(3)),
                  1,
                  Integer.parseInt(m.group(3)),
                  1,
                  m.group(1),
                  m.group(2).trim(),
                  "javac"));
            }
          }
        }
        return diagnostics;
      } catch (Exception e) {
        return List.of(new LspDiagnostic(file, 1, 1, 1, 1, "ERROR", e.getMessage(), "javac"));
      }
    });
  }

  @Override
  public CompletableFuture<List<CompletionItem>> complete(Path file, String content, int line, int character) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<Location>> definition(Path file, String content, int line, int character) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<Location>> references(Path file, String content, int line, int character) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<CodeAction>> codeActions(Path file, String content, int line, int character) {
    return CompletableFuture.completedFuture(List.of());
  }
}
