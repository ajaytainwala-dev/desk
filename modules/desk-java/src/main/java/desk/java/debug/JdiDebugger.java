package desk.java.debug;

import desk.java.JavaException;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real Java debugger backed by JDK {@code jdk.jdi} (JDWP).
 * This is not a mock — it launches a JVM and stops on line breakpoints.
 */
public final class JdiDebugger implements AutoCloseable {

  public record Locals(String name, String value, String type) {
  }

  public record Frame(int id, String method, String source, int line) {
  }

  private final Path workspaceRoot;
  private Process debuggee;
  private VirtualMachine vm;
  private ThreadReference suspendedThread;
  private final Map<String, Integer> pendingBreakpoints = new LinkedHashMap<>();
  private final List<BreakpointRequest> activeBreakpoints = new ArrayList<>();
  private final AtomicReference<String> stderrTail = new AtomicReference<>("");
  private volatile boolean connected;

  public JdiDebugger(Path workspaceRoot) {
    this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
  }

  public boolean isConnected() {
    return connected && vm != null;
  }

  public String stderrTail() {
    return stderrTail.get();
  }

  /**
   * Launch {@code mainClass} with JDWP and connect via JDI listening connector.
   */
  public void launch(Path javaHome, Path classpath, String mainClass, List<String> programArgs)
      throws IOException, InterruptedException {
    Objects.requireNonNull(javaHome, "javaHome");
    Objects.requireNonNull(classpath, "classpath");
    Objects.requireNonNull(mainClass, "mainClass");
    if (connected) {
      throw new JavaException("Debugger already connected");
    }

    ListeningConnector listener = findListenConnector();
    Map<String, Connector.Argument> args = listener.defaultArguments();
    args.get("timeout").setValue("20000");
    String address;
    try {
      address = listener.startListening(args);
    } catch (IllegalConnectorArgumentsException e) {
      throw new JavaException("Cannot start JDWP listener: " + e.getMessage(), e);
    }

    Path javaBin = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
    if (!Files.isRegularFile(javaBin)) {
      throw new JavaException("java launcher missing: " + javaBin);
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin.toAbsolutePath().toString());
    cmd.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address);
    cmd.add("-cp");
    cmd.add(classpath.toAbsolutePath().toString());
    cmd.add(mainClass);
    if (programArgs != null) {
      cmd.addAll(programArgs);
    }

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(workspaceRoot.toFile());
    pb.redirectError(ProcessBuilder.Redirect.PIPE);
    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
    debuggee = pb.start();
    drainAsync(debuggee.getErrorStream(), true);
    drainAsync(debuggee.getInputStream(), false);

    try {
      vm = listener.accept(args);
      listener.stopListening(args);
    } catch (Exception e) {
      destroyQuiet(debuggee);
      throw new JavaException("JDWP accept failed: " + e.getMessage()
          + " stderr=" + stderrTail.get(), e);
    }
    connected = true;
    EventRequestManager erm = vm.eventRequestManager();
    ClassPrepareRequest cpr = erm.createClassPrepareRequest();
    cpr.setSuspendPolicy(ClassPrepareRequest.SUSPEND_ALL);
    cpr.enable();

