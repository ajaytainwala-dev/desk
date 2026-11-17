package devkit.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CompileResult and RunResult value types.
 */
class ResultTypesTest {

  @Test
  @DisplayName("CompileResult reflects success and failure")
  void compileResult() {
    Path out = Paths.get("devkit-out");
    CompileResult success = new CompileResult(true, "", "", out);
    assertTrue(success.success());
    assertEquals("", success.output());
    assertEquals("", success.errorOutput());
    assertEquals(out, success.outputDirectory());

    CompileResult failure = new CompileResult(false, "", "error: x", out);
    assertFalse(failure.success());
    assertEquals("error: x", failure.errorOutput());
  }

  @Test
  @DisplayName("RunResult reflects exit code and output")
  void runResult() {
    RunResult success = new RunResult(0, "hello\n", "");
    assertTrue(success.success());
    assertEquals(0, success.exitCode());
    assertEquals("hello\n", success.output());

    RunResult failure = new RunResult(1, "", "error\n");
    assertFalse(failure.success());
    assertEquals(1, failure.exitCode());
    assertEquals("error\n", failure.errorOutput());
  }
}
