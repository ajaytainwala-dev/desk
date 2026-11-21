package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JdkSelector;
import desk.java.build.BuildRequest;
import desk.java.build.BuildResult;
import desk.java.build.BuildService;

import java.nio.file.Path;

/**
 * {@code desk clean} — clean build outputs via detected build system.
 */
public final class CleanCommand implements Command {

  @Override
  public String name() {
    return "clean";
  }

  @Override
  public String description() {
    return "Clean build outputs for the current workspace.";
  }

  @Override
  public CommandResult execute(String[] args) {
    try {
      Path root = WorkspaceStore.current().map(Workspace::root)
          .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
      Path jdk = JdkSelector.current(root).map(j -> j.home()).orElse(null);
      BuildResult result = BuildService.execute(
          new BuildRequest(root, BuildRequest.Goal.CLEAN, jdk, false));
      System.out.print(result.output());
      return result.success()
          ? CommandResult.success()
          : CommandResult.failure(result.exitCode() == 0 ? 1 : result.exitCode(), "Clean failed");
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
