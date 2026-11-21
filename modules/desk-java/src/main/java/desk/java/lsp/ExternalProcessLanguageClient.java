package desk.java.lsp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Placeholder adapter that launches an external language server binary
 * (for example a JDT LS launcher). Full JSON-RPC LSP framing is intentionally
 * staged; this class establishes process lifecycle isolation so the editor is
 * never tightly coupled to one server.
 */
public final class ExternalProcessLanguageClient implements LanguageClient {

  private final Path serverCommand;
  private Process process;
  private Path workspaceRoot;

  public ExternalProcessLanguageClient(Path serverCommand) {
    this.serverCommand = serverCommand;
  }

  @Override
  public void start(Path workspaceRoot) throws Exception {
    this.workspaceRoot = workspaceRoot;
    ProcessBuilder pb = new ProcessBuilder(serverCommand.toString(), workspaceRoot.toString());
    pb.redirectErrorStream(true);
    process = pb.start();
    // Drain startup output on a daemon thread so a noisy server cannot block.
    Thread t = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        while (reader.readLine() != null) {
          // intentionally discard for now
        }
      } catch (Exception ignored) {
        // ignore
      }
    }, "desk-ls-stdout");
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void stop() {
    if (process != null) {
      process.destroy();
      try {
        process.destroyForcibly();
      } catch (Exception ignored) {
        // ignore
      }
      process = null;
    }
  }

  @Override
  public boolean isRunning() {
    return process != null && process.isAlive();
  }

  @Override
  public CompletableFuture<List<LspDiagnostic>> diagnose(Path file, String content) {
    // Until JSON-RPC is wired, fall back to javac client for real diagnostics.
    JavacLanguageClient fallback = new JavacLanguageClient();
    fallback.start(workspaceRoot != null ? workspaceRoot : file.getParent());
    return fallback.diagnose(file, content);
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
