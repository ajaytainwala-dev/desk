package devkit.core.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration source with an explicit precedence order:
 * <ol>
 *   <li>defaults (built-in)</li>
 *   <li>user config file</li>
 *   <li>project config file</li>
 *   <li>environment variable overrides</li>
 *   <li>command-line overrides</li>
 * </ol>
 * Later sources override earlier ones.
 */
public final class ConfigSources {

  private ConfigSources() {
    // utility class
  }

  /**
   * Merges multiple configs into one, with later configs overriding earlier
   * ones for duplicate keys.
   */
  public static Config merge(Config... configs) {
    java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
    for (Config config : configs) {
      for (String key : config.keySet()) {
        merged.put(key, config.get(key));
      }
    }
    return new DefaultConfig(merged);
  }

  /**
   * Loads the configuration for a given project directory, applying the
   * standard precedence order. Missing files are treated as empty configs.
   *
   * @param projectDir the project directory, or {@code null} to skip
   *        project-level configuration.
   */
  public static Config load(Path projectDir) {
    Config defaults = Defaults.config();
    Config user = JsonConfigLoader.load(userConfigFile());
    Config project = projectDir == null
        ? Config.EMPTY
        : JsonConfigLoader.load(projectDir.resolve("devkit.json"));
    Config env = EnvironmentConfig.fromEnvironment();
    return merge(defaults, user, project, env);
  }

  private static Path userConfigFile() {
    String home = System.getProperty("user.home");
    if (home == null) {
      return null;
    }
    return java.nio.file.Paths.get(home, ".config", "devkit", "config.json");
  }
}
