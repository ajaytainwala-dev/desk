package desk.core.config;

/**
 * Thrown when configuration cannot be loaded, parsed, or validated.
 */
public class ConfigException extends RuntimeException {

  public ConfigException(String message) {
    super(message);
  }

  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
