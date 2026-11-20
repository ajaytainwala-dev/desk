package desk.syntax.themes;

import desk.lexer.tokens.TokenType;

/**
 * A Monokai-inspired dark theme.
 */
public class MonokaiTheme extends AbstractTheme {

  public MonokaiTheme() {
    super("monokai", "#272822", "#f8f8f2");

    style(TokenType.KEYWORD, "#f92672");
    style(TokenType.CONTEXTUAL_KEYWORD, "#f92672");
    style(TokenType.PRIMITIVE_TYPE, "#66d9ef");
    style(TokenType.IDENTIFIER, "#f8f8f2");
    style(TokenType.STRING_LITERAL, "#e6db74");
    style(TokenType.CHAR_LITERAL, "#e6db74");
    style(TokenType.TEXT_BLOCK, "#e6db74");
    style(TokenType.LINE_COMMENT, "#75715e");
    style(TokenType.BLOCK_COMMENT, "#75715e");
    style(TokenType.ANNOTATION, "#fd971f");
    style(TokenType.INTEGER_LITERAL, "#ae81ff");
    style(TokenType.FLOATING_POINT_LITERAL, "#ae81ff");
    style(TokenType.OPERATOR, "#f8f8f2");
    style(TokenType.SEPARATOR, "#f8f8f2");
    style(TokenType.UNKNOWN, "#ff0000");
  }
}
