package desk.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessIoTest {

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void timesOutHungProcess() {
    ProcessIo.Result result = ProcessIo.run(
        List.of("sleep", "30"),
        null,
        Map.of(),
        1,
        TimeUnit.SECONDS);
    assertTrue(result.timedOut());
    assertEquals(124, result.exitCode());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void capturesStdout() {
    ProcessIo.Result result = ProcessIo.run(
        List.of("echo", "hello-desk"),
        null,
        Map.of(),
        5,
        TimeUnit.SECONDS);
    assertFalse(result.timedOut());
    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("hello-desk"));
  }
}
