package devkit.java;

/**
 * Exception thrown when Java compilation or execution fails.
 */
public class JavaException extends RuntimeException {

  public JavaException(String message) {
    super(message);
  }

  public JavaException(String message, Throwable cause) {
    super(message, cause);
  }
}
