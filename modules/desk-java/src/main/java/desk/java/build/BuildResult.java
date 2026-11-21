package desk.java.build;

import java.util.List;

public final class BuildResult {

  private final boolean success;
  private final int exitCode;
  private final BuildSystem system;
  private final String output;
  private final List<BuildDiagnostic> diagnostics;
  private final long durationMs;

  public BuildResult(
      boolean success,
      int exitCode,
      BuildSystem system,
      String output,
      List<BuildDiagnostic> diagnostics,
      long durationMs) {
    this.success = success;
    this.exitCode = exitCode;
    this.system = system;
    this.output = output != null ? output : "";
    this.diagnostics = List.copyOf(diagnostics != null ? diagnostics : List.of());
    this.durationMs = durationMs;
  }

  public boolean success() {
    return success;
  }

  public int exitCode() {
    return exitCode;
  }

  public BuildSystem system() {
    return system;
  }

  public String output() {
    return output;
  }

  public List<BuildDiagnostic> diagnostics() {
    return diagnostics;
  }

  public long durationMs() {
    return durationMs;
  }
}
