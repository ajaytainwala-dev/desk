package desk.java.lsp;

import desk.java.lsp.json.Json;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentLengthTransportTest {

  @Test
  void framesAndParsesMessage() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ContentLengthTransport writer = new ContentLengthTransport(
        new ByteArrayInputStream(new byte[0]), out);
    writer.send(Json.obj("jsonrpc", "2.0", "method", "initialized", "params", Json.obj()));

    byte[] bytes = out.toByteArray();
    String raw = new String(bytes, StandardCharsets.UTF_8);
    assertTrue(raw.startsWith("Content-Length:"));
    assertTrue(raw.contains("\r\n\r\n"));

    ContentLengthTransport reader = new ContentLengthTransport(
        new ByteArrayInputStream(bytes), new ByteArrayOutputStream());
    Map<String, Object> msg = reader.receive();
    assertNotNull(msg);
    assertEquals("initialized", msg.get("method"));
  }
}
