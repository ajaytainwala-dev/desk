package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;
import devkit.syntax.highlighter.Theme;
import devkit.syntax.highlighter.TokenStyle;

import java.util.EnumMap;
import java.util.Map;

/**
 * Base implementation of {@link Theme} storing styles in a map keyed by
 * token type. Subclasses populate the map in their constructor.
 */
public abstract class AbstractTheme implements Theme {

  private final String name;
  private final String background;
  private final String foreground;
  private final Map<TokenType, TokenStyle> styles = new EnumMap<>(TokenType.class);

  protected AbstractTheme(String name, String background, String foreground) {
    this.name = name;
    this.background = background;
    this.foreground = foreground;
  }

  protected void style(TokenType type, String foreground) {
    styles.put(type, new TokenStyle(foreground));
  }

  protected void style(TokenType type, String foreground, String background,
                       boolean bold, boolean italic) {
    styles.put(type, new TokenStyle(foreground, background, bold, italic));
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String background() {
    return background;
  }

  @Override
  public String foreground() {
    return foreground;
  }

  @Override
  public TokenStyle style(TokenType type) {
    return styles.get(type);
  }
}
