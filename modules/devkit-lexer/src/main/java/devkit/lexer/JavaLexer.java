package devkit.lexer;

import devkit.lexer.diagnostics.LexerException;
import devkit.lexer.tokens.Token;
import devkit.lexer.tokens.TokenStream;
import devkit.lexer.tokens.TokenType;

import java.util.Set;

/**
 * A reusable Java source lexer.
 *
 * <p>The lexer converts Java source text into a stream of {@link Token}s. It
 * handles keywords, contextual keywords, primitive types, identifiers (with
 * Unicode support), operators, separators, delimiters, numeric literals,
 * string literals, character literals, text blocks, line and block comments,
 * annotations, whitespace, and newlines.</p>
 *
 * <p>The lexer never crashes on syntactically invalid or unfinished input.
 * Unrecognized characters are emitted as {@code UNKNOWN} tokens, and
 * unterminated strings/comments are emitted as their respective token types
 * rather than throwing.</p>
 */
public final class JavaLexer {

  private static final Set<String> KEYWORDS = java.util.Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch",
      "char", "class", "const", "continue", "default", "do", "double",
      "else", "enum", "extends", "false", "final", "finally", "float",
      "for", "goto", "if", "implements", "import", "instanceof", "int",
      "interface", "long", "native", "new", "null", "package", "private",
      "protected", "public", "return", "short", "static", "strictfp",
      "super", "switch", "synchronized", "this", "throw", "throws",
      "transient", "true", "try", "void", "volatile", "while", "_");

  private static final Set<String> PRIMITIVE_TYPES = java.util.Set.of(
      "byte", "short", "int", "long", "float", "double", "char", "boolean");

  private static final Set<String> CONTEXTUAL_KEYWORDS = java.util.Set.of(
      "var", "record", "sealed", "permits", "yield", "module", "opens",
      "provides", "requires", "to", "uses", "with", "exports", "transitive",
      "by");

  private static final Set<String> THREE_CHAR_OPS = java.util.Set.of(
      ">>>", "<<=", ">>=", "&&=", "||=", "...");

  private static final Set<String> TWO_CHAR_OPS = java.util.Set.of(
      "->", "::", ">>", "<<", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=",
      "*=", "/=", "%=", "&=", "|=", "^=");

  private static final Set<Character> OPERATOR_CHARS = java.util.Set.of(
      '+', '-', '*', '/', '%', '&', '|', '^', '!', '~', '<', '>', '=', '?', ':');

  private final String source;
  private final int length;
  private int pos = 0;
  private int line = 1;
  private int column = 1;

  public JavaLexer(String source) {
    if (source == null) {
      throw new LexerException("Source text must not be null");
    }
    this.source = source;
    this.length = source.length();
  }

  /**
   * Tokenizes the entire source text.
   */
  public TokenStream tokenize() {
    java.util.List<Token> tokens = new java.util.ArrayList<>();
    while (!isAtEnd()) {
      tokens.add(scanToken());
    }
    tokens.add(new Token(TokenType.EOF, "", line, column, pos));
    return new TokenStream(tokens);
  }

  private Token scanToken() {
    int startLine = line;
    int startColumn = column;
    int startPos = pos;

    char c = advance();

    switch (c) {
      case ' ':
      case '\t':
      case '\r':
        return scanWhitespace(startLine, startColumn, startPos, c);
      case '\n':
        return new Token(TokenType.NEWLINE, "\n", startLine, startColumn, startPos);
      case '/':
        if (peek() == '/') {
          return scanLineComment(startLine, startColumn, startPos);
        }
        if (peek() == '*') {
          return scanBlockComment(startLine, startColumn, startPos);
        }
        return new Token(TokenType.OPERATOR, "/", startLine, startColumn, startPos);
      case '"':
        return scanStringOrTextBlock(startLine, startColumn, startPos);
      case '\'':
        return scanChar(startLine, startColumn, startPos);
      case '@':
        return scanAnnotation(startLine, startColumn, startPos);
      case ',':
        return new Token(TokenType.SEPARATOR, ",", startLine, startColumn, startPos);
      case ';':
        return new Token(TokenType.SEPARATOR, ";", startLine, startColumn, startPos);
      case '(':
        return new Token(TokenType.LPAREN, "(", startLine, startColumn, startPos);
      case ')':
        return new Token(TokenType.RPAREN, ")", startLine, startColumn, startPos);
      case '{':
        return new Token(TokenType.LBRACE, "{", startLine, startColumn, startPos);
      case '}':
        return new Token(TokenType.RBRACE, "}", startLine, startColumn, startPos);
      case '[':
        return new Token(TokenType.LBRACKET, "[", startLine, startColumn, startPos);
      case ']':
        return new Token(TokenType.RBRACKET, "]", startLine, startColumn, startPos);
      default:
        if (isIdentifierStart(c)) {
          return scanIdentifier(startLine, startColumn, startPos, c);
        }
        if (Character.isDigit(c)) {
          return scanNumber(startLine, startColumn, startPos, c);
        }
        // A float may start with a dot followed by a digit (e.g. .5).
        if (c == '.' && !isAtEnd() && Character.isDigit(peek())) {
          return scanNumber(startLine, startColumn, startPos, c);
        }
        return scanOperator(startLine, startColumn, startPos, c);
    }
  }

  private Token scanWhitespace(int startLine, int startColumn, int startPos, char first) {
    StringBuilder sb = new StringBuilder();
    sb.append(first);
    while (!isAtEnd() && (peek() == ' ' || peek() == '\t' || peek() == '\r')) {
      sb.append(advance());
    }
    return new Token(TokenType.WHITESPACE, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanLineComment(int startLine, int startColumn, int startPos) {
    // The first '/' was already consumed by advance() in scanToken.
    StringBuilder sb = new StringBuilder();
    sb.append('/');
    while (!isAtEnd() && peek() != '\n') {
      sb.append(advance());
    }
    return new Token(TokenType.LINE_COMMENT, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanBlockComment(int startLine, int startColumn, int startPos) {
    // The first '/' was already consumed by advance() in scanToken.
    StringBuilder sb = new StringBuilder();
    sb.append('/');
    while (!isAtEnd()) {
      char c = advance();
      sb.append(c);
      if (c == '*' && peek() == '/') {
        sb.append(advance());
        break;
      }
    }
    return new Token(TokenType.BLOCK_COMMENT, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanStringOrTextBlock(int startLine, int startColumn, int startPos) {
    // Text block: """
    if (peek() == '"' && peekNext() == '"') {
      return scanTextBlock(startLine, startColumn, startPos);
    }
    StringBuilder sb = new StringBuilder();
    sb.append('"');
    while (!isAtEnd()) {
      char c = advance();
      sb.append(c);
      if (c == '\\') {
        if (!isAtEnd()) {
          sb.append(advance());
        }
      } else if (c == '"') {
        break;
      }
    }
    return new Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanTextBlock(int startLine, int startColumn, int startPos) {
    // Consume the 2nd and 3rd quotes (the 1st was consumed by advance() at the
    // top of scanToken).
    advance();
    advance();
    StringBuilder sb = new StringBuilder();
    sb.append("\"\"\"");
    while (!isAtEnd()) {
      char c = advance();
      sb.append(c);
      if (c == '"' && peek() == '"' && peekNext() == '"') {
        sb.append(advance());
        sb.append(advance());
        break;
      }
    }
    return new Token(TokenType.TEXT_BLOCK, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanChar(int startLine, int startColumn, int startPos) {
    StringBuilder sb = new StringBuilder();
    sb.append('\'');
    while (!isAtEnd()) {
      char c = advance();
      sb.append(c);
      if (c == '\\') {
        if (!isAtEnd()) {
          sb.append(advance());
        }
      } else if (c == '\'') {
        break;
      } else if (c == '\n') {
        break; // unclosed char literal, stop at newline
      }
    }
    return new Token(TokenType.CHAR_LITERAL, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanAnnotation(int startLine, int startColumn, int startPos) {
    // '@' was consumed by advance() at the top of scanToken.
    StringBuilder sb = new StringBuilder();
    sb.append('@');
    if (!isAtEnd() && isIdentifierStart(peek())) {
      while (!isAtEnd() && isIdentifierPart(peek())) {
        sb.append(advance());
      }
    }
    return new Token(TokenType.ANNOTATION, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanIdentifier(int startLine, int startColumn, int startPos, char first) {
    StringBuilder sb = new StringBuilder();
    sb.append(first);
    while (!isAtEnd() && isIdentifierPart(peek())) {
      sb.append(advance());
    }
    String text = sb.toString();
    if (PRIMITIVE_TYPES.contains(text)) {
      return new Token(TokenType.PRIMITIVE_TYPE, text, startLine, startColumn, startPos);
    }
    if (KEYWORDS.contains(text)) {
      return new Token(TokenType.KEYWORD, text, startLine, startColumn, startPos);
    }
    if (CONTEXTUAL_KEYWORDS.contains(text)) {
      return new Token(TokenType.CONTEXTUAL_KEYWORD, text, startLine, startColumn, startPos);
    }
    return new Token(TokenType.IDENTIFIER, text, startLine, startColumn, startPos);
  }

  private Token scanNumber(int startLine, int startColumn, int startPos, char first) {
    StringBuilder sb = new StringBuilder();
    sb.append(first);
    boolean isFloat = false;

    // Hex (0x / 0X) or binary (0b / 0B)
    if (first == '0' && !isAtEnd()) {
      char next = peek();
      if (next == 'x' || next == 'X') {
        sb.append(advance());
        while (!isAtEnd() && (isHexDigit(peek()) || peek() == '_')) {
          sb.append(advance());
        }
        if (!isAtEnd() && (peek() == 'L' || peek() == 'l')) {
          sb.append(advance());
        }
        return new Token(TokenType.INTEGER_LITERAL, sb.toString(), startLine, startColumn, startPos);
      }
      if (next == 'b' || next == 'B') {
        sb.append(advance());
        while (!isAtEnd() && (peek() == '0' || peek() == '1' || peek() == '_')) {
          sb.append(advance());
        }
        if (!isAtEnd() && (peek() == 'L' || peek() == 'l')) {
          sb.append(advance());
        }
        return new Token(TokenType.INTEGER_LITERAL, sb.toString(), startLine, startColumn, startPos);
      }
    }

    // Decimal integer part
    while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '_')) {
      sb.append(advance());
    }

    // Floating point: digits.digits or digits.E...
    if (!isAtEnd() && peek() == '.') {
      char afterDot = peekNext();
      if (Character.isDigit(afterDot)) {
        isFloat = true;
        sb.append(advance()); // consume '.'
        while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '_')) {
          sb.append(advance());
        }
      }
    }

    if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
      isFloat = true;
      sb.append(advance());
      if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
        sb.append(advance());
      }
      while (!isAtEnd() && Character.isDigit(peek())) {
        sb.append(advance());
      }
    }

    // Optional suffix
    if (!isAtEnd()) {
      char c = peek();
      if (isFloat && (c == 'f' || c == 'F' || c == 'd' || c == 'D')) {
        sb.append(advance());
      } else if (!isFloat && (c == 'L' || c == 'l')) {
        sb.append(advance());
      }
    }

    return isFloat
        ? new Token(TokenType.FLOATING_POINT_LITERAL, sb.toString(), startLine, startColumn, startPos)
        : new Token(TokenType.INTEGER_LITERAL, sb.toString(), startLine, startColumn, startPos);
  }

  private Token scanOperator(int startLine, int startColumn, int startPos, char first) {
    if (!OPERATOR_CHARS.contains(first)) {
      return new Token(TokenType.UNKNOWN, String.valueOf(first), startLine, startColumn, startPos);
    }

    char second = peek();
    char third = peekNext();

    String three = "" + first + second + third;
    if (THREE_CHAR_OPS.contains(three)) {
      advance();
      advance();
      return new Token(TokenType.OPERATOR, three, startLine, startColumn, startPos);
    }

    String two = "" + first + second;
    if (TWO_CHAR_OPS.contains(two)) {
      advance();
      return new Token(TokenType.OPERATOR, two, startLine, startColumn, startPos);
    }

    return new Token(TokenType.OPERATOR, String.valueOf(first), startLine, startColumn, startPos);
  }

  private boolean isAtEnd() {
    return pos >= length;
  }

  private char advance() {
    char c = source.charAt(pos++);
    if (c == '\n') {
      line++;
      column = 1;
    } else {
      column++;
    }
    return c;
  }

  private char peek() {
    return isAtEnd() ? '\0' : source.charAt(pos);
  }

  private char peekNext() {
    return (pos + 1 >= length) ? '\0' : source.charAt(pos + 1);
  }

  private static boolean isIdentifierStart(char c) {
    return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
  }

  private static boolean isIdentifierPart(char c) {
    return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }
}
