package desk.core.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigTest {

  @Test
  void getStringReturnsValue() {
    Map<String, Object> values = java.util.Map.of("key", "value");
    Config config = new DefaultConfig(values);
    assertEquals("value", config.getString("key"));
  }

  @Test
  void getStringMissingReturnsDefault() {
    Config config = new DefaultConfig(java.util.Map.of());
    assertEquals("def", config.getString("missing", "def"));
  }

  @Test
  void getIntParsesValue() {
    Config config = new DefaultConfig(java.util.Map.of("port", "8080"));
    assertEquals(8080, config.getInt("port"));
  }

  @Test
  void getIntMissingReturnsDefault() {
    Config config = new DefaultConfig(java.util.Map.of());
    assertEquals(3000, config.getInt("port", 3000));
  }

  @Test
  void getIntInvalidReturnsDefault() {
    Config config = new DefaultConfig(java.util.Map.of("port", "notanumber"));
    assertEquals(9090, config.getInt("port", 9090));
  }

  @Test
  void getBooleanAcceptsVariants() {
    Config config = new DefaultConfig(java.util.Map.of(
        "a", "true", "b", "1", "c", "yes", "d", "on",
        "e", "false", "f", "0", "g", "no", "h", "off"));
    assertTrue(config.getBoolean("a"));
    assertTrue(config.getBoolean("b"));
    assertTrue(config.getBoolean("c"));
    assertTrue(config.getBoolean("d"));
    assertFalse(config.getBoolean("e"));
    assertFalse(config.getBoolean("f"));
    assertFalse(config.getBoolean("g"));
    assertFalse(config.getBoolean("h"));
  }

  @Test
  void getBooleanInvalidReturnsDefault() {
    Config config = new DefaultConfig(java.util.Map.of("flag", "maybe"));
    assertTrue(config.getBoolean("flag", true));
  }

  @Test
  void keySetAndAsMapAreUnmodifiable() {
    Config config = new DefaultConfig(java.util.Map.of("x", "1"));
    assertThrows(UnsupportedOperationException.class, () -> config.keySet().add("y"));
    assertThrows(UnsupportedOperationException.class, () -> config.asMap().put("y", "2"));
  }

  @Test
  void emptyConfigHasNoKeys() {
    assertTrue(Config.EMPTY.keySet().isEmpty());
    assertNull(Config.EMPTY.getString("anything"));
  }
}
