package desk.java.debug;

import desk.java.lsp.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Debug Adapter Protocol Content-Length framing (same wire shape as LSP).
 * Does not claim a working debugger — only transport + request helpers.
 */
public final class DapTransport implements AutoCloseable {

  private final InputStream in;
  private final OutputStream out;
  private final Object writeLock = new Object();
  private final AtomicLong nextId = new AtomicLong(1);

  public DapTransport(InputStream in, OutputStream out) {
    this.in = Objects.requireNonNull(in, "in");
    this.out = Objects.requireNonNull(out, "out");
  }

  public long nextId() {
    return nextId.getAndIncrement();
  }

  public void send(Map<String, Object> message) throws IOException {
    byte[] body = Json.stringify(message).getBytes(StandardCharsets.UTF_8);
    String header = "Content-Length: " + body.length + "\r\n\r\n";
    synchronized (writeLock) {
      out.write(header.getBytes(StandardCharsets.US_ASCII));
      out.write(body);
      out.flush();
    }
  }

  public Map<String, Object> receive() throws IOException {
    int contentLength = -1;
    while (true) {
      String line = readHeaderLine();
      if (line == null) {
        return null;
      }
      if (line.isEmpty()) {
        break;
      }
      int colon = line.indexOf(':');
      if (colon > 0) {
        String name = line.substring(0, colon).trim();
        String value = line.substring(colon + 1).trim();
        if ("Content-Length".equalsIgnoreCase(name)) {
          contentLength = Integer.parseInt(value);
        }
      }
    }
    if (contentLength < 0) {
      throw new IOException("Missing Content-Length header");
    }
    if (contentLength > 32 * 1024 * 1024) {
      throw new IOException("Content-Length too large: " + contentLength);
    }
    byte[] body = in.readNBytes(contentLength);
    if (body.length != contentLength) {
      throw new IOException("Unexpected EOF reading body");
    }
    return Json.parseObject(new String(body, StandardCharsets.UTF_8));
  }

  public Map<String, Object> request(String command, Map<String, Object> arguments)
      throws IOException {
    long id = nextId();
    Map<String, Object> msg = new LinkedHashMap<>();
    msg.put("seq", id);
    msg.put("type", "request");
    msg.put("command", command);
    if (arguments != null) {
      msg.put("arguments", arguments);
    }
    send(msg);
    while (true) {
      Map<String, Object> resp = receive();
      if (resp == null) {
        throw new IOException("Adapter closed during " + command);
      }
      Object type = resp.get("type");
      if ("response".equals(type) && Objects.equals(asLong(resp.get("request_seq")), id)) {
        return resp;
      }
      // Ignore events until matching response; caller can drain later via receive().
    }
  }

  private static Long asLong(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o instanceof String s) {
      try {
        return Long.parseLong(s);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private String readHeaderLine() throws IOException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      int b = in.read();
      if (b < 0) {
        return sb.isEmpty() ? null : sb.toString();
      }
      if (b == '\n') {
        String line = sb.toString();
        if (line.endsWith("\r")) {
          line = line.substring(0, line.length() - 1);
        }
        return line;
      }
      sb.append((char) b);
    }
  }

  @Override
  public void close() throws IOException {
    try {
      in.close();
    } finally {
      out.close();
    }
  }
}
