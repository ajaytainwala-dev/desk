package devkit.core.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Read-only application configuration.
 *
 * <p>Configuration values are looked up by key. The implementation is
 * immutable once built, which makes it safe to share across components and
 * threads.</p>
 */
public interface Config {

  /**
   * Returns whether a value is present for the given key.
   */
  boolean has(String key);

  /**
   * Returns the string value for a key, or {@code null} if absent.
   */
  String getString(String key);

  /**
   * Returns the string value for a key, or the default if absent.
   */
  String getString(String key, String defaultValue);

  /**
   * Returns the integer value for a key, or {@code null} if absent or not a
   * valid integer.
   */
  Integer getInt(String key);

  /**
   * Returns the integer value for a key, or the default if absent or invalid.
   */
  int getInt(String key, int defaultValue);

  /**
   * Returns the boolean value for a key, or {@code null} if absent.
   * Accepts "true"/"false" (case-insensitive) and "1"/"0".
   */
  Boolean getBoolean(String key);

  /**
   * Returns the boolean value for a key, or the default if absent or invalid.
   */
  boolean getBoolean(String key, boolean defaultValue);

  /**
   * Returns the raw value for a key, or {@code null} if absent.
   */
  Object get(String key);

  /**
   * Returns all configured keys.
   */
  Set<String> keySet();

  /**
   * Returns an unmodifiable view of all key/value pairs.
   */
  Map<String, Object> asMap();

  /**
   * Empty configuration (useful when no config file is present).
   */
  Config EMPTY = new DefaultConfig(Collections.emptyMap());
}
