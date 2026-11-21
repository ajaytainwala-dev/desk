package desk.java.debug;

import desk.java.JavaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Debug Adapter Protocol session.
 *
 * <p>Transport and {@code initialize} handshake are real when
 * {@code DESK_JAVA_DAP} points at an adapter. Higher-level operations remain
 * unimplemented until end-to-end validated — they throw rather than fake success.</p>
 *
 * @see docs/debug/DAP_DESIGN.md
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
  private Process adapterProcess;
  private DapTransport transport;
  private volatile boolean initialized;
  private volatile boolean attached;
  private Map<String, Object> capabilities = Map.of();

  public DapSession(Path workspaceRoot) {
    this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
  }

  public Path workspaceRoot() {
    return workspaceRoot;
  }

  public boolean isAttached() {
    return attached;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public Map<String, Object> capabilities() {
    return capabilities;
  }

  /**
   * Discover a Java debug adapter executable ({@code DESK_JAVA_DAP} or PATH).
   */
  public static Optional<Path> discoverAdapter() {
    String env = System.getenv("DESK_JAVA_DAP");
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      if (Files.isRegularFile(p) || Files.isExecutable(p)) {
        return Optional.of(p.toAbsolutePath().normalize());
      }
    }
    return Optional.empty();
  }

  /**
   * Spawns the adapter and performs the DAP {@code initialize} request.
   * Does not launch the debuggee — that remains a later milestone.
   */
  public Map<String, Object> connectAndInitialize(Path adapterExecutable) throws IOException {
    Objects.requireNonNull(adapterExecutable, "adapterExecutable");
    if (!Files.isRegularFile(adapterExecutable) && !Files.isExecutable(adapterExecutable)) {
      throw new JavaException("DAP adapter not found: " + adapterExecutable);
    }
    ProcessBuilder pb = new ProcessBuilder(adapterExecutable.toString());
    pb.directory(workspaceRoot.toFile());
    pb.redirectError(ProcessBuilder.Redirect.PIPE);
    adapterProcess = pb.start();
    transport = new DapTransport(adapterProcess.getInputStream(), adapterProcess.getOutputStream());

    Map<String, Object> args = new LinkedHashMap<>();
    args.put("clientID", "desk");
    args.put("clientName", "Desk");
    args.put("adapterID", "java");
    args.put("pathFormat", "path");
    args.put("linesStartAt1", true);
    args.put("columnsStartAt1", true);
    args.put("supportsVariableType", true);
    args.put("supportsVariablePaging", false);
    Map<String, Object> response = transport.request("initialize", args);
    Object body = response.get("body");
    if (body instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> caps = (Map<String, Object>) map;
      capabilities = caps;
    }
    Object success = response.get("success");
    if (Boolean.FALSE.equals(success)) {
      throw new JavaException("DAP initialize failed: " + response.get("message"));
    }
    // Client sends initialized request after initialize response per DAP.
    transport.request("initialized", Map.of());
    initialized = true;
    attached = true;
    return capabilities;
  }

  /**
   * Test/helper: attach an already-open transport (e.g. mock adapter pipes).
   */
  public Map<String, Object> initializeWithTransport(DapTransport transport) throws IOException {
    this.transport = Objects.requireNonNull(transport, "transport");
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("clientID", "desk");
    args.put("clientName", "Desk");
    args.put("adapterID", "java");
    args.put("pathFormat", "path");
    args.put("linesStartAt1", true);
    args.put("columnsStartAt1", true);
    Map<String, Object> response = transport.request("initialize", args);
    Object body = response.get("body");
    if (body instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> caps = (Map<String, Object>) map;
      capabilities = caps;
    }
    transport.request("initialized", Map.of());
    initialized = true;
    attached = true;
    return capabilities;
  }

  public void launch(LaunchConfig config) {
    requireInitialized();
    throw new UnsupportedOperationException(
        "DAP launch/debuggee not end-to-end validated yet. Transport/initialize works; "
            + "see docs/debug/DAP_DESIGN.md. Adapter caps: " + capabilities.keySet());
  }

  public void setBreakpoint(Breakpoint bp) {
    requireInitialized();
    throw new UnsupportedOperationException(
        "DAP breakpoints not end-to-end validated yet (adapter may support them)");
  }

  public void continueExecution() {
    requireInitialized();
    throw new UnsupportedOperationException("DAP continue not end-to-end validated yet");
  }

  public void stepOver() {
    requireInitialized();
    throw new UnsupportedOperationException("DAP stepOver not end-to-end validated yet");
  }

  public void stepIn() {
    requireInitialized();
    throw new UnsupportedOperationException("DAP stepIn not end-to-end validated yet");
  }

  public void stepOut() {
    requireInitialized();
    throw new UnsupportedOperationException("DAP stepOut not end-to-end validated yet");
  }

  public List<StackFrame> stackTrace() {
    requireInitialized();
    throw new UnsupportedOperationException("DAP stackTrace not end-to-end validated yet");
  }

  public List<Variable> variables(int frameId) {
    requireInitialized();
    throw new UnsupportedOperationException("DAP variables not end-to-end validated yet");
  }

  private void requireInitialized() {
    if (!initialized || transport == null) {
      throw new UnsupportedOperationException(
          "DAP session not initialized. Connect an adapter via DESK_JAVA_DAP first. "
              + "See docs/debug/DAP_DESIGN.md");
    }
  }

  @Override
  public void close() {
    attached = false;
    initialized = false;
    if (transport != null) {
      try {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("restart", false);
        transport.request("disconnect", args);
      } catch (Exception ignored) {
        // ignore
      }
      try {
        transport.close();
      } catch (Exception ignored) {
        // ignore
      }
      transport = null;
    }
    if (adapterProcess != null) {
      adapterProcess.destroy();
      try {
        if (!adapterProcess.waitFor(2, TimeUnit.SECONDS)) {
          adapterProcess.destroyForcibly();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        adapterProcess.destroyForcibly();
      }
      adapterProcess = null;
    }
  }
}
