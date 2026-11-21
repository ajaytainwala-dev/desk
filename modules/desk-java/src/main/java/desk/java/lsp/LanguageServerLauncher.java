package desk.java.lsp;

import desk.java.JavaException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Discovers and launches an external Java language server (typically Eclipse
 * JDT LS). Paths are never hardcoded to a single machine — discovery uses
 * {@code DESK_JAVA_LS}, config, and common install layouts.
 */
public final class LanguageServerLauncher {

  private LanguageServerLauncher() {
  }

  public static Optional<Path> discover() {
    String env = System.getenv("DESK_JAVA_LS");
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      if (Files.isRegularFile(p) || Files.isExecutable(p)) {
        return Optional.of(p.toAbsolutePath().normalize());
      }
    }
    String home = System.getProperty("user.home");
    List<Path> candidates = new ArrayList<>();
    if (home != null) {
      candidates.add(Path.of(home, ".desk", "jdt-language-server", "bin", "jdtls"));
      candidates.add(Path.of(home, ".local", "share", "jdtls", "bin", "jdtls"));
      candidates.add(Path.of(home, "jdt-language-server", "bin", "jdtls"));
    }
    candidates.add(Path.of("/usr/bin/jdtls"));
    candidates.add(Path.of("/usr/local/bin/jdtls"));
    if (isWindows()) {
      if (home != null) {
        candidates.add(Path.of(home, "AppData", "Local", "jdtls", "bin", "jdtls.bat"));
      }
      candidates.add(Path.of("C:\\Program Files\\jdtls\\bin\\jdtls.bat"));
    }
    for (Path c : candidates) {
      if (Files.isRegularFile(c)) {
        return Optional.of(c.toAbsolutePath().normalize());
      }
    }
    // PATH lookup
    String path = System.getenv("PATH");
    if (path != null) {
      for (String entry : path.split(isWindows() ? ";" : ":")) {
        if (entry == null || entry.isBlank()) {
          continue;
        }
        Path jdtls = Path.of(entry.trim()).resolve(isWindows() ? "jdtls.bat" : "jdtls");
        if (Files.isRegularFile(jdtls)) {
          return Optional.of(jdtls.toAbsolutePath().normalize());
        }
      }
    }
    return Optional.empty();
  }

  public static LaunchedServer start(Path workspaceRoot, Path serverCommand) {
    ObjectsRequire(workspaceRoot, serverCommand);
    try {
      List<String> cmd = new ArrayList<>();
      cmd.add(serverCommand.toString());
      // jdtls accepts workspace as argument in many distributions
      cmd.add("-data");
      Path data = workspaceRoot.resolve(".desk").resolve("jdtls-workspace");
      Files.createDirectories(data);
      cmd.add(data.toString());

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(workspaceRoot.toFile());
      pb.redirectError(ProcessBuilder.Redirect.PIPE);
      Process process = pb.start();

      // Drain stderr so it cannot fill and block; log lines are discarded to avoid
      // mixing with stdout protocol. Callers can set DESK_LSP_LOG=1 later.
      Thread err = new Thread(() -> {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if ("1".equals(System.getenv("DESK_LSP_LOG"))) {
              System.err.println("[jdtls] " + line);
            }
          }
        } catch (Exception ignored) {
          // ignore
        }
      }, "desk-lsp-stderr");
      err.setDaemon(true);
      err.start();

      ContentLengthTransport transport =
          new ContentLengthTransport(process.getInputStream(), process.getOutputStream());
      JsonRpcConnection connection = new JsonRpcConnection(transport);
      LspSession session = new LspSession(connection, workspaceRoot);
      session.initialize();
      return new LaunchedServer(process, connection, session, serverCommand);
    } catch (Exception e) {
      throw new JavaException("Failed to start language server: " + e.getMessage(), e);
    }
  }

  private static void ObjectsRequire(Path workspaceRoot, Path serverCommand) {
    if (workspaceRoot == null) {
      throw new JavaException("workspaceRoot required");
    }
    if (serverCommand == null) {
      throw new JavaException("serverCommand required");
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  public record LaunchedServer(
      Process process,
      JsonRpcConnection connection,
      LspSession session,
      Path command) implements AutoCloseable {
    @Override
    public void close() {
      try {
        session.close();
      } catch (Exception ignored) {
        // ignore
      }
      if (process != null && process.isAlive()) {
        process.destroy();
        try {
          process.destroyForcibly();
        } catch (Exception ignored) {
          // ignore
        }
      }
    }

    public boolean alive() {
      return process != null && process.isAlive() && session.isInitialized();
    }
  }
}
