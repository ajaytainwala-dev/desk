package devkit.formatter;

import devkit.lexer.JavaLexer;
import devkit.lexer.tokens.Token;
import devkit.lexer.tokens.TokenStream;
import devkit.lexer.tokens.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Formats Java source code according to a {@link FormattingConfig}.
 *
 * <p>The formatter operates on the token stream produced by
 * {@link JavaLexer}, so it is independent of the editor and of any file I/O.
 * It normalizes whitespace, indentation, and brace placement while preserving
 * all source tokens and their order.</p>
 *
 * <p>Unparseable input is handled gracefully: unknown tokens are passed
 * through unchanged, and the formatter never throws on malformed Java.</p>
 */
public final class Formatter {

  /** Keywords that are typically followed by a parenthesized expression. */
  private static final Set<String> CONTROL_FLOW_KEYWORDS = java.util.Set.of(
      "if", "for", "while", "switch", "synchronized", "catch");

  /** Keywords that can follow a closing brace on the same line. */
  private static final Set<String> BRACE_FOLLOW_KEYWORDS = java.util.Set.of(
      "else", "catch", "finally");

  /** Separator values that should never have a space before them. */
  private static final Set<String> NO_SPACE_BEFORE_VALUES = java.util.Set.of(
      ")", "}", "]", ",", ";", ":", ".");

  /** Tokens that should never have a space after them. */
  private static final Set<TokenType> NO_SPACE_AFTER = java.util.Set.of(
      TokenType.LPAREN, TokenType.LBRACE, TokenType.LBRACKET);

  /** Separator values that should never have a space after them. */
  private static final Set<String> NO_SPACE_AFTER_VALUES = java.util.Set.of(
      "(", "{", "[", ".");

  private final FormattingConfig config;

  public Formatter() {
    this(FormattingConfig.DEFAULT);
  }

  public Formatter(FormattingConfig config) {
    this.config = config;
  }

  /**
   * Formats the given Java source text.
   *
   * @param source the source text to format (must not be {@code null})
   * @return a {@link FormatResult} containing the formatted text and change info
   * @throws FormatterException if the source cannot be lexed
   */
  public FormatResult format(String source) {
    if (source == null) {
      throw new FormatterException("Source text must not be null");
    }
    if (source.isEmpty()) {
      return new FormatResult("", "");
    }

    try {
      TokenStream stream = new JavaLexer(source).tokenize();
      String formatted = formatTokens(stream.tokens());
      if (config.insertFinalNewline()
          && !formatted.isEmpty()
          && !formatted.endsWith("\n")) {
        formatted = formatted + "\n";
      }
      return new FormatResult(source, formatted);
    } catch (Exception e) {
      throw new FormatterException("Failed to format source: " + e.getMessage(), e);
    }
  }

  /**
   * Formats the given Java source text and returns only the formatted string.
   */
  public String formatString(String source) {
    return format(source).formatted();
  }

  private String formatTokens(List<Token> tokens) {
    // Filter out whitespace, newlines, and EOF; we control all whitespace output.
    List<Token> significant = new ArrayList<>();
    for (Token t : tokens) {
      if (t.type() != TokenType.WHITESPACE && t.type() != TokenType.NEWLINE
          && t.type() != TokenType.EOF) {
        significant.add(t);
      }
    }

    StringBuilder out = new StringBuilder();
    int indentLevel = 0;
    boolean atStartOfLine = true;
    boolean pendingNewline = false;

    for (int i = 0; i < significant.size(); i++) {
      Token token = significant.get(i);
      Token next = i + 1 < significant.size() ? significant.get(i + 1) : null;

      // Decrease indent before emitting a closing brace: "}" belongs to the
      // enclosing block, not the inner one.
      if (token.type() == TokenType.RBRACE && "}".equals(token.value())) {
        indentLevel = Math.max(0, indentLevel - 1);
      }

      // Handle pending newline: emit \n + indentation before this token.
      if (pendingNewline) {
        out.append('\n');
        pendingNewline = false;
      }
      if (atStartOfLine) {
        out.append(indentString(indentLevel));
        atStartOfLine = false;
      }

      // Emit the token value.
      out.append(token.value());

      // Decide what to do after this token.
      boolean newlineAfter = shouldNewlineAfter(token, next);
      boolean spaceAfter = shouldInsertSpaceAfter(token, next);

      if (newlineAfter) {
        pendingNewline = true;
        atStartOfLine = true;
        // Adjust indent level for braces.
        if (token.type() == TokenType.LBRACE && "{".equals(token.value())) {
          indentLevel++;
        }
      } else if (spaceAfter) {
        out.append(' ');
      }
    }

    return out.toString();
  }

