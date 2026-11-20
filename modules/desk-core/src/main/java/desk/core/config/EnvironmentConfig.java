package desk.core.config;

import java.util.Map;

/**
 * Reads configuration overrides from environment variables prefixed with
 * {@code DEVKIT_}.
 *
 * <p>For example {@code DEVKIT_EDITOR_INDENT_SIZE=2} overrides the key
 * {@code editor.indentSize}.</p>
 */
public final class EnvironmentConfig {

  private static final String PREFIX = "DEVKIT_";

  private EnvironmentConfig() {
    // utility class
  }

  public static Config fromEnvironment() {
    Map<String, String> env = System.getenv();
    Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, String> entry : env.entrySet()) {
      String name = entry.getKey();
      if (name.startsWith(PREFIX)) {
        String key = name.substring(PREFIX.length())
            .replace('_', '.')
            .toLowerCase(java.util.Locale.ROOT);
        values.put(key, entry.getValue());
      }
    }
    return new DefaultConfig(values);
  }
}
