package desk.java.lsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional live Eclipse JDT LS test. Skipped unless {@code DESK_JDTLS_IT=1}
 * and {@code DESK_JAVA_LS} points at a launchable jdtls command.
 *
 * <p>Environmental failures (missing binary, network) are distinct from
 * protocol bugs — this test fails only when the server is present and
 * misbehaves.</p>
 */
class LiveJdtLsIntegrationTest {

  @Test
  @EnabledIfEnvironmentVariable(named = "DESK_JDTLS_IT", matches = "1")
  void initializeAgainstRealJdtls() throws Exception {
    String cmd = System.getenv("DESK_JAVA_LS");
    assertNotNull(cmd, "DESK_JAVA_LS must be set when DESK_JDTLS_IT=1");
    Path exe = Path.of(cmd);
    assertTrue(Files.isRegularFile(exe) || Files.isExecutable(exe),
        "DESK_JAVA_LS not executable: " + cmd);

    Path ws = Files.createTempDirectory("desk-jdtls-ws");
    Path src = ws.resolve("src").resolve("Main.java");
    Files.createDirectories(src.getParent());
    Files.writeString(src, "public class Main { public static void main(String[] a) {} }\n");

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(ws.toFile());
    pb.redirectError(ProcessBuilder.Redirect.PIPE);
    Process proc = pb.start();
    try (ContentLengthTransport t = new ContentLengthTransport(
        proc.getInputStream(), proc.getOutputStream())) {
      t.send(Map.of(
          "jsonrpc", "2.0",
          "id", 1,
          "method", "initialize",
          "params", Map.of(
              "processId", ProcessHandle.current().pid(),
              "rootUri", ws.toUri().toString(),
              "capabilities", Map.of(
                  "textDocument", Map.of(
                      "completion", Map.of("completionItem", Map.of("snippetSupport", false)),
                      "hover", Map.of(),
                      "definition", Map.of())))));
      Map<String, Object> init = t.receive();
      assertNotNull(init, "jdtls closed during initialize");
      assertNull(init.get("error"), () -> String.valueOf(init.get("error")));
      assertNotNull(init.get("result"));

      t.send(Map.of("jsonrpc", "2.0", "method", "initialized", "params", Map.of()));
      t.send(Map.of(
          "jsonrpc", "2.0",
          "id", 2,
          "method", "shutdown",
          "params", Map.of()));
      Map<String, Object> shutdown = t.receive();
      assertNotNull(shutdown);
      t.send(Map.of("jsonrpc", "2.0", "method", "exit", "params", Map.of()));
    } finally {
      proc.destroy();
      proc.waitFor(10, TimeUnit.SECONDS);
      if (proc.isAlive()) {
        proc.destroyForcibly();
      }
    }
  }
}
