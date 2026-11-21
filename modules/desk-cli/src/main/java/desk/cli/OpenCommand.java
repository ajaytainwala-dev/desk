package desk.cli;

import desk.core.workspace.OpenDocumentState;
import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code desk open <path>} — open a folder workspace or a single file.
 */
public final class OpenCommand implements Command {

  @Override
  public String name() {
    return "open";
  }

  @Override
  public String description() {
    return "Open a folder as a workspace or a file in the workspace session.";
  }

  @Override
  public String usage() {
    return "<path>";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0])) {
      System.out.println("Usage: desk open <path>");
      System.out.println("       desk .");
      return CommandResult.success();
    }
    try {
      Path path = Path.of(args[0]).toAbsolutePath().normalize();
      if (Files.isDirectory(path)) {
        Workspace ws = WorkspaceStore.open(path);
        System.out.println("Opened workspace: " + ws.root());
        int code = TuiLauncher.launch(path.toString());
        return code == 0 ? CommandResult.success()
            : CommandResult.failure(code == 1 && TuiLauncher.locateEditorBinary() == null ? 1 : code,
                "Editor exited with code " + code);
      }
      if (!Files.isRegularFile(path)) {
        return CommandResult.failure(1, "Path not found: " + path);
      }
      Path root = path.getParent() != null ? path.getParent() : Path.of(".").toAbsolutePath();
      Workspace ws = WorkspaceStore.open(root);
      OpenDocumentState doc = WorkspaceStore.newDocumentState(path, 0, 0, 0);
      ws = ws.withDocument(doc);
      WorkspaceStore.saveSession(ws);
      int code = TuiLauncher.launch(path.toString());
      return code == 0 ? CommandResult.success() : CommandResult.failure(code, "Editor exited with " + code);
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