  /**
   * Returns the indentation string for the given level.
   */
  private String indentString(int level) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < level * config.indentSize(); i++) {
      sb.append(config.useTabs() ? '\t' : ' ');
    }
    return sb.toString();
  }

  /**
   * Whether a newline should be emitted after the given token.
   */
  private boolean shouldNewlineAfter(Token token, Token next) {
    switch (token.type()) {
      case LBRACE:
        if ("{".equals(token.value())) {
          return true;
        }
        return false;
      case RBRACE:
        if ("}".equals(token.value())) {
          return true;
        }
        return false;
      case SEPARATOR:
        return ";".equals(token.value());
      default:
        return false;
    }
  }

  /**
   * Whether a space should be inserted after the given token.
   */
  private boolean shouldInsertSpaceAfter(Token token, Token next) {
    if (next == null) {
      return false;
    }

    String val = token.value();

    // No space after opening delimiters or dot.
    if (NO_SPACE_AFTER_VALUES.contains(val)) {
      return false;
    }

    // No space after an opening delimiter token type.
    if (NO_SPACE_AFTER.contains(token.type())) {
      return false;
    }

    // Space after comma.
    if (token.type() == TokenType.SEPARATOR && ",".equals(val)) {
      return config.spaceAfterComma();
    }

    // Space after semicolon (but semicolon triggers newline, so no space here).
    if (token.type() == TokenType.SEPARATOR && ";".equals(val)) {
      return false;
    }

    // Space after a keyword that expects a parenthesized expression.
    if (token.type() == TokenType.KEYWORD
        && CONTROL_FLOW_KEYWORDS.contains(val)
        && next.type() == TokenType.LPAREN) {
      return true;
    }

    // Space after a closing brace followed by else/catch/finally.
    // (The newline after } handles the line break; no space needed.)
    if (token.type() == TokenType.RBRACE && "}".equals(val)
        && next.type() == TokenType.KEYWORD
        && BRACE_FOLLOW_KEYWORDS.contains(next.value())) {
      return false;
    }

    // Space after binary operators.
    if (token.type() == TokenType.OPERATOR && config.spaceAroundOperators()) {
      return true;
    }

    // Space after an identifier/keyword followed by another identifier/keyword
    // (e.g. "class Foo", "void bar", "public static").
    if ((token.type() == TokenType.IDENTIFIER || token.type() == TokenType.KEYWORD)
        && (next.type() == TokenType.IDENTIFIER || next.type() == TokenType.KEYWORD)) {
      return true;
    }

    // Space after a closing parenthesis followed by an identifier/keyword
    // (e.g. ") throws", ") extends").
    if (token.type() == TokenType.RPAREN && ")".equals(val)
        && (next.type() == TokenType.IDENTIFIER || next.type() == TokenType.KEYWORD)) {
      return true;
    }

    // No space before closing delimiters, comma, semicolon, colon, dot.
    String nextVal = next.value();
    if (NO_SPACE_BEFORE_VALUES.contains(nextVal)) {
      return false;
    }

    // Space before an opening brace: "Foo {", "bar() {", "if (x) {".
    if ("{".equals(nextVal)) {
      return true;
    }

    // Default: no space.
    return false;
  }
}
