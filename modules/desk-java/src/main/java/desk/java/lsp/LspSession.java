package desk.java.lsp;

import desk.java.JavaException;
import desk.java.lsp.json.Json;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level LSP session: initialize, document sync, and common requests.
 */
public final class LspSession implements AutoCloseable {

  private final JsonRpcConnection connection;
  private final Path workspaceRoot;
  private final AtomicInteger documentVersion = new AtomicInteger(1);
  private final ConcurrentHashMap<Path, Integer> openVersions = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<LspDiagnostic> latestDiagnostics = new CopyOnWriteArrayList<>();
  private volatile boolean initialized;

  public LspSession(JsonRpcConnection connection, Path workspaceRoot) {
    this.connection = Objects.requireNonNull(connection, "connection");
    this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    connection.setNotificationHandler(this::onNotification);
  }

  public void initialize() {
    Map<String, Object> caps = Json.obj(
        "textDocument", Json.obj(
            "synchronization", Json.obj("didSave", true, "dynamicRegistration", false),
            "completion", Json.obj("completionItem", Json.obj("snippetSupport", false)),
            "hover", Json.obj("contentFormat", Json.arr("plaintext", "markdown")),
            "definition", Json.obj(),
            "references", Json.obj(),
            "documentSymbol", Json.obj(),
            "formatting", Json.obj(),
            "rename", Json.obj("prepareSupport", false),
            "codeAction", Json.obj("codeActionLiteralSupport", Json.obj(
                "codeActionKind", Json.obj("valueSet", Json.arr("quickfix", "refactor", "source"))))),
        "workspace", Json.obj(
            "workspaceFolders", true));

    Map<String, Object> params = Json.obj(
        "processId", ProcessHandle.current().pid(),
        "rootUri", toUri(workspaceRoot),
        "rootPath", workspaceRoot.toString(),
        "capabilities", caps,
        "workspaceFolders", Json.arr(Json.obj(
            "uri", toUri(workspaceRoot),
            "name", workspaceRoot.getFileName() != null
                ? workspaceRoot.getFileName().toString()
                : "workspace")));

    connection.requestSync("initialize", params, 60, TimeUnit.SECONDS);
    connection.notify("initialized", Json.obj());
    initialized = true;
  }

  public boolean isInitialized() {
    return initialized && connection.isAlive();
  }

  public void didOpen(Path file, String languageId, String text) {
    ensureInit();
    Path norm = file.toAbsolutePath().normalize();
    int version = documentVersion.getAndIncrement();
    openVersions.put(norm, version);
    connection.notify("textDocument/didOpen", Json.obj(
        "textDocument", Json.obj(
            "uri", toUri(norm),
            "languageId", languageId != null ? languageId : "java",
            "version", version,
            "text", text != null ? text : "")));
  }

  public void didChange(Path file, String text) {
    ensureInit();
    Path norm = file.toAbsolutePath().normalize();
    int version = documentVersion.getAndIncrement();
    openVersions.put(norm, version);
    connection.notify("textDocument/didChange", Json.obj(
        "textDocument", Json.obj("uri", toUri(norm), "version", version),
        "contentChanges", Json.arr(Json.obj("text", text != null ? text : ""))));
  }

  public void didSave(Path file, String text) {
    ensureInit();
    Path norm = file.toAbsolutePath().normalize();
    connection.notify("textDocument/didSave", Json.obj(
        "textDocument", Json.obj("uri", toUri(norm)),
        "text", text));
  }

  public void didClose(Path file) {
    ensureInit();
    Path norm = file.toAbsolutePath().normalize();
    openVersions.remove(norm);
    connection.notify("textDocument/didClose", Json.obj(
        "textDocument", Json.obj("uri", toUri(norm))));
  }

  public List<CompletionItem> completion(Path file, int line, int character) {
    Object result = documentRequest("textDocument/completion", file, line, character);
    return parseCompletions(result);
  }

  public String hover(Path file, int line, int character) {
    Object result = documentRequest("textDocument/hover", file, line, character);
    if (result == null) {
      return "";
    }
    Map<String, Object> obj = Json.asObject(result);
    if (obj == null) {
      return "";
    }
    Object contents = obj.get("contents");
    if (contents instanceof String s) {
      return s;
    }
    Map<String, Object> marked = Json.asObject(contents);
    if (marked != null && marked.get("value") != null) {
      return Json.asString(marked.get("value"));
    }
    List<Object> arr = Json.asArray(contents);
    if (arr != null && !arr.isEmpty()) {
      Object first = arr.get(0);
      Map<String, Object> m = Json.asObject(first);
      if (m != null && m.get("value") != null) {
        return Json.asString(m.get("value"));
      }
      return String.valueOf(first);
    }
    return "";
  }

