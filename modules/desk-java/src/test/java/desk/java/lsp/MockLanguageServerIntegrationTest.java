package desk.java.lsp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic JSON-RPC LSP integration against an in-process mock server.
 * Does not download JDT LS — live jdtls tests are gated separately
 * ({@code DESK_JDTLS_IT=1} / {@code DESK_JAVA_LS}).
 */
class MockLanguageServerIntegrationTest {

  @Test
  void initializeCompletionHoverAndShutdown() throws Exception {
    PipedInputStream clientIn = new PipedInputStream(256 * 1024);
    PipedOutputStream serverOut = new PipedOutputStream(clientIn);
    PipedInputStream serverIn = new PipedInputStream(256 * 1024);
    PipedOutputStream clientOut = new PipedOutputStream(serverIn);

    AtomicReference<Throwable> serverError = new AtomicReference<>();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> server = pool.submit(() -> {
      try (ContentLengthTransport t = new ContentLengthTransport(serverIn, serverOut)) {
        Map<String, Object> init = t.receive();
        assertEquals("initialize", init.get("method"));
        reply(t, init.get("id"), Map.of(
            "capabilities", Map.of(
                "textDocumentSync", 2,
                "completionProvider", Map.of("resolveProvider", false),
                "hoverProvider", true,
                "renameProvider", true,
                "codeActionProvider", true,
                "documentFormattingProvider", true,
                "signatureHelpProvider", Map.of("triggerCharacters", List.of("(", ","))
            )));
        Map<String, Object> initialized = t.receive();
        assertEquals("initialized", initialized.get("method"));

        Map<String, Object> open = t.receive();
        assertEquals("textDocument/didOpen", open.get("method"));

        Map<String, Object> complete = t.receive();
        assertEquals("textDocument/completion", complete.get("method"));
        reply(t, complete.get("id"), Map.of(
            "isIncomplete", false,
            "items", List.of(Map.of("label", "println", "kind", 2))));

        Map<String, Object> hover = t.receive();
        assertEquals("textDocument/hover", hover.get("method"));
        reply(t, hover.get("id"), Map.of(
            "contents", Map.of("kind", "markdown", "value", "mock hover")));

        Map<String, Object> rename = t.receive();
        assertEquals("textDocument/rename", rename.get("method"));
        reply(t, rename.get("id"), Map.of(
            "changes", Map.of(
                "file:///tmp/A.java", List.of(Map.of(
                    "range", Map.of(
                        "start", Map.of("line", 0, "character", 0),
                        "end", Map.of("line", 0, "character", 1)),
                    "newText", "X")))));

        Map<String, Object> format = t.receive();
        assertEquals("textDocument/formatting", format.get("method"));
        reply(t, format.get("id"), List.of());

        Map<String, Object> shutdown = t.receive();
        assertEquals("shutdown", shutdown.get("method"));
        reply(t, shutdown.get("id"), null);
        Map<String, Object> exit = t.receive();
        assertEquals("exit", exit.get("method"));
      } catch (Throwable e) {
        serverError.set(e);
      }
    });

    try (ContentLengthTransport client = new ContentLengthTransport(clientIn, clientOut)) {
      Map<String, Object> initReq = new LinkedHashMap<>();
      initReq.put("jsonrpc", "2.0");
      initReq.put("id", 1);
      initReq.put("method", "initialize");
      initReq.put("params", Map.of("processId", ProcessHandle.current().pid(),
          "rootUri", "file:///tmp/ws",
          "capabilities", Map.of()));
      client.send(initReq);
      Map<String, Object> initRes = client.receive();
      assertNotNull(initRes.get("result"));

      client.send(Map.of("jsonrpc", "2.0", "method", "initialized", "params", Map.of()));
      client.send(Map.of(
          "jsonrpc", "2.0",
          "method", "textDocument/didOpen",
          "params", Map.of(
              "textDocument", Map.of(
                  "uri", "file:///tmp/A.java",
                  "languageId", "java",
                  "version", 1,
                  "text", "class A {}"))));

      client.send(Map.of(
          "jsonrpc", "2.0",
          "id", 2,
          "method", "textDocument/completion",
          "params", Map.of(
              "textDocument", Map.of("uri", "file:///tmp/A.java"),
              "position", Map.of("line", 0, "character", 1))));
      Map<String, Object> comp = client.receive();
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) comp.get("result");
      assertNotNull(result.get("items"));

      client.send(Map.of(
          "jsonrpc", "2.0",
          "id", 3,
          "method", "textDocument/hover",
          "params", Map.of(
              "textDocument", Map.of("uri", "file:///tmp/A.java"),
              "position", Map.of("line", 0, "character", 1))));
      assertNotNull(client.receive().get("result"));

      client.send(Map.of(
          "jsonrpc", "2.0",
          "id", 4,
          "method", "textDocument/rename",
          "params", Map.of(
              "textDocument", Map.of("uri", "file:///tmp/A.java"),
              "position", Map.of("line", 0, "character", 6),
              "newName", "B")));
      assertNotNull(client.receive().get("result"));

      client.send(Map.of(
          "jsonrpc", "2.0",
          "id", 5,
          "method", "textDocument/formatting",
          "params", Map.of(
              "textDocument", Map.of("uri", "file:///tmp/A.java"),
              "options", Map.of("tabSize", 4, "insertSpaces", true))));
      assertNotNull(client.receive().get("result"));

      Map<String, Object> shutdownReq = new LinkedHashMap<>();
      shutdownReq.put("jsonrpc", "2.0");
      shutdownReq.put("id", 6);
      shutdownReq.put("method", "shutdown");
      shutdownReq.put("params", Map.of());
      client.send(shutdownReq);
      Map<String, Object> shutdownRes = client.receive();
      assertEquals(6, ((Number) shutdownRes.get("id")).intValue());

      Map<String, Object> exitReq = new LinkedHashMap<>();
      exitReq.put("jsonrpc", "2.0");
      exitReq.put("method", "exit");
      exitReq.put("params", Map.of());
      client.send(exitReq);
    }

    server.get(10, TimeUnit.SECONDS);
    if (serverError.get() != null) {
      fail(serverError.get());
    }
    pool.shutdownNow();
  }

  @Test
  void rejectsMissingContentLength() {
    String bad = "Not-A-Header: 1\r\n\r\n{}";
    ContentLengthTransport t = new ContentLengthTransport(
        new java.io.ByteArrayInputStream(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        new java.io.ByteArrayOutputStream());
    assertThrows(IOException.class, t::receive);
  }

  private static void reply(ContentLengthTransport t, Object id, Object result) throws IOException {
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("jsonrpc", "2.0");
    msg.put("id", id);
    msg.put("result", result);
    t.send(msg);
  }
}
