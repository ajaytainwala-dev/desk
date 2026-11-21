package desk.core.workspace;

import desk.core.filesystem.FileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists recent workspaces and session state under {@code ~/.config/desk}
 * and {@code <workspace>/.desk/}.
 */
public final class WorkspaceStore {

  private WorkspaceStore() {
  }

  public static Path userConfigDir() {
    String home = System.getProperty("user.home");
    if (home == null) {
      return Path.of(".config", "desk");
    }
    return Path.of(home, ".config", "desk");
  }

  public static Path recentFile() {
    return userConfigDir().resolve("recent-workspaces.json");
  }

  public static Path currentPointerFile() {
    return userConfigDir().resolve("current-workspace.json");
  }

  public static Workspace open(Path root) {
    Objects.requireNonNull(root, "root");
    Path normalized = root.toAbsolutePath().normalize();
    if (!Files.isDirectory(normalized)) {
      throw new WorkspaceException("Not a directory: " + normalized);
    }
    ensureDeskDir(normalized);
    Workspace session = loadSession(normalized).orElseGet(() ->
        new Workspace(normalized, null, Instant.now(), List.of(), null));
    rememberRecent(session);
    writeCurrent(session);
    return session;
  }

  public static Workspace openPath(String path) {
    if (path == null || path.isBlank() || ".".equals(path.trim())) {
      return open(Path.of("").toAbsolutePath().normalize());
    }
    return open(Path.of(path.trim()));
  }

  public static void closeCurrent() {
    try {
      Files.deleteIfExists(currentPointerFile());
    } catch (IOException e) {
      throw new WorkspaceException("Unable to close workspace: " + e.getMessage(), e);
    }
  }

