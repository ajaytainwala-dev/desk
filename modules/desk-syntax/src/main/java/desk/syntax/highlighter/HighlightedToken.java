package desk.syntax.highlighter;

import desk.lexer.tokens.Token;

/**
 * A token paired with the style that should be used to render it.
 */
public final class HighlightedToken {

  private final Token token;
  private final TokenStyle style;

  public HighlightedToken(Token token, TokenStyle style) {
    this.token = token;
    this.style = style;
  }

  public Token token() {
    return token;
  }

  public TokenStyle style() {
    return style;
  }

  @Override
  public String toString() {
    return "HighlightedToken{token=" + token + ", style=" + style + "}";
  }
}
