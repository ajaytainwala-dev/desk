package desk.lexer;

import desk.lexer.tokens.Token;
import desk.lexer.tokens.TokenStream;
import desk.lexer.tokens.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JavaLexerTest {

  private TokenStream lex(String source) {
    return new JavaLexer(source).tokenize();
  }

  /**
   * Returns all tokens including whitespace and EOF.
   */
  private List<Token> allTokens(String source) {
    return lex(source).tokens();
  }

  /**
   * Returns only non-whitespace, non-newline, non-EOF tokens — useful for
   * asserting on the meaningful tokens produced by the lexer.
   */
  private List<Token> realTokens(String source) {
    return allTokens(source).stream()
        .filter(t -> t.type() != TokenType.WHITESPACE
            && t.type() != TokenType.NEWLINE
            && t.type() != TokenType.EOF)
        .collect(Collectors.toList());
  }

  @Test
  void emptySourceProducesEof() {
    List<Token> tokens = allTokens("");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.EOF, tokens.get(0).type());
  }

  @Test
  void keywords() {
    List<Token> tokens = realTokens("class interface enum");
    assertEquals(3, tokens.size());
    assertEquals(TokenType.KEYWORD, tokens.get(0).type());
    assertEquals("class", tokens.get(0).value());
    assertEquals(TokenType.KEYWORD, tokens.get(1).type());
    assertEquals(TokenType.KEYWORD, tokens.get(2).type());
  }

  @Test
  void primitiveTypes() {
    List<Token> tokens = realTokens("int long double boolean char");
    assertEquals(5, tokens.size());
    for (Token token : tokens) {
      assertEquals(TokenType.PRIMITIVE_TYPE, token.type());
    }
  }

  @Test
  void contextualKeywords() {
    List<Token> tokens = realTokens("var record sealed yield");
    assertEquals(4, tokens.size());
    assertEquals(TokenType.CONTEXTUAL_KEYWORD, tokens.get(0).type());
    assertEquals("var", tokens.get(0).value());
    assertEquals(TokenType.CONTEXTUAL_KEYWORD, tokens.get(1).type());
    assertEquals(TokenType.CONTEXTUAL_KEYWORD, tokens.get(2).type());
    assertEquals(TokenType.CONTEXTUAL_KEYWORD, tokens.get(3).type());
  }

  @Test
  void identifiers() {
    List<Token> tokens = realTokens("myVariable _internal $price");
    assertEquals(3, tokens.size());
    assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
    assertEquals("myVariable", tokens.get(0).value());
    assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
    assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
  }

  @Test
  void unicodeIdentifiers() {
    List<Token> tokens = realTokens("\u00e9l\u00e9phant \u03c0 omega");
    assertEquals(3, tokens.size());
    assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
    assertEquals("\u00e9l\u00e9phant", tokens.get(0).value());
    assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
    assertEquals("\u03c0", tokens.get(1).value());
  }

  @Test
  void integerLiterals() {
    List<Token> tokens = realTokens("42 0xFF 0b1010 1_000_000 42L");
    assertEquals(5, tokens.size());
    for (Token token : tokens) {
      assertEquals(TokenType.INTEGER_LITERAL, token.type());
    }
    assertEquals("42", tokens.get(0).value());
    assertEquals("0xFF", tokens.get(1).value());
    assertEquals("0b1010", tokens.get(2).value());
    assertEquals("1_000_000", tokens.get(3).value());
    assertEquals("42L", tokens.get(4).value());
  }

  @Test
  void floatingPointLiterals() {
    List<Token> tokens = realTokens("3.14 1e10 2.5e-3 1.5f 1.0D");
    assertEquals(5, tokens.size());
    for (Token token : tokens) {
      assertEquals(TokenType.FLOATING_POINT_LITERAL, token.type());
    }
    assertEquals("3.14", tokens.get(0).value());
    assertEquals("1e10", tokens.get(1).value());
    assertEquals("1.5f", tokens.get(3).value());
  }

  @Test
  void stringLiteral() {
    List<Token> tokens = realTokens("\"hello world\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.STRING_LITERAL, tokens.get(0).type());
    assertEquals("\"hello world\"", tokens.get(0).value());
  }

  @Test
  void stringLiteralWithEscapedQuotes() {
    List<Token> tokens = realTokens("\"say \\\"hi\\\"\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.STRING_LITERAL, tokens.get(0).type());
    assertEquals("\"say \\\"hi\\\"\"", tokens.get(0).value());
  }

  @Test
  void stringLiteralWithEscapeSequences() {
    List<Token> tokens = realTokens("\"a\\nb\\t\\r\\\\\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.STRING_LITERAL, tokens.get(0).type());
  }

  @Test
  void charLiteral() {
    List<Token> tokens = realTokens("'a' '\\n' '\\''");
    assertEquals(3, tokens.size());
    for (Token token : tokens) {
      assertEquals(TokenType.CHAR_LITERAL, token.type());
    }
    assertEquals("'a'", tokens.get(0).value());
  }

  @Test
  void textBlock() {
    List<Token> tokens = realTokens("\"\"\"\n\"\"\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.TEXT_BLOCK, tokens.get(0).type());
    assertEquals("\"\"\"\n\"\"\"", tokens.get(0).value());
  }

  @Test
  void textBlockMultiline() {
    List<Token> tokens = realTokens("\"\"\"\n    public class Example { }\n    \"\"\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.TEXT_BLOCK, tokens.get(0).type());
    assertTrue(tokens.get(0).value().startsWith("\"\"\""));
    assertTrue(tokens.get(0).value().endsWith("\"\"\""));
  }

  @Test
  void lineComment() {
    List<Token> tokens = realTokens("// this is a comment");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.LINE_COMMENT, tokens.get(0).type());
    assertEquals("// this is a comment", tokens.get(0).value());
  }

  @Test
  void blockComment() {
    List<Token> tokens = realTokens("/* block comment */");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.BLOCK_COMMENT, tokens.get(0).type());
    assertEquals("/* block comment */", tokens.get(0).value());
  }

  @Test
  void unterminatedBlockCommentDoesNotCrash() {
    List<Token> tokens = allTokens("/* never closed");
    assertEquals(2, tokens.size());
    assertEquals(TokenType.BLOCK_COMMENT, tokens.get(0).type());
    assertEquals(TokenType.EOF, tokens.get(1).type());
  }

  @Test
  void annotation() {
    List<Token> tokens = realTokens("@Override @Deprecated");
    assertEquals(2, tokens.size());
    assertEquals(TokenType.ANNOTATION, tokens.get(0).type());
    assertEquals("@Override", tokens.get(0).value());
    assertEquals(TokenType.ANNOTATION, tokens.get(1).type());
    assertEquals("@Deprecated", tokens.get(1).value());
  }

  @Test
  void operators() {
    List<Token> tokens = realTokens("+ - * / % == != <= >= && || -> :: >>>");
    assertEquals(14, tokens.size());
    for (Token token : tokens) {
      assertEquals(TokenType.OPERATOR, token.type());
    }
  }

  @Test
  void separatorsAndDelimiters() {
    List<Token> tokens = realTokens("; , ( ) { } [ ]");
    assertEquals(8, tokens.size());
    assertEquals(TokenType.SEPARATOR, tokens.get(0).type());
    assertEquals(";", tokens.get(0).value());
    assertEquals(TokenType.SEPARATOR, tokens.get(1).type());
    assertEquals(",", tokens.get(1).value());
    assertEquals(TokenType.LPAREN, tokens.get(2).type());
    assertEquals(TokenType.RPAREN, tokens.get(3).type());
    assertEquals(TokenType.LBRACE, tokens.get(4).type());
    assertEquals(TokenType.RBRACE, tokens.get(5).type());
    assertEquals(TokenType.LBRACKET, tokens.get(6).type());
    assertEquals(TokenType.RBRACKET, tokens.get(7).type());
  }

  @Test
  void unknownCharacter() {
    List<Token> tokens = realTokens("`");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.UNKNOWN, tokens.get(0).type());
    assertEquals("`", tokens.get(0).value());
  }

  @Test
  void keywordsInsideStringAreNotHighlightedAsKeywords() {
    List<Token> tokens = realTokens("\"if (x > 10) { not actual syntax }\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.STRING_LITERAL, tokens.get(0).type());
  }

  @Test
  void keywordsInsideCommentsAreNotHighlightedAsKeywords() {
    List<Token> tokens = realTokens("// class int if");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.LINE_COMMENT, tokens.get(0).type());
  }

  @Test
  void textBlockPreservesNonJavaContent() {
    List<Token> tokens = realTokens("\"\"\"\n    public class Example { }\n    \"\"\"");
    assertEquals(1, tokens.size());
    assertEquals(TokenType.TEXT_BLOCK, tokens.get(0).type());
  }

  @Test
  void lineAndColumnTracking() {
    List<Token> tokens = allTokens("int x;");
    // int, space, x, ;, EOF
    assertEquals(5, tokens.size());
    assertEquals(1, tokens.get(0).line());
    assertEquals(1, tokens.get(0).column());
    assertEquals(1, tokens.get(2).line()); // x
    assertEquals(5, tokens.get(2).column()); // "int " is 4 chars, x at col 5
  }

  @Test
  void newlineIncrementsLine() {
    List<Token> tokens = allTokens("int x;\nint y;");
    // int, space, x, ;, newline, int, space, y, ;, EOF
    assertEquals(10, tokens.size());
    assertEquals(TokenType.NEWLINE, tokens.get(4).type());
    assertEquals(2, tokens.get(5).line()); // int on line 2
  }

  @Test
  void malformedInputDoesNotCrash() {
    List<Token> tokens = allTokens("class @@@ 123abc \"unterminated");
    assertFalse(tokens.isEmpty());
    assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type());
  }

  @Test
  void nullSourceThrows() {
    assertThrows(desk.lexer.diagnostics.LexerException.class, () -> new JavaLexer(null));
  }

  @Test
  void eofTokenAtEnd() {
    List<Token> tokens = allTokens("int x;");
    assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type());
  }
}
