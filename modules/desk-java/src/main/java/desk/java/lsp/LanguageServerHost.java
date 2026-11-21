package desk.java.lsp;

import desk.java.JavaException;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chooses and manages a language client. Prefers an external JDT LS when
 * discoverable ({@code DESK_JAVA_LS} or PATH); otherwise falls back to
 * javac diagnostics (diagnostics-only — completion/navigation throw).
 */
public final class LanguageServerHost {

  private final AtomicReference<LanguageClient> client = new AtomicReference<>();
  private volatile Path workspaceRoot;

  public synchronized LanguageClient ensureStarted(Path workspaceRoot) throws Exception {
    Objects.requireNonNull(workspaceRoot, "workspaceRoot");
    this.workspaceRoot = workspaceRoot;
    LanguageClient existing = client.get();
    if (existing != null && existing.isRunning()) {
      return existing;
    }
    Optional<Path> server = LanguageServerLauncher.discover();
    String forced = System.getenv("DESK_JAVA_LS");
    if (forced != null && !forced.isBlank() && server.isEmpty()) {
      throw new JavaException("DESK_JAVA_LS set but not executable: " + forced);
    }
    if (server.isPresent()) {
      var launched = LanguageServerLauncher.start(workspaceRoot, server.get());
      LanguageClient lsp = new LspLanguageClient(launched);
      lsp.start(workspaceRoot);
      client.set(lsp);
      return lsp;
    }
    JavacLanguageClient fallback = new JavacLanguageClient();
    fallback.start(workspaceRoot);
    client.set(fallback);
    return fallback;
  }

  public synchronized Optional<LanguageClient> current() {
    return Optional.ofNullable(client.get());
  }

  public Optional<Path> workspaceRoot() {
    return Optional.ofNullable(workspaceRoot);
  }

  public synchronized void shutdown() {
    LanguageClient c = client.getAndSet(null);
    if (c != null) {
      c.stop();
    }
  }

  public boolean usingExternalServer() {
    return client.get() instanceof LspLanguageClient;
  }
}
