package desk.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JDK discovery and selection.
 */
class JdkDiscoveryTest {

  @Test
  @DisplayName("detect returns installations including current JVM")
  void detectReturnsInstallations() {
    List<JdkInstallation> installs = JdkDiscovery.detect();
    assertNotNull(installs);
    assertFalse(installs.isEmpty(), "Expected at least the current JVM");
    assertTrue(installs.stream().anyMatch(JdkInstallation::valid));
  }

  @Test
  @DisplayName("discover returns JDK homes with javac")
  void discoverReturnsList() {
    List<Path> jdks = JdkDiscovery.discover();
    assertNotNull(jdks);
    for (Path jdk : jdks) {
      assertTrue(Files.isDirectory(jdk));
      assertTrue(Files.isRegularFile(jdk.resolve("bin").resolve(
          JdkDiscovery.isWindows() ? "javac.exe" : "javac")));
    }
  }

  @Test
  @DisplayName("preferredJdk returns first usable JDK or null")
  void preferredJdk() {
    Path jdk = JdkDiscovery.preferredJdk();
    if (jdk != null) {
      assertTrue(Files.isDirectory(jdk));
    }
  }

  @Test
  @DisplayName("inspect probes java.home")
  void inspectCurrent() {
    Path home = Path.of(System.getProperty("java.home"));
    JdkInstallation install = JdkDiscovery.inspect(home).orElseThrow();
    assertTrue(install.valid() || install.javaExecutable().isPresent());
    assertTrue(install.version().isPresent());
  }

  @Test
  @DisplayName("findByFeature locates matching JDK when present")
  void findByFeature() {
    int feature = JdkVersion.parse(System.getProperty("java.version"))
        .map(JdkVersion::feature)
        .orElse(25);
    // May find JDK or JRE-in-jdk; assert no crash
    JdkDiscovery.findByFeature(feature);
  }
}
