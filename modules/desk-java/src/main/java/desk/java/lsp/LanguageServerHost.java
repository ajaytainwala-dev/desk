package desk.java.lsp;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses a language client implementation. Prefers an external JDT LS when
 * configured via {@code DESK_JAVA_LS}, otherwise falls back to javac diagnostics.
 */
public final class LanguageServerHost {

  private LanguageClient client;

  public synchronized LanguageClient ensureStarted(Path workspaceRoot) throws Exception {
    Objects.requireNonNull(workspaceRoot, "workspaceRoot");
    if (client != null && client.isRunning()) {
      return client;
    }
    String external = System.getenv("DESK_JAVA_LS");
    if (external != null && !external.isBlank()) {
      client = new ExternalProcessLanguageClient(Path.of(external.trim()));
    } else {
      client = new JavacLanguageClient();
    }
    client.start(workspaceRoot);
    return client;
  }

  public synchronized Optional<LanguageClient> current() {
    return Optional.ofNullable(client);
  }

  public synchronized void shutdown() {
    if (client != null) {
      client.stop();
      client = null;
    }
  }
}
