package desk.core.logging;

/**
 * Minimal logging facade backed by SLF4J.
 *
 * <p>The application codes to this interface so the logging backend can be
 * swapped (for example, to java.util.logging or a native-image-compatible
 * implementation) without touching the rest of the codebase.</p>
 */
public interface Logger {

  String name();

  boolean isDebugEnabled();

  boolean isInfoEnabled();

  boolean isWarnEnabled();

  boolean isErrorEnabled();

  void debug(String message);

  void debug(String format, Object arg);

  void debug(String format, Object... arguments);

  void info(String message);

  void info(String format, Object arg);

  void info(String format, Object... arguments);

  void warn(String message);

  void warn(String format, Object arg);

  void warn(String format, Object... arguments);

  void error(String message);

  void error(String format, Object arg);

  void error(String format, Object... arguments);

  void error(String message, Throwable throwable);
}
