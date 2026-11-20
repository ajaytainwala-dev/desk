package desk.formatter;

/**
 * Exception thrown when formatting fails.
 */
public class FormatterException extends RuntimeException {

  public FormatterException(String message) {
    super(message);
  }

  public FormatterException(String message, Throwable cause) {
    super(message, cause);
  }
}
