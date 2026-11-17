package devkit.syntax.token.mapping;

import devkit.lexer.tokens.TokenType;

/**
 * Maps language-specific tokens to generic {@link TokenType}s, enabling the
 * highlighter to support additional languages in the future.
 *
 * <p>Initially only Java is supported. Future languages (JSON, XML, YAML,
 * JavaScript, TypeScript, Python, Rust) can provide their own implementations
 * without modifying the lexer or highlighter.</p>
 */
public interface TokenMapping {

  /**
   * The language name this mapping applies to (e.g. "java", "json").
   */
  String language();

  /**
   * Maps a language-specific token name to a {@link TokenType}. Returns
   * {@code null} if the token should be treated as {@link TokenType#UNKNOWN}.
   */
  TokenType map(String tokenName);
}
