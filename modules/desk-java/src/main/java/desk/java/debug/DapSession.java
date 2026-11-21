package desk.java.debug;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Debug Adapter Protocol session facade (Phase 4 foundation).
 *
 * <p>This is an <strong>architecture stub</strong> for a future Java Debug
 * Server / JDWP-backed adapter. Methods throw {@link UnsupportedOperationException}
 * until a real adapter process is wired — never fake success.</p>
 */
public final class DapSession implements AutoCloseable {

  public record LaunchConfig(
      String name,
      Path cwd,
      String mainClass,
      List<String> args,
      List<String> vmArgs,
      Path projectJdkHome) {
  }

  public record Breakpoint(Path file, int line, boolean enabled) {
  }

  public record StackFrame(int id, String name, Path file, int line) {
  }

  public record Variable(String name, String value, String type) {
  }

  private final Path workspaceRoot;
  private volatile boolean attached;

  public DapSession(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public Path workspaceRoot() {
    return workspaceRoot;
  }

  public boolean isAttached() {
    return attached;
  }

  /**
   * Discover a Java debug adapter executable ({@code DESK_JAVA_DAP} or PATH).
   */
  public static Optional<Path> discoverAdapter() {
    String env = System.getenv("DESK_JAVA_DAP");
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      if (java.nio.file.Files.isRegularFile(p)) {
        return Optional.of(p.toAbsolutePath().normalize());
      }
    }
    return Optional.empty();
  }

  public void launch(LaunchConfig config) {
    throw new UnsupportedOperationException(
        "DAP launch not implemented yet. Set DESK_JAVA_DAP when a Java debug adapter is available. "
            + "See docs/debug/DAP_DESIGN.md");
  }

  public void setBreakpoint(Breakpoint bp) {
    throw new UnsupportedOperationException("DAP breakpoints not implemented yet");
  }

  public void continueExecution() {
    throw new UnsupportedOperationException("DAP continue not implemented yet");
  }

  public void stepOver() {
    throw new UnsupportedOperationException("DAP stepOver not implemented yet");
  }

  public void stepIn() {
    throw new UnsupportedOperationException("DAP stepIn not implemented yet");
  }

  public void stepOut() {
    throw new UnsupportedOperationException("DAP stepOut not implemented yet");
  }

  public List<StackFrame> stackTrace() {
    throw new UnsupportedOperationException("DAP stackTrace not implemented yet");
  }

  public List<Variable> variables(int frameId) {
    throw new UnsupportedOperationException("DAP variables not implemented yet");
  }

  @Override
  public void close() {
    attached = false;
  }
}
