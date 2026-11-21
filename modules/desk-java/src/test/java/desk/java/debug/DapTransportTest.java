package desk.java.debug;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DapTransportTest {

  @Test
  void framesRoundTrip() throws Exception {
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("seq", 1);
    msg.put("type", "request");
    msg.put("command", "initialize");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DapTransport writer = new DapTransport(new ByteArrayInputStream(new byte[0]), out);
    writer.send(msg);
    byte[] framed = out.toByteArray();
    String text = new String(framed, StandardCharsets.UTF_8);
    assertTrue(text.startsWith("Content-Length:"));
    DapTransport reader = new DapTransport(new ByteArrayInputStream(framed),
        new ByteArrayOutputStream());
    Map<String, Object> got = reader.receive();
    assertEquals("initialize", got.get("command"));
  }

  @Test
  void initializeHandshakeWithMockAdapter() throws Exception {
    PipedInputStream clientIn = new PipedInputStream(64 * 1024);
    PipedOutputStream adapterOut = new PipedOutputStream(clientIn);
    PipedInputStream adapterIn = new PipedInputStream(64 * 1024);
    PipedOutputStream clientOut = new PipedOutputStream(adapterIn);

    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> mock = pool.submit(() -> {
      try (DapTransport t = new DapTransport(adapterIn, adapterOut)) {
        Map<String, Object> req = t.receive();
        assertEquals("initialize", req.get("command"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supportsConfigurationDoneRequest", true);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("seq", 1);
        resp.put("type", "response");
        resp.put("request_seq", req.get("seq"));
        resp.put("success", true);
        resp.put("command", "initialize");
        resp.put("body", body);
        t.send(resp);
        Map<String, Object> initReq = t.receive();
        assertEquals("initialized", initReq.get("command"));
        Map<String, Object> initResp = new LinkedHashMap<>();
        initResp.put("seq", 2);
        initResp.put("type", "response");
        initResp.put("request_seq", initReq.get("seq"));
        initResp.put("success", true);
        initResp.put("command", "initialized");
        t.send(initResp);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    try (DapTransport transport = new DapTransport(clientIn, clientOut);
         DapSession session = new DapSession(Path.of(".").toAbsolutePath())) {
      Map<String, Object> caps = session.initializeWithTransport(transport);
      assertTrue(session.isInitialized());
      assertEquals(Boolean.TRUE, caps.get("supportsConfigurationDoneRequest"));
      assertThrows(UnsupportedOperationException.class,
          () -> session.launch(new DapSession.LaunchConfig(
              "t", Path.of("."), "Main", List.of(), List.of(), null)));
    }
    mock.get(5, TimeUnit.SECONDS);
    pool.shutdownNow();
  }
}
