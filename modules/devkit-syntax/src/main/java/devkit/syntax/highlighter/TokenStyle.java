package devkit.syntax.highlighter;

import devkit.lexer.tokens.TokenType;

/**
 * The visual style applied to a token of a given type.
 *
 * <p>Colors are expressed as hexadecimal {@code #RRGGBB} strings so the
 * theme remains independent of any particular terminal or GUI renderer. A
 * {@code null} color means "use the default"/"inherit".</p>
 */
public final class TokenStyle {

  private final String foreground;
  private final String background;
  private final boolean bold;
  private final boolean italic;

  public TokenStyle(String foreground, String background, boolean bold, boolean italic) {
    this.foreground = foreground;
    this.background = background;
    this.bold = bold;
    this.italic = italic;
  }

  public TokenStyle(String foreground) {
    this(foreground, null, false, false);
  }

  public String foreground() {
    return foreground;
  }

  public String background() {
    return background;
  }

  public boolean isBold() {
    return bold;
  }

  public boolean isItalic() {
    return italic;
  }

  @Override
  public String toString() {
    return String.format("TokenStyle{fg=%s, bg=%s, bold=%b, italic=%b}",
        foreground, background, bold, italic);
  }
}
