package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.lsp.CompletionItem;
import desk.java.lsp.LanguageClient;
import desk.java.lsp.LanguageServerHost;
import desk.java.lsp.LanguageServerLauncher;
import desk.java.lsp.Location;
import desk.java.lsp.LspDiagnostic;
import desk.java.lsp.LspSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * {@code desk lsp ...} — Language Server Protocol operations.
 */
public final class LspCommand implements Command {

  @Override
  public String name() {
    return "lsp";
  }

  @Override
  public String description() {
    return "Java language intelligence via LSP (JDT LS when available).";
  }

  @Override
  public String usage() {
    return "<status|diagnose|complete|hover|definition|references|symbols> [file] [line] [col]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      System.out.println("""
          Usage: desk lsp <subcommand>

            status                         Show LSP / JDT LS discovery status
            diagnose <file>                Publish/compile diagnostics for a file
            complete <file> <line> <col>   Completions (requires JDT LS)
            hover <file> <line> <col>      Hover info (requires JDT LS)
            definition <file> <line> <col> Go to definition (requires JDT LS)
            references <file> <line> <col> Find references (requires JDT LS)
            symbols <file>                 Document symbols (requires JDT LS)

          Configure server with DESK_JAVA_LS=/path/to/jdtls (never hardcoded).
          Lines and columns are 1-based.
          """);
      return CommandResult.success();
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    try {
      return switch (sub) {
        case "status" -> status();
        case "diagnose" -> diagnose(args);
        case "complete" -> complete(args);
        case "hover" -> hover(args);
        case "definition" -> definition(args);
        case "references" -> references(args);
        case "symbols" -> symbols(args);
        default -> CommandResult.failure(1, "Unknown lsp subcommand: " + sub);
      };
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }

  private CommandResult status() {
    var discovered = LanguageServerLauncher.discover();
    System.out.println("DESK_JAVA_LS=" + nullToUnset(System.getenv("DESK_JAVA_LS")));
    if (discovered.isPresent()) {
      System.out.println("Discovered language server: " + discovered.get());
    } else {
      System.out.println("No JDT LS discovered. Diagnostics fall back to javac.");
      System.out.println("Install Eclipse JDT Language Server and set DESK_JAVA_LS.");
    }
    return CommandResult.success();
  }

  private CommandResult diagnose(String[] args) throws Exception {
    if (args.length < 2) {
      return CommandResult.failure(1, "Usage: desk lsp diagnose <file>");
    }
    Path file = Path.of(args[1]).toAbsolutePath().normalize();
    if (!FileSystem.isFile(file)) {
      return CommandResult.failure(1, "File not found: " + file);
    }
    Path root = workspaceRoot(file);
    LanguageServerHost host = new LanguageServerHost();
    try {
      LanguageClient client = host.ensureStarted(root);
      String content = Files.readString(file);
      List<LspDiagnostic> diags = client.diagnose(file, content).get(60, TimeUnit.SECONDS);
      if (diags.isEmpty()) {
        System.out.println("ok: no diagnostics");
      } else {
        for (LspDiagnostic d : diags) {
          System.out.println(d.severity() + " " + d.file() + ":" + d.line() + ":" + d.column()
              + " [" + d.source() + "] " + d.message());
        }
      }
      System.out.println("backend: " + (host.usingExternalServer() ? "jdtls" : "javac-fallback"));
      return diags.stream().anyMatch(d -> "ERROR".equals(d.severity()))
          ? CommandResult.failure(1, "diagnostics found")
          : CommandResult.success();
    } finally {
      host.shutdown();
    }
  }

  private CommandResult complete(String[] args) throws Exception {
    Pos p = requirePos(args, "complete");
    return withLsp(p, (session, file, content) -> {
      session.didOpen(file, "java", content);
      var items = session.completion(file, p.line0(), p.col0());
      for (CompletionItem item : items) {
        System.out.println(item.label()
            + (item.detail() != null ? " — " + item.detail() : ""));
      }
      System.out.println("(" + items.size() + " items)");
      return CommandResult.success();
    });
  }

  private CommandResult hover(String[] args) throws Exception {
    Pos p = requirePos(args, "hover");
    return withLsp(p, (session, file, content) -> {
      session.didOpen(file, "java", content);
      String hover = session.hover(file, p.line0(), p.col0());
      System.out.println(hover == null || hover.isBlank() ? "(no hover)" : hover);
      return CommandResult.success();
    });
  }

  private CommandResult definition(String[] args) throws Exception {
    Pos p = requirePos(args, "definition");
    return withLsp(p, (session, file, content) -> {
      session.didOpen(file, "java", content);
      for (Location loc : session.definition(file, p.line0(), p.col0())) {
        System.out.println(loc.file() + ":" + loc.line() + ":" + loc.column());
      }
      return CommandResult.success();
    });
  }

  private CommandResult references(String[] args) throws Exception {
    Pos p = requirePos(args, "references");
    return withLsp(p, (session, file, content) -> {
      session.didOpen(file, "java", content);
      for (Location loc : session.references(file, p.line0(), p.col0())) {
        System.out.println(loc.file() + ":" + loc.line() + ":" + loc.column());
      }
      return CommandResult.success();
    });
  }

  private CommandResult symbols(String[] args) throws Exception {
    if (args.length < 2) {
      return CommandResult.failure(1, "Usage: desk lsp symbols <file>");
    }
    Path file = Path.of(args[1]).toAbsolutePath().normalize();
    String content = Files.readString(file);
    Pos p = new Pos(file, 1, 1);
    return withLsp(p, (session, f, c) -> {
      session.didOpen(f, "java", content);
      for (var sym : session.documentSymbols(f)) {
        System.out.println(sym.name() + "  kind=" + sym.kind()
            + "  " + sym.line() + ":" + sym.character());
      }
      return CommandResult.success();
    });
  }

  @FunctionalInterface
  private interface LspAction {
    CommandResult run(LspSession session, Path file, String content) throws Exception;
  }

  private CommandResult withLsp(Pos pos, LspAction action) throws Exception {
    Path root = workspaceRoot(pos.file());
    var serverPath = LanguageServerLauncher.discover();
    if (serverPath.isEmpty()) {
      return CommandResult.failure(1,
          "No JDT LS found. Set DESK_JAVA_LS to the jdtls launcher, or use 'desk lsp diagnose' for javac diagnostics.");
    }
    var launched = LanguageServerLauncher.start(root, serverPath.get());
    try {
      String content = Files.readString(pos.file());
      return action.run(launched.session(), pos.file(), content);
    } finally {
      launched.close();
    }
  }

  private Pos requirePos(String[] args, String name) {
    if (args.length < 4) {
      throw new IllegalArgumentException(
          "Usage: desk lsp " + name + " <file> <line> <col>  (1-based)");
    }
    Path file = Path.of(args[1]).toAbsolutePath().normalize();
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File not found: " + file);
    }
    int line = Integer.parseInt(args[2]);
    int col = Integer.parseInt(args[3]);
    return new Pos(file, line, col);
  }

  private Path workspaceRoot(Path file) {
    return WorkspaceStore.current().map(Workspace::root)
        .orElseGet(() -> file.getParent() != null ? file.getParent() : Path.of(".").toAbsolutePath());
  }

  private static String nullToUnset(String v) {
    return v == null || v.isBlank() ? "(unset)" : v;
  }

  private record Pos(Path file, int line, int col) {
    int line0() {
      return Math.max(0, line - 1);
    }

    int col0() {
      return Math.max(0, col - 1);
    }
  }
}
