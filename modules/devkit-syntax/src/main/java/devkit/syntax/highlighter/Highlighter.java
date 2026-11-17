package devkit.syntax.highlighter;

import devkit.lexer.tokens.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a {@link Theme} to a {@link TokenStream}, producing a list of
 * {@link HighlightedToken}s.
 */
public final class Highlighter {

  private final Theme theme;

  public Highlighter(Theme theme) {
    this.theme = theme;
  }

  public Theme theme() {
    return theme;
  }

  /**
   * Highlights a token stream using the configured theme. Whitespace and
   * newline tokens are mapped to the default style (null) so they render
   * with the terminal's default colors.
   */
  public List<HighlightedToken> highlight(devkit.lexer.tokens.TokenStream stream) {
    List<HighlightedToken> result = new ArrayList<>();
    for (devkit.lexer.tokens.Token token : stream.tokens()) {
      TokenStyle style = theme.style(token.type());
      result.add(new HighlightedToken(token, style));
    }
    return result;
  }
}