    // Consume VMStart
    waitForVmStart(5_000);
  }

  public void setBreakpoint(String className, int line) {
    requireVm();
    pendingBreakpoints.put(className, line);
    for (ReferenceType type : vm.classesByName(className)) {
      installBreakpoint(type, line);
    }
  }

  /**
   * Resume and wait until a breakpoint hits or the VM ends.
   *
   * @return {@code true} if stopped at a breakpoint
   */
  public boolean continueUntilBreakpoint(long timeoutMs) throws InterruptedException {
    requireVm();
    suspendedThread = null;
    vm.resume();
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      long remaining = Math.max(1, deadline - System.currentTimeMillis());
      EventSet events;
      try {
        events = vm.eventQueue().remove(remaining);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
      if (events == null) {
        continue;
      }
      boolean hit = false;
      for (Event event : events) {
        if (event instanceof BreakpointEvent bp) {
          suspendedThread = bp.thread();
          hit = true;
        } else if (event instanceof ClassPrepareEvent cpe) {
          String name = cpe.referenceType().name();
          Integer line = pendingBreakpoints.get(name);
          if (line != null) {
            installBreakpoint(cpe.referenceType(), line);
          }
        } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
          connected = false;
          events.resume();
          return false;
        } else if (event instanceof VMStartEvent) {
          // ignore
        }
      }
      if (hit) {
        // leave VM suspended
        return true;
      }
      events.resume();
    }
    return false;
  }

  public List<Frame> stackTrace() {
    requireVm();
    ThreadReference thread = suspendedThread != null ? suspendedThread : pickSuspendedThread();
    if (thread == null) {
      return List.of();
    }
    try {
      List<Frame> frames = new ArrayList<>();
      int id = 0;
      for (StackFrame frame : thread.frames()) {
        Location loc = frame.location();
        Method method = loc.method();
        String source = loc.sourcePath();
        frames.add(new Frame(id++, method == null ? "?" : method.name(),
            source == null ? "?" : source, loc.lineNumber()));
      }
      return frames;
    } catch (IncompatibleThreadStateException e) {
      throw new JavaException("Thread not suspended for stackTrace", e);
    } catch (Exception e) {
      throw new JavaException("stackTrace failed: " + e.getMessage(), e);
    }
  }

  public List<Locals> locals() {
    requireVm();
    ThreadReference thread = suspendedThread != null ? suspendedThread : pickSuspendedThread();
    if (thread == null) {
      return List.of();
    }
    try {
      StackFrame frame = thread.frame(0);
      List<Locals> out = new ArrayList<>();
      for (LocalVariable var : frame.visibleVariables()) {
        Value value = frame.getValue(var);
        out.add(new Locals(var.name(),
            value == null ? "null" : value.toString(),
            var.typeName()));
      }
      return out;
    } catch (Exception e) {
      throw new JavaException("locals failed: " + e.getMessage(), e);
    }
  }

  public void stepOver() throws InterruptedException {
    step(StepRequest.STEP_OVER);
  }

  public void stepInto() throws InterruptedException {
    step(StepRequest.STEP_INTO);
  }

  public void stepOut() throws InterruptedException {
    step(StepRequest.STEP_OUT);
  }

  public void continueExecution() {
    requireVm();
    suspendedThread = null;
    vm.resume();
  }

  public void terminate() {
    if (vm != null) {
      try {
        vm.exit(0);
      } catch (Exception ignored) {
        try {
          vm.dispose();
        } catch (Exception ignored2) {
          // ignore
        }
      }
    }
    destroyQuiet(debuggee);
    connected = false;
  }

  private void step(int depth) throws InterruptedException {
    requireVm();
    ThreadReference thread = suspendedThread != null ? suspendedThread : pickSuspendedThread();
    if (thread == null) {
      throw new JavaException("No suspended thread to step");
    }
    EventRequestManager erm = vm.eventRequestManager();
    for (StepRequest existing : new ArrayList<>(erm.stepRequests())) {
      erm.deleteEventRequest(existing);
    }
    StepRequest step = erm.createStepRequest(thread, StepRequest.STEP_LINE, depth);
    step.addCountFilter(1);
    step.enable();
    suspendedThread = null;
    vm.resume();
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      EventSet events = vm.eventQueue().remove(deadline - System.currentTimeMillis());
      if (events == null) {
        continue;
      }
      boolean stopped = false;
      for (Event event : events) {
        if (event instanceof com.sun.jdi.event.StepEvent se) {
          suspendedThread = se.thread();
          stopped = true;
        } else if (event instanceof BreakpointEvent bp) {
          suspendedThread = bp.thread();
          stopped = true;
        } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
          connected = false;
        }
      }
      if (stopped) {
        return;
      }
      events.resume();
    }
    throw new JavaException("Step timed out");
  }

  private void installBreakpoint(ReferenceType type, int line) {
    try {
      List<Location> locations = type.locationsOfLine(line);
      if (locations.isEmpty()) {
        return;
      }
      BreakpointRequest req = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
      req.setSuspendPolicy(BreakpointRequest.SUSPEND_ALL);
      req.enable();
      activeBreakpoints.add(req);
    } catch (Exception e) {
      throw new JavaException("Cannot set breakpoint " + type.name() + ":" + line
          + " — " + e.getMessage(), e);
    }
  }

  private void waitForVmStart(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      EventSet events = vm.eventQueue().remove(deadline - System.currentTimeMillis());
      if (events == null) {
        continue;
      }
      boolean started = false;
      for (Event event : events) {
        if (event instanceof VMStartEvent) {
          started = true;
        }
      }
      events.resume();
      if (started) {
        return;
      }
    }
  }

  private ThreadReference pickSuspendedThread() {
    for (ThreadReference t : vm.allThreads()) {
      try {
        if (t.isSuspended()) {
          return t;
        }
      } catch (Exception ignored) {
        // ignore
      }
    }
    return null;
  }

  private ListeningConnector findListenConnector() {
    for (ListeningConnector c : Bootstrap.virtualMachineManager().listeningConnectors()) {
      if (c.name().contains("SocketListen") || c.transport().name().equals("dt_socket")) {
        return c;
      }
    }
    throw new JavaException("No JDWP SocketListen connector available (jdk.jdi)");
  }

  private void requireVm() {
    if (!connected || vm == null) {
      throw new JavaException("Debugger not connected");
    }
  }

  private void drainAsync(InputStream in, boolean err) {
    Thread t = new Thread(() -> {
      try {
        byte[] buf = new byte[4096];
        int n;
        StringBuilder acc = new StringBuilder();
        while ((n = in.read(buf)) >= 0) {
          String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
          if (err) {
            acc.append(chunk);
            if (acc.length() > 32_000) {
              acc.delete(0, acc.length() - 16_000);
            }
            stderrTail.set(acc.toString());
          }
        }
      } catch (Exception ignored) {
        // ignore
      }
    }, err ? "desk-jdi-err" : "desk-jdi-out");
    t.setDaemon(true);
    t.start();
  }

  private static void destroyQuiet(Process p) {
    if (p == null) {
      return;
    }
    p.destroy();
    try {
      if (!p.waitFor(2, TimeUnit.SECONDS)) {
        p.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      p.destroyForcibly();
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  @Override
  public void close() {
    terminate();
  }
}
