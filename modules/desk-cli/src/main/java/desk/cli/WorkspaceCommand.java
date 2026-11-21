package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JdkSelector;
import desk.java.project.ProjectDescriptor;
import desk.java.project.ProjectDetector;

import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code desk workspace ...} — open/list/close workspaces.
 */
public final class WorkspaceCommand implements Command {

  @Override
  public String name() {
    return "workspace";
  }

  @Override
  public String description() {
    return "Manage Desk workspaces (open, list, close).";
  }

  @Override
  public String usage() {
    return "<list|open|close|status> [path]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      System.out.println("""
          Usage: desk workspace <list|open|close|status> [path]

            list            List recent workspaces
            open [path]     Open a folder as the current workspace
            close           Clear the current workspace pointer
            status          Show current workspace and detected project
          """);
      return CommandResult.success();
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    try {
      return switch (sub) {
        case "list" -> list();
        case "open" -> open(args.length > 1 ? args[1] : ".");
        case "close" -> close();
        case "status" -> status();
        default -> CommandResult.failure(1, "Unknown workspace subcommand: " + sub);
      };
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }

  private CommandResult list() {
    var recent = WorkspaceStore.listRecent();
    if (recent.isEmpty()) {
      System.out.println("No recent workspaces.");
      return CommandResult.success();
    }
    System.out.println("Recent workspaces:");
    for (var r : recent) {
      System.out.println("  " + r.name() + "  " + r.root() + "  (" + r.lastOpened() + ")");
    }
    return CommandResult.success();
  }

  private CommandResult open(String path) {
    Workspace ws = WorkspaceStore.openPath(path);
    ProjectDescriptor project = ProjectDetector.detect(ws.root());
    System.out.println("Opened workspace: " + ws.root());
    System.out.println("Project: " + project.summary());
    JdkSelector.current(ws.root()).ifPresent(j ->
        System.out.println("JDK: " + j.summaryLine()));
    WorkspaceStore.saveSession(ws);
    return CommandResult.success();
  }

  private CommandResult close() {
    WorkspaceStore.closeCurrent();
    System.out.println("Workspace closed.");
    return CommandResult.success();
  }

  private CommandResult status() {
    var current = WorkspaceStore.current();
    if (current.isEmpty()) {
      System.out.println("No current workspace. Run 'desk workspace open .'.");
      return CommandResult.success();
    }
    Workspace ws = current.get();
    System.out.println("Current workspace: " + ws.root());
    System.out.println("Name: " + ws.name());
    System.out.println("Open documents: " + ws.openDocuments().size());
    ProjectDescriptor project = ProjectDetector.detect(ws.root());
    System.out.println("Project: " + project.summary());
    JdkSelector.current(ws.root()).ifPresent(j ->
        System.out.println("JDK: " + j.summaryLine()));
    return CommandResult.success();
  }
}
