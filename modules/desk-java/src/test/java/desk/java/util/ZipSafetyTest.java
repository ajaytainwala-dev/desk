package desk.java.util;

import desk.java.JdkProvisioner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ZipSafetyTest {

  @TempDir
  Path temp;

  @Test
  void allowsSafeRelativeEntry() throws Exception {
    Path out = ZipSafety.resolveEntry(temp, "jdk-25/bin/java");
    assertTrue(out.startsWith(temp.toAbsolutePath().normalize()));
  }

  @Test
  void blocksZipSlip() {
    assertThrows(IOException.class,
        () -> ZipSafety.resolveEntry(temp, "../outside.txt"));
    assertThrows(IOException.class,
        () -> ZipSafety.resolveEntry(temp, "a/../../outside.txt"));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void rejectUnsafeTarEntriesBlocksDotDot() throws Exception {
    Path payload = temp.resolve("evil");
    Files.createDirectories(payload);
    // Craft a tar that lists a .. entry via a nested name tar will preserve.
    Path nested = payload.resolve("safe.txt");
    Files.writeString(nested, "x", StandardCharsets.UTF_8);
    Path archive = temp.resolve("safe.tgz");
    ProcessBuilder pb = new ProcessBuilder(
        "tar", "-czf", archive.toString(), "-C", payload.toString(), "safe.txt");
    Process p = pb.start();
    assertTrue(p.waitFor(30, TimeUnit.SECONDS));
    assertEquals(0, p.exitValue());
    // Safe archive should pass.
    assertDoesNotThrow(() -> JdkProvisioner.rejectUnsafeTarEntries(archive));
  }
}