  public List<Location> definition(Path file, int line, int character) {
    return parseLocations(documentRequest("textDocument/definition", file, line, character));
  }

  public List<Location> references(Path file, int line, int character) {
    Map<String, Object> params = positionParams(file, line, character);
    params.put("context", Json.obj("includeDeclaration", true));
    return parseLocations(connection.requestSync(
        "textDocument/references", params, 30, TimeUnit.SECONDS));
  }

  public List<DocumentSymbol> documentSymbols(Path file) {
    ensureInit();
    Object result = connection.requestSync("textDocument/documentSymbol",
        Json.obj("textDocument", Json.obj("uri", toUri(file.toAbsolutePath().normalize()))),
        30, TimeUnit.SECONDS);
    return parseSymbols(result);
  }

  public String formatting(Path file, int tabSize, boolean insertSpaces) {
    ensureInit();
    Object result = connection.requestSync("textDocument/formatting", Json.obj(
        "textDocument", Json.obj("uri", toUri(file.toAbsolutePath().normalize())),
        "options", Json.obj("tabSize", tabSize, "insertSpaces", insertSpaces)),
        60, TimeUnit.SECONDS);
    List<Object> edits = Json.asArray(result);
    if (edits == null || edits.isEmpty()) {
      return null;
    }
    // Return concatenated newText of full-document edits when present.
    StringBuilder sb = new StringBuilder();
    for (Object edit : edits) {
      Map<String, Object> e = Json.asObject(edit);
      if (e != null && e.get("newText") != null) {
        sb.append(Json.asString(e.get("newText")));
      }
    }
    return sb.toString();
  }

  public Object rename(Path file, int line, int character, String newName) {
    ensureInit();
    return connection.requestSync("textDocument/rename", Json.obj(
        "textDocument", Json.obj("uri", toUri(file.toAbsolutePath().normalize())),
        "position", Json.obj("line", line, "character", character),
        "newName", newName), 60, TimeUnit.SECONDS);
  }

  public List<CodeAction> codeActions(Path file, int line, int character) {
    ensureInit();
    Object result = connection.requestSync("textDocument/codeAction", Json.obj(
        "textDocument", Json.obj("uri", toUri(file.toAbsolutePath().normalize())),
        "range", Json.obj(
            "start", Json.obj("line", line, "character", character),
            "end", Json.obj("line", line, "character", character)),
        "context", Json.obj("diagnostics", Json.arr())), 30, TimeUnit.SECONDS);
    List<Object> arr = Json.asArray(result);
    List<CodeAction> actions = new ArrayList<>();
    if (arr == null) {
      return actions;
    }
    for (Object item : arr) {
      Map<String, Object> m = Json.asObject(item);
      if (m == null) {
        continue;
      }
      actions.add(new CodeAction(
          Json.asString(m.get("title")),
          Json.asString(m.get("kind")),
          m.containsKey("edit") ? "workspaceEdit" : null));
    }
    return actions;
  }

  public List<LspDiagnostic> diagnostics() {
    return List.copyOf(latestDiagnostics);
  }

  private Object documentRequest(String method, Path file, int line, int character) {
    ensureInit();
    return connection.requestSync(method, positionParams(file, line, character), 30, TimeUnit.SECONDS);
  }

  private Map<String, Object> positionParams(Path file, int line, int character) {
    return Json.obj(
        "textDocument", Json.obj("uri", toUri(file.toAbsolutePath().normalize())),
        "position", Json.obj("line", Math.max(0, line), "character", Math.max(0, character)));
  }

  private void onNotification(Map<String, Object> msg) {
    String method = Json.asString(msg.get("method"));
    if ("textDocument/publishDiagnostics".equals(method)) {
      Map<String, Object> params = Json.asObject(msg.get("params"));
      if (params == null) {
        return;
      }
      String uri = Json.asString(params.get("uri"));
      Path file = fromUri(uri);
      List<Object> diags = Json.asArray(params.get("diagnostics"));
      List<LspDiagnostic> parsed = new ArrayList<>();
      if (diags != null) {
        for (Object d : diags) {
          Map<String, Object> dm = Json.asObject(d);
          if (dm == null) {
            continue;
          }
          Map<String, Object> range = Json.asObject(dm.get("range"));
          Map<String, Object> start = range != null ? Json.asObject(range.get("start")) : null;
          Map<String, Object> end = range != null ? Json.asObject(range.get("end")) : null;
          int severity = Json.asInt(dm.get("severity"), 1);
          String sev = switch (severity) {
            case 1 -> "ERROR";
            case 2 -> "WARNING";
            case 3 -> "INFO";
            default -> "HINT";
          };
          parsed.add(new LspDiagnostic(
              file,
              start != null ? Json.asInt(start.get("line"), 0) + 1 : 1,
              start != null ? Json.asInt(start.get("character"), 0) + 1 : 1,
              end != null ? Json.asInt(end.get("line"), 0) + 1 : 1,
              end != null ? Json.asInt(end.get("character"), 0) + 1 : 1,
              sev,
              Json.asString(dm.get("message")),
              "lsp"));
        }
      }
      latestDiagnostics.removeIf(d -> d.file() != null && d.file().equals(file));
      latestDiagnostics.addAll(parsed);
    }
  }

