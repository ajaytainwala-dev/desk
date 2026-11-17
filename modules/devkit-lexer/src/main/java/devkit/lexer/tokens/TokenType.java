package devkit.lexer.tokens;

/**
 * Classification of a single token produced by the lexer.
 */
public enum TokenType {
  // Keywords and types
  KEYWORD,
  CONTEXTUAL_KEYWORD,
  PRIMITIVE_TYPE,

  // Identifiers and literals
  IDENTIFIER,
  INTEGER_LITERAL,
  FLOATING_POINT_LITERAL,
  CHAR_LITERAL,
  STRING_LITERAL,
  TEXT_BLOCK,

  // Comments
  LINE_COMMENT,
  BLOCK_COMMENT,

  // Annotations
  ANNOTATION,

  // Operators
  OPERATOR,

  // Separators
  SEPARATOR,

  // Delimiters
  LPAREN,
  RPAREN,
  LBRACE,
  RBRACE,
  LBRACKET,
  RBRACKET,

  // Structural
  WHITESPACE,
  NEWLINE,

  // Error / end
  UNKNOWN,
  EOF
}
