package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JdkSelector;
import desk.java.build.BuildDiagnostic;
import desk.java.build.BuildRequest;
import desk.java.build.BuildResult;
import desk.java.build.BuildService;

import java.nio.file.Path;

/**
 * {@code desk test} — run project tests via the detected build system.
 */
public final class TestCommand implements Command {

  @Override
  public String name() {
    return "test";
  }

  @Override
  public String description() {
    return "Run tests for the current workspace.";
  }

  @Override
  public String usage() {
    return "[--watch]";
  }

  @Override
  public CommandResult execute(String[] args) {
    boolean watch = false;
    for (String arg : args) {
      if ("--watch".equals(arg)) {
        watch = true;
      }
      if ("--help".equals(arg) || "-h".equals(arg)) {
        System.out.println("Usage: desk test [--watch]");
        return CommandResult.success();
      }
    }
    try {
      Path root = WorkspaceStore.current().map(Workspace::root)
          .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
      if (watch) {
        return CommandResult.failure(1,
            "desk test --watch is not implemented yet. Omit --watch to run tests once.");
      }
      Path jdk = JdkSelector.current(root).map(j -> j.home()).orElse(null);
      BuildResult result = BuildService.execute(
          new BuildRequest(root, BuildRequest.Goal.TEST, jdk, watch));
      System.out.print(result.output());
      for (BuildDiagnostic d : result.diagnostics()) {
        System.out.println(d.format());
      }
      return result.success()
          ? CommandResult.success()
          : CommandResult.failure(result.exitCode() == 0 ? 1 : result.exitCode(), "Tests failed");
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
