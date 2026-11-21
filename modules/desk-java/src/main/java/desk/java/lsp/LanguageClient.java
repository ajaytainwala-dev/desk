package desk.java.lsp;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Editor-facing language client. Implementations talk to a language server
 * process (for example Eclipse JDT LS) without coupling the editor UI to a
 * specific server.
 */
public interface LanguageClient {

  void start(Path workspaceRoot) throws Exception;

  void stop();

  boolean isRunning();

  CompletableFuture<List<LspDiagnostic>> diagnose(Path file, String content);

  CompletableFuture<List<CompletionItem>> complete(Path file, String content, int line, int character);

  CompletableFuture<List<Location>> definition(Path file, String content, int line, int character);

  CompletableFuture<List<Location>> references(Path file, String content, int line, int character);

  CompletableFuture<List<CodeAction>> codeActions(Path file, String content, int line, int character);
}