  public static Optional<Workspace> current() {
    Path pointer = currentPointerFile();
    if (!Files.isRegularFile(pointer)) {
      return Optional.empty();
    }
    try {
      String json = FileSystem.readString(pointer);
      String root = extractString(json, "root");
      if (root == null) {
        return Optional.empty();
      }
      Path path = Path.of(root);
      if (!Files.isDirectory(path)) {
        return Optional.empty();
      }
      return Optional.of(loadSession(path).orElseGet(() ->
          new Workspace(path, null, Instant.now(), List.of(), null)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static List<RecentWorkspace> listRecent() {
    Path file = recentFile();
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    try {
      String json = FileSystem.readString(file);
      return parseRecent(json);
    } catch (Exception e) {
      return List.of();
    }
  }

  public static void saveSession(Workspace workspace) {
    Objects.requireNonNull(workspace, "workspace");
    ensureDeskDir(workspace.root());
    StringBuilder docs = new StringBuilder();
    docs.append('[');
    List<OpenDocumentState> openDocuments = workspace.openDocuments();
    for (int i = 0; i < openDocuments.size(); i++) {
      OpenDocumentState doc = openDocuments.get(i);
      if (i > 0) {
        docs.append(',');
      }
      docs.append('{')
          .append("\"id\":").append(quote(doc.id())).append(',')
          .append("\"path\":").append(quote(doc.path().toString())).append(',')
          .append("\"cursorLine\":").append(doc.cursorLine()).append(',')
          .append("\"cursorColumn\":").append(doc.cursorColumn()).append(',')
          .append("\"scrollLine\":").append(doc.scrollLine())
          .append('}');
    }
    docs.append(']');

    String json = "{\n"
        + "  \"root\": " + quote(workspace.root().toString()) + ",\n"
        + "  \"name\": " + quote(workspace.name()) + ",\n"
        + "  \"openedAt\": " + quote(workspace.openedAt().toString()) + ",\n"
        + "  \"activeDocumentId\": "
        + workspace.activeDocumentId().map(WorkspaceStore::quote).orElse("null") + ",\n"
        + "  \"openDocuments\": " + docs + "\n"
        + "}\n";
    atomicWrite(workspace.sessionFile(), json);
    rememberRecent(workspace);
    writeCurrent(workspace);
  }

  public static OpenDocumentState newDocumentState(Path file, int line, int col, int scroll) {
    return new OpenDocumentState(UUID.randomUUID().toString(), file, line, col, scroll);
  }

  private static Optional<Workspace> loadSession(Path root) {
    Path session = root.resolve(".desk").resolve("session.json");
    if (!Files.isRegularFile(session)) {
      return Optional.empty();
    }
    try {
      String json = FileSystem.readString(session);
      String name = extractString(json, "name");
      String openedAt = extractString(json, "openedAt");
      String active = extractString(json, "activeDocumentId");
      List<OpenDocumentState> docs = parseDocuments(json);
      Instant instant = openedAt != null ? Instant.parse(openedAt) : Instant.now();
      return Optional.of(new Workspace(root, name, instant, docs, active));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private static List<OpenDocumentState> parseDocuments(String json) {
    List<OpenDocumentState> docs = new ArrayList<>();
    int arr = json.indexOf("\"openDocuments\"");
    if (arr < 0) {
      return docs;
    }
    int start = json.indexOf('[', arr);
    int end = json.indexOf(']', start);
    if (start < 0 || end < 0) {
      return docs;
    }
    String body = json.substring(start + 1, end);
    String[] objects = body.split("\\},\\s*\\{");
    for (String raw : objects) {
      String obj = raw.replace("{", "").replace("}", "").trim();
      if (obj.isEmpty()) {
        continue;
      }
      String id = extractString("{" + obj + "}", "id");
      String path = extractString("{" + obj + "}", "path");
      if (id == null || path == null) {
        continue;
      }
      int line = extractInt("{" + obj + "}", "cursorLine", 0);
      int col = extractInt("{" + obj + "}", "cursorColumn", 0);
      int scroll = extractInt("{" + obj + "}", "scrollLine", 0);
      docs.add(new OpenDocumentState(id, Path.of(path), line, col, scroll));
    }
    return docs;
  }

  private static void rememberRecent(Workspace workspace) {
    Map<String, RecentWorkspace> map = new LinkedHashMap<>();
    for (RecentWorkspace recent : listRecent()) {
      map.put(recent.root().toString(), recent);
    }
    RecentWorkspace updated = new RecentWorkspace(
        workspace.root(), workspace.name(), Instant.now());
    map.put(updated.root().toString(), updated);

    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"workspaces\": [\n");
    int i = 0;
    for (RecentWorkspace r : map.values()) {
      if (i++ > 0) {
        sb.append(",\n");
      }
      sb.append("    {\"root\":").append(quote(r.root().toString()))
          .append(",\"name\":").append(quote(r.name()))
          .append(",\"lastOpened\":").append(quote(r.lastOpened().toString()))
          .append('}');
      if (i >= 20) {
        break;
      }
    }
    sb.append("\n  ]\n}\n");
    atomicWrite(recentFile(), sb.toString());
  }

  private static List<RecentWorkspace> parseRecent(String json) {
    List<RecentWorkspace> list = new ArrayList<>();
    int arr = json.indexOf('[');
    int end = json.lastIndexOf(']');
    if (arr < 0 || end < 0) {
      return list;
    }
    String body = json.substring(arr + 1, end);
    String[] objects = body.split("\\},\\s*\\{");
    for (String raw : objects) {
      String obj = "{" + raw.replace("{", "").replace("}", "") + "}";
      String root = extractString(obj, "root");
      String name = extractString(obj, "name");
      String last = extractString(obj, "lastOpened");
      if (root == null) {
        continue;
      }
      Instant instant = last != null ? Instant.parse(last) : Instant.EPOCH;
      list.add(new RecentWorkspace(Path.of(root), name != null ? name : root, instant));
    }
    return list;
  }

  private static void writeCurrent(Workspace workspace) {
    String json = "{\n  \"root\": " + quote(workspace.root().toString())
        + ",\n  \"name\": " + quote(workspace.name()) + "\n}\n";
    atomicWrite(currentPointerFile(), json);
  }

  private static void ensureDeskDir(Path root) {
    try {
      Files.createDirectories(root.resolve(".desk"));
    } catch (IOException e) {
      throw new WorkspaceException("Unable to create .desk directory: " + e.getMessage(), e);
    }
  }

  private static void atomicWrite(Path file, String content) {
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      FileSystem.writeString(file, content);
    } catch (Exception e) {
      // Fallback for cross-device ATOMIC_MOVE failures: direct write
      try {
        Path parent = file.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(file, content);
      } catch (IOException ioe) {
        throw new WorkspaceException("Unable to write " + file + ": " + ioe.getMessage(), ioe);
      }
    }
  }

  static String extractString(String json, String key) {
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) {
      return null;
    }
    int i = colon + 1;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
      i++;
    }
    if (i < json.length() && json.regionMatches(i, "null", 0, 4)) {
      return null;
    }
    if (i >= json.length() || json.charAt(i) != '"') {
      return null;
    }
    int start = i + 1;
    int end = start;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == '\\' && end + 1 < json.length()) {
        end += 2;
        continue;
      }
      if (c == '"') {
        break;
      }
      end++;
    }
    return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  static int extractInt(String json, String key, int defaultValue) {
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) {
      return defaultValue;
    }
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) {
      return defaultValue;
    }
    int i = colon + 1;
    while (i < json.length() && !Character.isDigit(json.charAt(i)) && json.charAt(i) != '-') {
      i++;
    }
    int j = i;
    while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) {
      j++;
    }
    try {
      return Integer.parseInt(json.substring(i, j));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public record RecentWorkspace(Path root, String name, Instant lastOpened) {
  }
}
