package desk.lexer.tokens;

import java.util.Collections;
import java.util.List;

/**
 * The result of lexing: an ordered, immutable list of tokens.
 */
public final class TokenStream {

  private final List<Token> tokens;

  public TokenStream(List<Token> tokens) {
    this.tokens = Collections.unmodifiableList(new java.util.ArrayList<>(tokens));
  }

  public List<Token> tokens() {
    return tokens;
  }

  public int size() {
    return tokens.size();
  }

  public Token get(int index) {
    return tokens.get(index);
  }

  @Override
  public String toString() {
    return "TokenStream{size=" + size() + "}";
  }
}