  private void ensureInit() {
    if (!initialized) {
      throw new JavaException("LSP session not initialized");
    }
  }

  static String toUri(Path path) {
    return path.toAbsolutePath().normalize().toUri().toString();
  }

  static Path fromUri(String uri) {
    if (uri == null) {
      return null;
    }
    try {
      return Path.of(URI.create(uri));
    } catch (Exception e) {
      return Path.of(uri.replace("file://", ""));
    }
  }

  @SuppressWarnings("unchecked")
  private static List<CompletionItem> parseCompletions(Object result) {
    List<CompletionItem> items = new ArrayList<>();
    List<Object> arr = Json.asArray(result);
    if (arr == null) {
      Map<String, Object> obj = Json.asObject(result);
      if (obj != null) {
        arr = Json.asArray(obj.get("items"));
      }
    }
    if (arr == null) {
      return items;
    }
    for (Object item : arr) {
      Map<String, Object> m = Json.asObject(item);
      if (m == null) {
        continue;
      }
      String label = Json.asString(m.get("label"));
      String detail = Json.asString(m.get("detail"));
      String insert = m.get("insertText") != null
          ? Json.asString(m.get("insertText"))
          : label;
      String kind = String.valueOf(m.getOrDefault("kind", ""));
      items.add(new CompletionItem(label, detail, insert, kind));
    }
    return items;
  }

  private static List<Location> parseLocations(Object result) {
    List<Location> locs = new ArrayList<>();
    if (result == null) {
      return locs;
    }
    List<Object> arr = Json.asArray(result);
    if (arr == null) {
      Map<String, Object> single = Json.asObject(result);
      if (single != null) {
        arr = List.of(single);
      }
    }
    if (arr == null) {
      return locs;
    }
    for (Object item : arr) {
      Map<String, Object> m = Json.asObject(item);
      if (m == null) {
        continue;
      }
      // Location or LocationLink
      String uri = Json.asString(m.get("uri"));
      if (uri == null) {
        uri = Json.asString(m.get("targetUri"));
      }
      Map<String, Object> range = Json.asObject(m.get("range"));
      if (range == null) {
        range = Json.asObject(m.get("targetSelectionRange"));
      }
      Map<String, Object> start = range != null ? Json.asObject(range.get("start")) : null;
      locs.add(new Location(
          fromUri(uri),
          start != null ? Json.asInt(start.get("line"), 0) + 1 : 1,
          start != null ? Json.asInt(start.get("character"), 0) + 1 : 1));
    }
    return locs;
  }

  private static List<DocumentSymbol> parseSymbols(Object result) {
    List<DocumentSymbol> symbols = new ArrayList<>();
    List<Object> arr = Json.asArray(result);
    if (arr == null) {
      return symbols;
    }
    for (Object item : arr) {
      Map<String, Object> m = Json.asObject(item);
      if (m == null) {
        continue;
      }
      // DocumentSymbol or SymbolInformation
      String name = Json.asString(m.get("name"));
      int kind = Json.asInt(m.get("kind"), 0);
      Map<String, Object> range = Json.asObject(m.get("selectionRange"));
      if (range == null) {
        Map<String, Object> loc = Json.asObject(m.get("location"));
        range = loc != null ? Json.asObject(loc.get("range")) : Json.asObject(m.get("range"));
      }
      Map<String, Object> start = range != null ? Json.asObject(range.get("start")) : null;
      symbols.add(new DocumentSymbol(
          name,
          kind,
          start != null ? Json.asInt(start.get("line"), 0) + 1 : 1,
          start != null ? Json.asInt(start.get("character"), 0) + 1 : 1));
    }
    return symbols;
  }

  @Override
  public void close() {
    try {
      if (initialized) {
        try {
          connection.request("shutdown", null).orTimeout(5, TimeUnit.SECONDS).exceptionally(e -> null);
        } catch (Exception ignored) {
          // ignore
        }
        try {
          connection.notify("exit", null);
        } catch (Exception ignored) {
          // ignore
        }
      }
    } finally {
      connection.close();
    }
  }

  public record DocumentSymbol(String name, int kind, int line, int character) {
  }
}
