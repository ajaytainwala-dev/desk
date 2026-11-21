package desk.java.lsp;

import desk.java.JavaException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link LanguageClient} backed by a live {@link LspSession} (e.g. JDT LS).
 */
public final class LspLanguageClient implements LanguageClient {

  private final LanguageServerLauncher.LaunchedServer server;
  private Path workspaceRoot;

  public LspLanguageClient(LanguageServerLauncher.LaunchedServer server) {
    this.server = server;
  }

  @Override
  public void start(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
    // Already initialized by launcher.
  }

  @Override
  public void stop() {
    server.close();
  }

  @Override
  public boolean isRunning() {
    return server.alive();
  }

  @Override
  public CompletableFuture<List<LspDiagnostic>> diagnose(Path file, String content) {
    return CompletableFuture.supplyAsync(() -> {
      ensureOpen(file, content);
      // Give the server a brief moment to publish diagnostics.
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return server.session().diagnostics().stream()
          .filter(d -> d.file() == null || d.file().equals(file.toAbsolutePath().normalize()))
          .toList();
    });
  }

  @Override
  public CompletableFuture<List<CompletionItem>> complete(Path file, String content, int line, int character) {
    return CompletableFuture.supplyAsync(() -> {
      ensureOpen(file, content);
      // LSP uses 0-based lines
      return server.session().completion(file, Math.max(0, line - 1), Math.max(0, character - 1));
    });
  }

  @Override
  public CompletableFuture<List<Location>> definition(Path file, String content, int line, int character) {
    return CompletableFuture.supplyAsync(() -> {
      ensureOpen(file, content);
      return server.session().definition(file, Math.max(0, line - 1), Math.max(0, character - 1));
    });
  }

  @Override
  public CompletableFuture<List<Location>> references(Path file, String content, int line, int character) {
    return CompletableFuture.supplyAsync(() -> {
      ensureOpen(file, content);
      return server.session().references(file, Math.max(0, line - 1), Math.max(0, character - 1));
    });
  }

  @Override
  public CompletableFuture<List<CodeAction>> codeActions(Path file, String content, int line, int character) {
    return CompletableFuture.supplyAsync(() -> {
      ensureOpen(file, content);
      return server.session().codeActions(file, Math.max(0, line - 1), Math.max(0, character - 1));
    });
  }

  public LspSession session() {
    return server.session();
  }

  private void ensureOpen(Path file, String content) {
    if (!server.alive()) {
      throw new JavaException("Language server is not running");
    }
    try {
      String text = content;
      if (text == null && file != null && Files.isRegularFile(file)) {
        text = Files.readString(file);
      }
      server.session().didOpen(file, "java", text != null ? text : "");
    } catch (JavaException e) {
      throw e;
    } catch (Exception e) {
      throw new JavaException("Unable to sync document: " + e.getMessage(), e);
    }
  }
}
