package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;

/**
 * A high-contrast theme for accessibility.
 */
public class HighContrastTheme extends AbstractTheme {

  public HighContrastTheme() {
    super("high-contrast", "#000000", "#ffffff");

    style(TokenType.KEYWORD, "#ffff00", null, true, false);
    style(TokenType.CONTEXTUAL_KEYWORD, "#ffff00", null, true, false);
    style(TokenType.PRIMITIVE_TYPE, "#00ffff");
    style(TokenType.IDENTIFIER, "#ffffff");
    style(TokenType.STRING_LITERAL, "#ff00ff");
    style(TokenType.CHAR_LITERAL, "#ff00ff");
    style(TokenType.TEXT_BLOCK, "#ff00ff");
    style(TokenType.LINE_COMMENT, "#00ff00");
    style(TokenType.BLOCK_COMMENT, "#00ff00");
    style(TokenType.ANNOTATION, "#ffffff", null, true, false);
    style(TokenType.INTEGER_LITERAL, "#ff8800");
    style(TokenType.FLOATING_POINT_LITERAL, "#ff8800");
    style(TokenType.OPERATOR, "#ffffff");
    style(TokenType.SEPARATOR, "#ffffff");
    style(TokenType.UNKNOWN, "#ff0000", null, true, false);
  }
}
