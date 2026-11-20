package desk.syntax.highlighter;

import desk.lexer.tokens.TokenType;

/**
 * A syntax-highlighting theme mapping {@link TokenType}s to visual styles.
 *
 * <p>Themes are independent of the lexer and can be swapped at runtime. New
 * themes can be added for additional languages without modifying the lexer.</p>
 */
public interface Theme {

  String name();

  String background();

  String foreground();

  /**
   * Returns the style for a token type, or {@code null} to use the default
   * foreground/background.
   */
  TokenStyle style(TokenType type);
}
