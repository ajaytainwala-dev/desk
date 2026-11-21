package desk.java.lsp;

import desk.java.JavaException;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @deprecated Use {@link LspLanguageClient} via {@link LanguageServerHost}.
 * This stub remains only to avoid breaking reflective references; all
 * intelligence methods fail explicitly (never return empty success).
 */
@Deprecated
public final class ExternalProcessLanguageClient implements LanguageClient {

  @Override
  public void start(Path workspaceRoot) {
    throw new JavaException(
        "ExternalProcessLanguageClient is deprecated — use LanguageServerHost / LspLanguageClient");
  }

  @Override
  public void stop() {
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public CompletableFuture<List<LspDiagnostic>> diagnose(Path file, String content) {
    return CompletableFuture.failedFuture(unsupported());
  }

  @Override
  public CompletableFuture<List<CompletionItem>> complete(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported());
  }

  @Override
  public CompletableFuture<List<Location>> definition(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported());
  }

  @Override
  public CompletableFuture<List<Location>> references(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported());
  }

  @Override
  public CompletableFuture<List<CodeAction>> codeActions(Path file, String content, int line, int character) {
    return CompletableFuture.failedFuture(unsupported());
  }

  private static JavaException unsupported() {
    return new JavaException(
        "ExternalProcessLanguageClient is deprecated. Use desk lsp with DESK_JAVA_LS / jdtls.");
  }
}
