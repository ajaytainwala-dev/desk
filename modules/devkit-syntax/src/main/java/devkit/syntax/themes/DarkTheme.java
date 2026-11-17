package devkit.syntax.themes;

import devkit.lexer.tokens.TokenType;

/**
 * A dark theme inspired by Visual Studio Code's default dark theme.
 */
public class DarkTheme extends AbstractTheme {

  public DarkTheme() {
    super("dark", "#1e1e1e", "#d4d4d4");

    style(TokenType.KEYWORD, "#569cd6");
    style(TokenType.CONTEXTUAL_KEYWORD, "#569cd6");
    style(TokenType.PRIMITIVE_TYPE, "#4ec9b0");
    style(TokenType.IDENTIFIER, "#9cdcfe");
    style(TokenType.STRING_LITERAL, "#ce9178");
    style(TokenType.CHAR_LITERAL, "#ce9178");
    style(TokenType.TEXT_BLOCK, "#ce9178");
    style(TokenType.LINE_COMMENT, "#6a9955");
    style(TokenType.BLOCK_COMMENT, "#6a9955");
    style(TokenType.ANNOTATION, "#dcdcaa");
    style(TokenType.INTEGER_LITERAL, "#b5cea8");
    style(TokenType.FLOATING_POINT_LITERAL, "#b5cea8");
    style(TokenType.OPERATOR, "#d4d4d4");
    style(TokenType.SEPARATOR, "#d4d4d4");
    style(TokenType.UNKNOWN, "#f44747");
  }
}
