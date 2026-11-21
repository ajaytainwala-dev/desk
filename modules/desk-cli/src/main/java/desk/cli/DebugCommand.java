package desk.cli;

import desk.java.JdkDiscovery;
import desk.java.debug.DapSession;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code desk debug run} — real JDWP/JDI debugging (not a stub UI).
 *
 * <pre>
 * desk debug run --main DebugDemo --bp 5 [--classpath dir] [--src File.java]
 * </pre>
 */
public final class DebugCommand implements Command {

  @Override
  public String name() {
    return "debug";
  }

  @Override
  public String description() {
    return "Debug a Java main class with JDWP/JDI (breakpoints, locals, continue)";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
      System.out.println("""
          Usage:
            desk debug run --main <Class> --bp <line> [--src File.java] [--classpath dir]
          
          Backend: JDK jdk.jdi (real JVM). External DESK_JAVA_DAP adapters are discoverable
          but launch is JDI-first until a third-party adapter is end-to-end validated.
          """);
      return CommandResult.success();
    }
    if (!"run".equals(args[0])) {
      return CommandResult.failure(1, "Unknown debug subcommand. Try: desk debug run …");
    }
    String main = null;
    int bp = -1;
    Path src = null;
    Path classpath = Path.of("").toAbsolutePath().normalize();
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--main" -> main = args[++i];
        case "--bp" -> bp = Integer.parseInt(args[++i]);
        case "--src" -> src = Path.of(args[++i]);
        case "--classpath" -> classpath = Path.of(args[++i]).toAbsolutePath().normalize();
        default -> {
          return CommandResult.failure(1, "Unknown flag: " + args[i]);
        }
      }
    }
    if (main == null || bp < 1) {
      return CommandResult.failure(1, "--main and --bp <line> are required");
    }
    try {
      if (src != null) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
          return CommandResult.failure(1, "No system JavaCompiler (need JDK)");
        }
        int code = compiler.run(null, null, null, "-g", "-d", classpath.toString(), src.toString());
        if (code != 0) {
          return CommandResult.failure(1, "Compile failed");
        }
      }
      Path jdk = JdkDiscovery.preferredJdk();
      if (jdk == null) {
        return CommandResult.failure(1, "No JDK found");
      }
      try (DapSession session = new DapSession(classpath)) {
        session.initializeJdi();
        session.launch(new DapSession.LaunchConfig(
            "cli", classpath, main, List.of(), List.of(), jdk, classpath));
        session.setBreakpoint(main, bp);
        System.out.println("Waiting for breakpoint " + main + ":" + bp + " …");
        if (!session.continueUntilBreakpoint(30_000)) {
          return CommandResult.failure(1, "Breakpoint not hit (timeout or VM exited)");
        }
        System.out.println("Stopped. Stack:");
        for (DapSession.StackFrame f : session.stackTrace()) {
          System.out.println("  #" + f.id() + " " + f.name() + " at " + f.file() + ":" + f.line());
        }
        System.out.println("Locals:");
        for (DapSession.Variable v : session.variables(0)) {
          System.out.println("  " + v.type() + " " + v.name() + " = " + v.value());
        }
        session.continueExecution();
        System.out.println("Continued.");
      }
      return CommandResult.success();
    } catch (Exception e) {
      return CommandResult.failure(1, "debug failed: " + e.getMessage());
    }
  }
}
