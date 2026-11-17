package devkit.syntax;

import devkit.lexer.JavaLexer;
import devkit.lexer.tokens.TokenStream;
import devkit.syntax.highlighter.Highlighter;
import devkit.syntax.highlighter.HighlightedToken;
import devkit.syntax.highlighter.TokenStyle;
import devkit.syntax.themes.DarkTheme;
import devkit.syntax.themes.DraculaTheme;
import devkit.syntax.themes.HighContrastTheme;
import devkit.syntax.themes.LightTheme;
import devkit.syntax.themes.MonokaiTheme;
import devkit.syntax.themes.NordTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HighlighterTest {

  private HighlightedToken highlightOne(String source, String themeName) {
    devkit.syntax.highlighter.Theme theme = themeFor(themeName);
    Highlighter highlighter = new Highlighter(theme);
    TokenStream stream = new JavaLexer(source).tokenize();
    List<HighlightedToken> result = highlighter.highlight(stream);
    assertFalse(result.isEmpty());
    return result.get(0);
  }

  private devkit.syntax.highlighter.Theme themeFor(String name) {
    return switch (name) {
      case "dark" -> new DarkTheme();
      case "light" -> new LightTheme();
      case "monokai" -> new MonokaiTheme();
      case "dracula" -> new DraculaTheme();
      case "nord" -> new NordTheme();
      case "high-contrast" -> new HighContrastTheme();
      default -> throw new IllegalArgumentException("unknown theme: " + name);
    };
  }

  @Test
  void darkThemeKeywordStyle() {
    HighlightedToken ht = highlightOne("class", "dark");
    assertNotNull(ht.style());
    assertEquals("#569cd6", ht.style().foreground());
    assertEquals(TokenStyle.class, ht.style().getClass());
  }

  @Test
  void allThemesApplyKeywordStyle() {
    String[] themes = {"dark", "light", "monokai", "dracula", "nord", "high-contrast"};
    for (String themeName : themes) {
      HighlightedToken ht = highlightOne("class", themeName);
      assertNotNull(ht.style(), "theme " + themeName + " should style keywords");
      assertNotNull(ht.style().foreground(), "theme " + themeName + " keyword foreground");
    }
  }

  @Test
  void stringUsesStringStyleNotKeywordStyle() {
    HighlightedToken ht = highlightOne("\"class\"", "dark");
    assertNotNull(ht.style());
    assertEquals("#ce9178", ht.style().foreground());
  }

  @Test
  void commentUsesCommentStyle() {
    HighlightedToken ht = highlightOne("// comment", "dark");
    assertNotNull(ht.style());
    assertEquals("#6a9955", ht.style().foreground());
  }

  @Test
  void highContrastUsesBold() {
    devkit.syntax.highlighter.Theme theme = new HighContrastTheme();
    Highlighter highlighter = new Highlighter(theme);
    TokenStream stream = new JavaLexer("class").tokenize();
    List<HighlightedToken> result = highlighter.highlight(stream);
    HighlightedToken ht = result.get(0);
    assertNotNull(ht.style());
    assertTrue(ht.style().isBold());
    assertEquals("#ffff00", ht.style().foreground());
    assertEquals("#000000", theme.background());
    assertEquals("#ffffff", theme.foreground());
  }

  @Test
  void lightThemeHasLightBackground() {
    devkit.syntax.highlighter.Theme theme = new LightTheme();
    assertEquals("#ffffff", theme.background());
    assertEquals("#000000", theme.foreground());
  }

  @Test
  void unknownTypeGetsUnknownStyle() {
    HighlightedToken ht = highlightOne("`", "dark");
    assertNotNull(ht.style());
    assertEquals("#f44747", ht.style().foreground());
  }

  @Test
  void highlightReturnsOnePerToken() {
    Highlighter highlighter = new Highlighter(new DarkTheme());
    TokenStream stream = new JavaLexer("int x = 1;").tokenize();
    List<HighlightedToken> result = highlighter.highlight(stream);
    assertEquals(stream.size(), result.size());
  }

  @Test
  void themeNameIsCorrect() {
    assertEquals("dark", new DarkTheme().name());
    assertEquals("light", new LightTheme().name());
    assertEquals("monokai", new MonokaiTheme().name());
    assertEquals("dracula", new DraculaTheme().name());
    assertEquals("nord", new NordTheme().name());
    assertEquals("high-contrast", new HighContrastTheme().name());
  }
}
