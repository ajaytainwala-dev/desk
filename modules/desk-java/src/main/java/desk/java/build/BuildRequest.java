package desk.java.build;

import java.nio.file.Path;
import java.util.Objects;

public final class BuildRequest {

  public enum Goal {
    COMPILE,
    TEST,
    PACKAGE,
    CLEAN,
    RUN
  }

  private final Path workspaceRoot;
  private final Goal goal;
  private final Path projectJdkHome;
  private final boolean watch;

  public BuildRequest(Path workspaceRoot, Goal goal, Path projectJdkHome, boolean watch) {
    this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
    this.goal = Objects.requireNonNull(goal, "goal");
    this.projectJdkHome = projectJdkHome;
    this.watch = watch;
  }

  public Path workspaceRoot() {
    return workspaceRoot;
  }

  public Goal goal() {
    return goal;
  }

  public Path projectJdkHome() {
    return projectJdkHome;
  }

  public boolean watch() {
    return watch;
  }
}
