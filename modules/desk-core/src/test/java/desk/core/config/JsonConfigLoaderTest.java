package desk.core.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigLoaderTest {

  @Test
  void loadMissingFileReturnsEmpty() {
    Config config = JsonConfigLoader.load(Path.of("does-not-exist.json"));
    assertSame(Config.EMPTY, config);
  }

  @Test
  void loadParsesSimpleObject() throws Exception {
    Path file = Files.createTempFile("config", ".json");
    try {
      Files.writeString(file, "{\"name\": \"desk\", \"port\": 8080, \"enabled\": true}");
      Config config = JsonConfigLoader.load(file);
      assertEquals("desk", config.getString("name"));
      assertEquals(8080, config.getInt("port"));
      assertTrue(config.getBoolean("enabled"));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void loadEmptyFileReturnsEmpty() throws Exception {
    Path file = Files.createTempFile("empty", ".json");
    try {
      Files.writeString(file, "");
      assertSame(Config.EMPTY, JsonConfigLoader.load(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void loadMalformedThrows() throws Exception {
    Path file = Files.createTempFile("bad", ".json");
    try {
      Files.writeString(file, "{not valid json}");
      assertThrows(ConfigException.class, () -> JsonConfigLoader.load(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void unclosedStringThrows() throws Exception {
    Path file = Files.createTempFile("bad2", ".json");
    try {
      Files.writeString(file, "{\"key\": \"unclosed");
      assertThrows(ConfigException.class, () -> JsonConfigLoader.load(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }
}
