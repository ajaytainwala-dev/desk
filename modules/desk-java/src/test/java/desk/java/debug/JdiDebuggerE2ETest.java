package desk.java.debug;

import desk.java.JdkDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end JDWP/JDI debugging — launches a real JVM, hits a breakpoint,
 * reads locals, continues. Not a mock adapter.
 */
class JdiDebuggerE2ETest {

  @TempDir
  Path temp;

  @Test
  void breakpointLocalsAndContinue() throws Exception {
    Path src = temp.resolve("DebugDemo.java");
    Files.writeString(src, """
        public class DebugDemo {
          public static void main(String[] args) {
            int first = 10;
            int second = 20;
            int result = first + second;
            System.out.println(result);
          }
        }
        """);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler required");
    // -g required so JDI can read local variable names/types
    int code = compiler.run(null, null, null, "-g", src.toString());
    assertEquals(0, code, "compile failed");
    assertTrue(Files.isRegularFile(temp.resolve("DebugDemo.class")));

    Path jdk = JdkDiscovery.preferredJdk();
    assertNotNull(jdk);

    try (DapSession session = new DapSession(temp)) {
      session.initializeJdi();
      assertEquals("jdi", session.backend());
      session.launch(new DapSession.LaunchConfig(
          "debug-demo", temp, "DebugDemo", List.of(), List.of(), jdk, temp));
      // Line of: int result = first + second;
      session.setBreakpoint("DebugDemo", 5);
      assertTrue(session.continueUntilBreakpoint(TimeUnit.SECONDS.toMillis(20)),
          "expected breakpoint hit");

      List<DapSession.StackFrame> stack = session.stackTrace();
      assertFalse(stack.isEmpty());
      assertEquals("main", stack.get(0).name());

      List<DapSession.Variable> vars = session.variables(0);
      assertTrue(vars.stream().anyMatch(v -> "first".equals(v.name()) && v.value().contains("10")),
          () -> "locals=" + vars);
      assertTrue(vars.stream().anyMatch(v -> "second".equals(v.name()) && v.value().contains("20")),
          () -> "locals=" + vars);

      session.continueExecution();
      // Allow process to finish
      Thread.sleep(500);
    }
  }
}
