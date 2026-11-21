package desk.java.lsp;

import desk.java.lsp.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * LSP Content-Length framed message transport over arbitrary streams.
 */
public final class ContentLengthTransport implements AutoCloseable {

  private final InputStream in;
  private final OutputStream out;
  private final Object writeLock = new Object();

  public ContentLengthTransport(InputStream in, OutputStream out) {
    this.in = in;
    this.out = out;
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

  /**
   * Reads the next JSON-RPC message, or {@code null} on EOF.
   */
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
    String text = new String(body, StandardCharsets.UTF_8);
    return Json.parseObject(text);
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
      if (sb.length() > 16_384) {
        throw new IOException("Header line too long");
      }
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

  static boolean looksLikeWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
