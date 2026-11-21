package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.git.GitService;

import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code desk git ...} — thin Git CLI facade (never logs credentials).
 */
public final class GitCommand implements Command {

  @Override
  public String name() {
    return "git";
  }

  @Override
  public String description() {
    return "Git status, branch, and basic workflow helpers.";
  }

  @Override
  public String usage() {
    return "<status|branch|diff|log|add|commit|push|pull> [args]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0])) {
      System.out.println("""
          Usage: desk git <status|branch|diff|log|add|commit|push|pull> [args]

          Wraps the system git CLI. Credentials are never printed by Desk.
          """);
      return CommandResult.success();
    }
    try {
      Path root = WorkspaceStore.current().map(Workspace::root)
          .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
      String sub = args[0].toLowerCase(Locale.ROOT);
      String[] rest = new String[Math.max(0, args.length - 1)];
      if (args.length > 1) {
        System.arraycopy(args, 1, rest, 0, rest.length);
      }
      var result = switch (sub) {
        case "status" -> GitService.status(root);
        case "branch" -> rest.length == 0 ? GitService.branch(root) : GitService.createBranch(root, rest[0]);
        case "diff" -> GitService.diff(root, rest);
        case "log" -> GitService.log(root, 20);
        case "add" -> GitService.add(root, rest);
        case "commit" -> GitService.commit(root, rest);
        case "push" -> GitService.push(root);
        case "pull" -> GitService.pull(root);
        default -> new GitService.Result(1, "Unknown git subcommand: " + sub);
      };
      System.out.print(result.output());
      return result.exitCode() == 0
          ? CommandResult.success()
          : CommandResult.failure(result.exitCode(), "git " + sub + " failed");
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
