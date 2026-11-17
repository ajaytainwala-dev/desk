package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;

/**
 * A Nord-inspired dark theme.
 */
public class NordTheme extends AbstractTheme {

  public NordTheme() {
    super("nord", "#2e3440", "#d8dee9");

    style(TokenType.KEYWORD, "#81a1c1");
    style(TokenType.CONTEXTUAL_KEYWORD, "#81a1c1");
    style(TokenType.PRIMITIVE_TYPE, "#88c0d0");
    style(TokenType.IDENTIFIER, "#d8dee9");
    style(TokenType.STRING_LITERAL, "#a3be8c");
    style(TokenType.CHAR_LITERAL, "#a3be8c");
    style(TokenType.TEXT_BLOCK, "#a3be8c");
    style(TokenType.LINE_COMMENT, "#4c566a");
    style(TokenType.BLOCK_COMMENT, "#4c566a");
    style(TokenType.ANNOTATION, "#ebcb8b");
    style(TokenType.INTEGER_LITERAL, "#b48ead");
    style(TokenType.FLOATING_POINT_LITERAL, "#b48ead");
    style(TokenType.OPERATOR, "#d8dee9");
    style(TokenType.SEPARATOR, "#d8dee9");
    style(TokenType.UNKNOWN, "#bf616a");
  }
}
