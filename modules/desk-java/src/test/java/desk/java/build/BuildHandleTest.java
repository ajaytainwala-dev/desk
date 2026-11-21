package desk.java.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BuildHandleTest {

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void cancelStopsHungProcess() {
    BuildHandle handle = BuildHandle.start(
        List.of("sleep", "60"),
        null,
        Map.of(),
        BuildSystem.JAVAC);
    assertTrue(handle.isAlive());
    assertTrue(handle.cancel());
    BuildResult result = handle.await(5, TimeUnit.SECONDS);
    assertTrue(handle.isCancelled());
    assertFalse(result.success());
    assertEquals(130, result.exitCode());
  }
}
