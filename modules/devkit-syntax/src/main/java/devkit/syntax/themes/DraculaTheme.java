package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;

/**
 * A Dracula-inspired dark theme.
 */
public class DraculaTheme extends AbstractTheme {

  public DraculaTheme() {
    super("dracula", "#282a36", "#f8f8f2");

    style(TokenType.KEYWORD, "#ff79c6");
    style(TokenType.CONTEXTUAL_KEYWORD, "#ff79c6");
    style(TokenType.PRIMITIVE_TYPE, "#8be9fd");
    style(TokenType.IDENTIFIER, "#f8f8f2");
    style(TokenType.STRING_LITERAL, "#f1fa8c");
    style(TokenType.CHAR_LITERAL, "#f1fa8c");
    style(TokenType.TEXT_BLOCK, "#f1fa8c");
    style(TokenType.LINE_COMMENT, "#6272a4");
    style(TokenType.BLOCK_COMMENT, "#6272a4");
    style(TokenType.ANNOTATION, "#ffb86c");
    style(TokenType.INTEGER_LITERAL, "#bd93f9");
    style(TokenType.FLOATING_POINT_LITERAL, "#bd93f9");
    style(TokenType.OPERATOR, "#f8f8f2");
    style(TokenType.SEPARATOR, "#f8f8f2");
    style(TokenType.UNKNOWN, "#ff5555");
  }
}
