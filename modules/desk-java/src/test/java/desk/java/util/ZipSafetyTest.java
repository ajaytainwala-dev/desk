package desk.java.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

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
}
