package devkit.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JdkDiscovery.
 */
class JdkDiscoveryTest {

  @Test
  @DisplayName("discover returns a list (may be empty if no JDK)")
  void discoverReturnsList() {
    List<Path> jdks = JdkDiscovery.discover();
    assertNotNull(jdks);
  }

  @Test
  @DisplayName("preferredJdk returns first JDK or null")
  void preferredJdk() {
    Path jdk = JdkDiscovery.preferredJdk();
    // May be null if no JDK installed; just verify no crash.
    if (jdk != null) {
      assertTrue(java.nio.file.Files.isDirectory(jdk));
    }
  }
}
