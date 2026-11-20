package desk.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in default configuration values. The application must never require
 * a configuration file to exist; these defaults guarantee sensible behavior.
 */
public final class Defaults {

  private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

  static {
    DEFAULTS.put("app.name", "desk");
    DEFAULTS.put("app.version", "0.1.0");
    DEFAULTS.put("editor.indentSize", "4");
    DEFAULTS.put("editor.indentStyle", "spaces");
    DEFAULTS.put("editor.tabSize", "4");
    DEFAULTS.put("editor.maxLineLength", "120");
    DEFAULTS.put("editor.braceStyle", "same-line");
    DEFAULTS.put("editor.finalNewline", "true");
    DEFAULTS.put("editor.removeTrailingWhitespace", "true");
    DEFAULTS.put("editor.sortImports", "false");
    DEFAULTS.put("editor.blankLinesBetweenMethods", "1");
    DEFAULTS.put("formatter.dryRun", "false");
    DEFAULTS.put("formatter.recursive", "false");
    DEFAULTS.put("logging.level", "info");
    DEFAULTS.put("theme", "dark");
    DEFAULTS.put("portable", "false");
  }

  private Defaults() {
    // utility class
  }

  public static Config config() {
    return new DefaultConfig(DEFAULTS);
  }
}
