package desk.lexer.tokens;

/**
 * A single token produced by the lexer, with its classification, source text,
 * and position in the source.
 */
public final class Token {

  private final TokenType type;
  private final String value;
  private final int line;       // 1-based
  private final int column;     // 1-based, column of the first character
  private final int position;   // 0-based offset into the source

  public Token(TokenType type, String value, int line, int column, int position) {
    this.type = type;
    this.value = value;
    this.line = line;
    this.column = column;
    this.position = position;
  }

  public TokenType type() {
    return type;
  }

  public String value() {
    return value;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public int position() {
    return position;
  }

  @Override
  public String toString() {
    return String.format("Token{type=%s, value='%s', line=%d, col=%d, pos=%d}",
        type, value, line, column, position);
  }
}
