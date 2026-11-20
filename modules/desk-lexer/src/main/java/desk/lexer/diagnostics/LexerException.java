package desk.lexer.diagnostics;

/**
 * Thrown when the lexer encounters an unrecoverable error while tokenizing
 * source text. The lexer is designed to never throw this for ordinary
 * malformed or incomplete input; it produces {@code UNKNOWN} tokens instead.
 * This exception is reserved for truly unrecoverable conditions (for example,
 * a null source or an internal invariant violation).
 */
public class LexerException extends RuntimeException {

  public LexerException(String message) {
    super(message);
  }

  public LexerException(String message, Throwable cause) {
    super(message, cause);
  }
}
