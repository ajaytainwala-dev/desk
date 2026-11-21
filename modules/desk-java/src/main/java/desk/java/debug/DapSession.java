package desk.java.debug;

import desk.java.JavaException;
import desk.java.JdkDiscovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Debug session facade.
 *
 * <p>Primary path: JDK {@link JdiDebugger} (real JDWP). Optional external
 * adapter via {@code DESK_JAVA_DAP} for {@code initialize} handshake only until
 * that adapter is end-to-end validated.</p>
 */
public final class DapSession implements AutoCloseable {

  public record LaunchConfig(
      String name,
      Path cwd,
      String mainClass,
      List<String> args,
      List<String> vmArgs,
      Path projectJdkHome,
      Path classpath) {
    public LaunchConfig(String name, Path cwd, String mainClass, List<String> args,
        List<String> vmArgs, Path projectJdkHome) {
      this(name, cwd, mainClass, args, vmArgs, projectJdkHome, cwd);
    }
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
  private JdiDebugger jdi;
  private volatile boolean initialized;
  private volatile boolean attached;
  private Map<String, Object> capabilities = Map.of();
  private String backend = "none";

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

  public String backend() {
    return backend;
  }

  public Map<String, Object> capabilities() {
    return capabilities;
  }

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

  /** Prefer JDI backend — always available with a JDK that has {@code jdk.jdi}. */
  public Map<String, Object> initializeJdi() {
    capabilities = Map.of(
        "supportsConfigurationDoneRequest", true,
        "supportsStepBack", false,
        "supportsRestartRequest", false,
        "supportsEvaluateExpression", false,
        "backend", "jdi");
    backend = "jdi";
    initialized = true;
    attached = false;
    return capabilities;
  }

  public Map<String, Object> connectAndInitialize(Path adapterExecutable) throws IOException {
    Objects.requireNonNull(adapterExecutable, "adapterExecutable");
    ProcessBuilder pb = new ProcessBuilder(adapterExecutable.toString());
    pb.directory(workspaceRoot.toFile());
    pb.redirectError(ProcessBuilder.Redirect.PIPE);
    adapterProcess = pb.start();
    transport = new DapTransport(adapterProcess.getInputStream(), adapterProcess.getOutputStream());
    return initializeWithTransport(transport);
  }

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
    backend = "external-dap";
    initialized = true;
    attached = true;
    return capabilities;
  }

  public void launch(LaunchConfig config) throws IOException, InterruptedException {
    if (!initialized) {
      initializeJdi();
    }
    if (!"jdi".equals(backend) && transport != null) {
      throw new UnsupportedOperationException(
          "External DAP adapter launch not end-to-end validated. Use JDI backend (default).");
    }
    Path jdk = config.projectJdkHome() != null ? config.projectJdkHome() : JdkDiscovery.preferredJdk();
    if (jdk == null) {
      throw new JavaException("No JDK available for debug launch");
    }
    Path cp = config.classpath() != null ? config.classpath() : config.cwd();
    jdi = new JdiDebugger(config.cwd() != null ? config.cwd() : workspaceRoot);
    jdi.launch(jdk, cp, config.mainClass(),
        config.args() != null ? config.args() : List.of());
    attached = true;
  }

  public void setBreakpoint(Breakpoint bp) {
    requireJdi();
    String className = classNameFromSource(bp.file());
    jdi.setBreakpoint(className, bp.line());
  }

  public void setBreakpoint(String className, int line) {
    requireJdi();
    jdi.setBreakpoint(className, line);
  }

  /** Resume until breakpoint or timeout. */
  public boolean continueUntilBreakpoint(long timeoutMs) throws InterruptedException {
    requireJdi();
    return jdi.continueUntilBreakpoint(timeoutMs);
  }

  public void continueExecution() {
    requireJdi();
    jdi.continueExecution();
  }

  public void stepOver() throws InterruptedException {
    requireJdi();
    jdi.stepOver();
  }

  public void stepIn() throws InterruptedException {
    requireJdi();
    jdi.stepInto();
  }

  public void stepOut() throws InterruptedException {
    requireJdi();
    jdi.stepOut();
  }

  public List<StackFrame> stackTrace() {
    requireJdi();
    List<StackFrame> out = new ArrayList<>();
    for (JdiDebugger.Frame f : jdi.stackTrace()) {
      out.add(new StackFrame(f.id(), f.method(), Path.of(f.source()), f.line()));
    }
    return out;
  }

  public List<Variable> variables(int frameId) {
    requireJdi();
    if (frameId != 0) {
      throw new UnsupportedOperationException("Only top frame (0) locals supported in JDI slice");
    }
    List<Variable> out = new ArrayList<>();
    for (JdiDebugger.Locals v : jdi.locals()) {
      out.add(new Variable(v.name(), v.value(), v.type()));
    }
    return out;
  }

  private void requireJdi() {
    if (jdi == null || !jdi.isConnected()) {
      throw new JavaException("JDI debug session not active — call launch() first");
    }
  }

  static String classNameFromSource(Path file) {
    String name = file.getFileName().toString();
    if (name.endsWith(".java")) {
      name = name.substring(0, name.length() - 5);
    }
    return name;
  }

  @Override
  public void close() {
    attached = false;
    initialized = false;
    if (jdi != null) {
      jdi.close();
      jdi = null;
    }
    if (transport != null) {
      try {
        transport.request("disconnect", Map.of("restart", false));
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
