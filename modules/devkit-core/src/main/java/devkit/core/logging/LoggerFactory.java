package devkit.core.logging;

/**
 * Central point for obtaining {@link Logger} instances.
 *
 * <p>SLF4J is used as the logging backend with slf4j-simple as the default
 * implementation, which keeps the runtime footprint small and requires no
 * external configuration file.</p>
 */
public final class LoggerFactory {

  private static volatile Logger root;

  private LoggerFactory() {
    // utility class
  }

  /**
   * Returns a logger for the given name.
   */
  public static Logger getLogger(String name) {
    return Slf4jLogger.getLogger(name);
  }

  /**
   * Returns a logger for the given class.
   */
  public static Logger getLogger(Class<?> clazz) {
    return Slf4jLogger.getLogger(clazz);
  }

  /**
   * Returns the root application logger.
   */
  public static Logger root() {
    Logger current = root;
    if (current == null) {
      synchronized (LoggerFactory.class) {
        current = root;
        if (current == null) {
          current = getLogger("devkit");
          root = current;
        }
      }
    }
    return current;
  }
}
