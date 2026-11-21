package desk.java.lsp;

import desk.java.JavaException;
import desk.java.lsp.json.Json;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * JSON-RPC 2.0 request/response correlation over {@link ContentLengthTransport}.
 */
public final class JsonRpcConnection implements AutoCloseable {

  private final ContentLengthTransport transport;
  private final AtomicInteger nextId = new AtomicInteger(1);
  private final ConcurrentHashMap<Integer, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
  private final Thread reader;
  private volatile boolean closed;
  private volatile Consumer<Map<String, Object>> notificationHandler = m -> {
  };
  private final AtomicLong lastMessageAt = new AtomicLong(System.currentTimeMillis());

  public JsonRpcConnection(ContentLengthTransport transport) {
    this.transport = transport;
    this.reader = new Thread(this::readLoop, "desk-lsp-reader");
    this.reader.setDaemon(true);
    this.reader.start();
  }

  public void setNotificationHandler(Consumer<Map<String, Object>> handler) {
    this.notificationHandler = handler != null ? handler : m -> {
    };
  }

  public CompletableFuture<Object> request(String method, Object params) {
    int id = nextId.getAndIncrement();
    CompletableFuture<Object> future = new CompletableFuture<>();
    pending.put(id, future);
    Map<String, Object> message = Json.obj(
        "jsonrpc", "2.0",
        "id", id,
        "method", method,
        "params", params);
    try {
      transport.send(message);
    } catch (IOException e) {
      pending.remove(id);
      future.completeExceptionally(e);
    }
    return future;
  }

  public Object requestSync(String method, Object params, long timeout, TimeUnit unit) {
    try {
      return request(method, params).get(timeout, unit);
    } catch (Exception e) {
      throw new JavaException("LSP request failed: " + method + " — " + e.getMessage(), e);
    }
  }

  public void notify(String method, Object params) {
    Map<String, Object> message = Json.obj(
        "jsonrpc", "2.0",
        "method", method,
        "params", params);
    try {
      transport.send(message);
    } catch (IOException e) {
      throw new JavaException("LSP notify failed: " + method, e);
    }
  }

  public void cancel(int id) {
    notify("$/cancelRequest", Json.obj("id", id));
    CompletableFuture<Object> f = pending.remove(id);
    if (f != null) {
      f.completeExceptionally(new JavaException("Request cancelled: " + id));
    }
  }

  public boolean isAlive() {
    return !closed && reader.isAlive();
  }

  public long lastMessageAtMillis() {
    return lastMessageAt.get();
  }

  private void readLoop() {
    try {
      while (!closed) {
        Map<String, Object> msg = transport.receive();
        if (msg == null) {
          break;
        }
        lastMessageAt.set(System.currentTimeMillis());
        dispatch(msg);
      }
    } catch (Exception e) {
      failAll(e);
    } finally {
      closed = true;
      failAll(new JavaException("LSP connection closed"));
    }
  }

  private void dispatch(Map<String, Object> msg) {
    Object id = msg.get("id");
    String method = Json.asString(msg.get("method"));
    if (method != null && id == null) {
      notificationHandler.accept(msg);
      return;
    }
    if (id != null) {
      int iid = Json.asInt(id, -1);
      CompletableFuture<Object> future = pending.remove(iid);
      if (future == null) {
        return;
      }
      if (msg.containsKey("error")) {
        Map<String, Object> err = Json.asObject(msg.get("error"));
        String message = err != null ? Json.asString(err.get("message")) : "LSP error";
        future.completeExceptionally(new JavaException(message));
      } else {
        future.complete(msg.get("result"));
      }
    }
  }

  private void failAll(Exception e) {
    for (CompletableFuture<Object> f : pending.values()) {
      f.completeExceptionally(e);
    }
    pending.clear();
  }

  @Override
  public void close() {
    closed = true;
    try {
      transport.close();
    } catch (IOException ignored) {
      // ignore
    }
    reader.interrupt();
    failAll(new JavaException("LSP connection closed"));
  }
}
