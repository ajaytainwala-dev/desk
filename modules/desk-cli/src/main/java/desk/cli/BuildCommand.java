package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JdkSelector;
import desk.java.build.BuildDiagnostic;
import desk.java.build.BuildRequest;
import desk.java.build.BuildResult;
import desk.java.build.BuildService;

import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code desk build ...} — orchestrate Maven/Gradle/javac.
 */
public final class BuildCommand implements Command {

  @Override
  public String name() {
    return "build";
  }

  @Override
  public String description() {
    return "Build the current workspace (auto-detects Maven, Gradle, or javac).";
  }

  @Override
  public String usage() {
    return "[compile|clean|test|package]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
      System.out.println("""
          Usage: desk build [compile|clean|test|package]

          Detects the build system for the current workspace and orchestrates it.
          """);
      return CommandResult.success();
    }
    try {
      Path root = WorkspaceStore.current().map(Workspace::root)
          .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
      BuildRequest.Goal goal = BuildRequest.Goal.COMPILE;
      if (args.length > 0) {
        goal = switch (args[0].toLowerCase(Locale.ROOT)) {
          case "clean" -> BuildRequest.Goal.CLEAN;
          case "test" -> BuildRequest.Goal.TEST;
          case "package" -> BuildRequest.Goal.PACKAGE;
          case "compile", "build" -> BuildRequest.Goal.COMPILE;
          default -> BuildRequest.Goal.COMPILE;
        };
      }
      Path jdk = JdkSelector.current(root).map(j -> j.home()).orElse(null);
      BuildResult result = BuildService.execute(new BuildRequest(root, goal, jdk, false));
      System.out.print(result.output());
      if (!result.diagnostics().isEmpty()) {
        System.out.println("Diagnostics:");
        for (BuildDiagnostic d : result.diagnostics()) {
          System.out.println("  " + d.format());
        }
      }
      System.out.println("Build system: " + result.system()
          + "  exit=" + result.exitCode()
          + "  " + result.durationMs() + "ms");
      return result.success()
          ? CommandResult.success()
          : CommandResult.failure(result.exitCode() == 0 ? 1 : result.exitCode(), "Build failed");
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
