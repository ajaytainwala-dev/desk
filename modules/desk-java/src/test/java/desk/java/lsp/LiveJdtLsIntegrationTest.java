package desk.java.lsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live Eclipse JDT LS integration.
 *
 * <ul>
 *   <li>{@code DESK_JDTLS_CI=1} — required: provision pinned jdtls if needed; never skip.</li>
 *   <li>{@code DESK_JDTLS_IT=1} + {@code DESK_JAVA_LS} — optional local run.</li>
 * </ul>
 */
class LiveJdtLsIntegrationTest {

  static boolean shouldRun() {
    return "1".equals(System.getenv("DESK_JDTLS_CI"))
        || ("1".equals(System.getenv("DESK_JDTLS_IT")) && System.getenv("DESK_JAVA_LS") != null);
  }

  @Test
  @EnabledIf("shouldRun")
  void liveProtocolAgainstRealJdtls() throws Exception {
    Path launcher = resolveLauncher();
    Path ws = Files.createTempDirectory("desk-jdtls-ws");
    Path srcDir = ws.resolve("src");
    Files.createDirectories(srcDir);
    Path src = srcDir.resolve("Main.java");
    Files.writeString(src, """
        public class Main {
          public static void main(String[] args) {
            System.out.println("hi");
          }
        }
        """);
    Path data = ws.resolve(".desk").resolve("jdtls-data");
    Files.createDirectories(data);

    ProcessBuilder pb = new ProcessBuilder(
        launcher.toString(),
        "-data", data.toAbsolutePath().toString());
    pb.directory(ws.toFile());
    pb.redirectError(ProcessBuilder.Redirect.PIPE);
    Process proc = pb.start();
    AtomicReference<String> err = new AtomicReference<>("");
    Thread errDrain = new Thread(() -> {
      try {
        err.set(new String(proc.getErrorStream().readAllBytes()));
      } catch (Exception ignored) {
        // ignore
      }
    }, "jdtls-err");
    errDrain.setDaemon(true);
    errDrain.start();

    try (ContentLengthTransport t = new ContentLengthTransport(
        proc.getInputStream(), proc.getOutputStream())) {
      Map<String, Object> initParams = new LinkedHashMap<>();
      initParams.put("processId", ProcessHandle.current().pid());
      initParams.put("rootUri", ws.toUri().toString());
      initParams.put("capabilities", Map.of(
          "textDocument", Map.of(
              "synchronization", Map.of("didSave", true),
              "completion", Map.of("completionItem", Map.of("snippetSupport", false)),
              "hover", Map.of("contentFormat", List.of("markdown", "plaintext")),
              "definition", Map.of(),
              "references", Map.of(),
              "documentSymbol", Map.of(),
              "rename", Map.of(),
              "formatting", Map.of(),
              "codeAction", Map.of(),
              "signatureHelp", Map.of("triggerCharacters", List.of("(", ","))),
          "workspace", Map.of("workspaceEdit", Map.of("documentChanges", true))));

      t.send(rpc(1, "initialize", initParams));
      Map<String, Object> init = receiveIgnoringNotifications(t, 120_000);
      assertNotNull(init, "jdtls closed during initialize; stderr=" + err.get());
      assertNull(init.get("error"), () -> "initialize error: " + init.get("error")
          + " stderr=" + err.get());
      assertNotNull(init.get("result"), "initialize missing result; stderr=" + err.get());

      t.send(notify("initialized", Map.of()));

      String uri = src.toUri().toString();
      t.send(notify("textDocument/didOpen", Map.of(
          "textDocument", Map.of(
              "uri", uri,
              "languageId", "java",
              "version", 1,
              "text", Files.readString(src)))));

      // Allow indexer a moment; drain publishDiagnostics if any
      drainFor(t, 3_000);

      t.send(rpc(2, "textDocument/completion", Map.of(
          "textDocument", Map.of("uri", uri),
          "position", Map.of("line", 2, "character", 18))));
      Map<String, Object> completion = receiveIgnoringNotifications(t, 60_000);
      assertNotNull(completion.get("result"),
          () -> "completion empty; resp=" + completion + " stderr=" + err.get());

      t.send(rpc(3, "textDocument/hover", Map.of(
          "textDocument", Map.of("uri", uri),
          "position", Map.of("line", 1, "character", 20))));
      Map<String, Object> hover = receiveIgnoringNotifications(t, 60_000);
      assertTrue(hover.containsKey("result") || hover.containsKey("error"),
          () -> "hover missing; " + hover);

      t.send(rpc(4, "textDocument/documentSymbol", Map.of(
          "textDocument", Map.of("uri", uri))));
      Map<String, Object> symbols = receiveIgnoringNotifications(t, 60_000);
      assertNotNull(symbols.get("result"), () -> "symbols: " + symbols);

      t.send(rpc(5, "textDocument/formatting", Map.of(
          "textDocument", Map.of("uri", uri),
          "options", Map.of("tabSize", 2, "insertSpaces", true))));
      Map<String, Object> formatting = receiveIgnoringNotifications(t, 60_000);
      assertTrue(formatting.containsKey("result"), () -> "format: " + formatting);

      t.send(rpc(6, "shutdown", Map.of()));
      Map<String, Object> shutdown = receiveIgnoringNotifications(t, 30_000);
      assertNotNull(shutdown);
      t.send(notify("exit", Map.of()));
    } finally {
      proc.destroy();
      if (!proc.waitFor(15, TimeUnit.SECONDS)) {
        proc.destroyForcibly();
      }
    }
  }

  private static Path resolveLauncher() throws Exception {
    String env = System.getenv("DESK_JAVA_LS");
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      assertTrue(Files.isRegularFile(p) || Files.isExecutable(p), "DESK_JAVA_LS invalid: " + env);
      return p;
    }
    if ("1".equals(System.getenv("DESK_JDTLS_CI"))) {
      Path install = Path.of(System.getProperty("java.io.tmpdir"), "desk-ci-jdtls",
          JdtLsProvisioner.VERSION);
      return JdtLsProvisioner.ensureInstalled(install);
    }
    fail("No DESK_JAVA_LS and DESK_JDTLS_CI not set — test should have been disabled");
    return null;
  }

  private static Map<String, Object> rpc(int id, String method, Object params) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("jsonrpc", "2.0");
    m.put("id", id);
    m.put("method", method);
    m.put("params", params);
    return m;
  }

  private static Map<String, Object> notify(String method, Object params) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("jsonrpc", "2.0");
    m.put("method", method);
    m.put("params", params);
    return m;
  }

  private static Map<String, Object> receiveIgnoringNotifications(
      ContentLengthTransport t, long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      // ContentLengthTransport.receive blocks — rely on server responsiveness.
      Map<String, Object> msg = t.receive();
      if (msg == null) {
        return null;
      }
      if (msg.containsKey("id") && (msg.containsKey("result") || msg.containsKey("error"))) {
        return msg;
      }
      // notification / request from server — ignore for this IT
    }
    fail("Timed out waiting for LSP response");
    return null;
  }

  private static void drainFor(ContentLengthTransport t, long ms) {
    long deadline = System.currentTimeMillis() + ms;
    Thread drain = new Thread(() -> {
      try {
        while (System.currentTimeMillis() < deadline) {
          // non-ideal: receive blocks; interrupt via close not available
          break;
        }
      } catch (Exception ignored) {
        // ignore
      }
    });
    drain.setDaemon(true);
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
