package desk.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdkVersionTest {

  @Test
  @DisplayName("parses modern and legacy version strings")
  void parsesVersions() {
    assertEquals(25, JdkVersion.parseVersionString("25.0.5.1").orElseThrow().feature());
    assertEquals(21, JdkVersion.parseVersionString("21.0.3").orElseThrow().feature());
    assertEquals(8, JdkVersion.parseVersionString("1.8.0_412").orElseThrow().feature());
  }

  @Test
  @DisplayName("parses java -version output")
  void parsesJavaVersionOutput() {
    String output = "openjdk version \"25.0.5.1\" 2026-11-17 LTS\n"
        + "OpenJDK Runtime Environment Temurin-25.0.5.1+2 (build 25.0.5.1+2-LTS)\n";
    JdkVersion v = JdkVersion.parse(output).orElseThrow();
    assertEquals(25, v.feature());
    assertTrue(v.isLts());
  }

  @Test
  @DisplayName("LTS policy does not mark 26/27 as LTS")
  void ltsPolicy() {
    assertTrue(new JdkVersion(25, 0, 0, 0, "25").isLts());
    assertFalse(new JdkVersion(26, 0, 0, 0, "26").isLts());
    assertFalse(new JdkVersion(27, 0, 0, 0, "27").isLts());
    assertTrue(JavaVersionPolicy.isLts(25));
    assertFalse(JavaVersionPolicy.isLts(26));
    assertEquals(25, JavaVersionPolicy.PRIMARY_LTS);
  }
}
