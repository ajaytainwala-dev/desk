package desk.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable in-memory {@link Config} backed by a map.
 */
public final class DefaultConfig implements Config {

  private final Map<String, Object> values;

  public DefaultConfig(Map<String, Object> values) {
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  @Override
  public boolean has(String key) {
    return values.containsKey(key);
  }

  @Override
  public String getString(String key) {
    Object v = values.get(key);
    return v == null ? null : String.valueOf(v);
  }

  @Override
  public String getString(String key, String defaultValue) {
    String v = getString(key);
    return v == null ? defaultValue : v;
  }

  @Override
  public Integer getInt(String key) {
    String v = getString(key);
    if (v == null) {
      return null;
    }
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public int getInt(String key, int defaultValue) {
    Integer v = getInt(key);
    return v == null ? defaultValue : v;
  }

  @Override
  public Boolean getBoolean(String key) {
    String v = getString(key);
    if (v == null) {
      return null;
    }
    String t = v.trim().toLowerCase();
    switch (t) {
      case "true":
      case "1":
      case "yes":
      case "on":
        return true;
      case "false":
      case "0":
      case "no":
      case "off":
        return false;
      default:
        return null;
    }
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    Boolean v = getBoolean(key);
    return v == null ? defaultValue : v;
  }

  @Override
  public Object get(String key) {
    return values.get(key);
  }

  @Override
  public Set<String> keySet() {
    return Collections.unmodifiableSet(values.keySet());
  }

  @Override
  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(values);
  }

  @Override
  public String toString() {
    return "DefaultConfig{" + values + "}";
  }
}
