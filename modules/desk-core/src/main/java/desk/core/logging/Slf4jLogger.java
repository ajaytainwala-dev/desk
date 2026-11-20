package desk.core.logging;

import org.slf4j.LoggerFactory;

/**
 * {@link Logger} implementation backed by SLF4J.
 */
public class Slf4jLogger implements Logger {

  private final org.slf4j.Logger logger;

  private Slf4jLogger(org.slf4j.Logger logger) {
    this.logger = logger;
  }

  public static Logger getLogger(String name) {
    return new Slf4jLogger(LoggerFactory.getLogger(name));
  }

  public static Logger getLogger(Class<?> clazz) {
    return new Slf4jLogger(LoggerFactory.getLogger(clazz));
  }

  @Override
  public String name() {
    return logger.getName();
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public void debug(String message) {
    if (isDebugEnabled()) {
      logger.debug(message);
    }
  }

  @Override
  public void debug(String format, Object arg) {
    if (isDebugEnabled()) {
      logger.debug(format, arg);
    }
  }

  @Override
  public void debug(String format, Object... arguments) {
    if (isDebugEnabled()) {
      logger.debug(format, arguments);
    }
  }

  @Override
  public void info(String message) {
    if (isInfoEnabled()) {
      logger.info(message);
    }
  }

  @Override
  public void info(String format, Object arg) {
    if (isInfoEnabled()) {
      logger.info(format, arg);
    }
  }

  @Override
  public void info(String format, Object... arguments) {
    if (isInfoEnabled()) {
      logger.info(format, arguments);
    }
  }

  @Override
  public void warn(String message) {
    if (isWarnEnabled()) {
      logger.warn(message);
    }
  }

  @Override
  public void warn(String format, Object arg) {
    if (isWarnEnabled()) {
      logger.warn(format, arg);
    }
  }

  @Override
  public void warn(String format, Object... arguments) {
    if (isWarnEnabled()) {
      logger.warn(format, arguments);
    }
  }

  @Override
  public void error(String message) {
    logger.error(message);
  }

  @Override
  public void error(String format, Object arg) {
    logger.error(format, arg);
  }

  @Override
  public void error(String format, Object... arguments) {
    logger.error(format, arguments);
  }

  @Override
  public void error(String message, Throwable throwable) {
    logger.error(message, throwable);
  }
}
