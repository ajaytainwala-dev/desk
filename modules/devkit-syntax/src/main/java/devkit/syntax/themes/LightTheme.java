package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;

/**
 * A light theme suitable for bright terminals.
 */
public class LightTheme extends AbstractTheme {

  public LightTheme() {
    super("light", "#ffffff", "#000000");

    style(TokenType.KEYWORD, "#0000ff");
    style(TokenType.CONTEXTUAL_KEYWORD, "#0000ff");
    style(TokenType.PRIMITIVE_TYPE, "#2b91af");
    style(TokenType.IDENTIFIER, "#000000");
    style(TokenType.STRING_LITERAL, "#a31515");
    style(TokenType.CHAR_LITERAL, "#a31515");
    style(TokenType.TEXT_BLOCK, "#a31515");
    style(TokenType.LINE_COMMENT, "#008000");
    style(TokenType.BLOCK_COMMENT, "#008000");
    style(TokenType.ANNOTATION, "#ff0000");
    style(TokenType.INTEGER_LITERAL, "#098658");
    style(TokenType.FLOATING_POINT_LITERAL, "#098658");
    style(TokenType.OPERATOR, "#000000");
    style(TokenType.SEPARATOR, "#000000");
    style(TokenType.UNKNOWN, "#ff0000");
  }
}
