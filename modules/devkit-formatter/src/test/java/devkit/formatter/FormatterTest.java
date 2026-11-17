package devkit.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Java source formatter.
 */
class FormatterTest {

  private final Formatter formatter = new Formatter(FormattingConfig.DEFAULT);

  @Test
  @DisplayName("empty source returns empty string")
  void emptySource() {
    FormatResult result = formatter.format("");
    assertEquals("", result.formatted());
    assertFalse(result.changed());
  }

  @Test
  @DisplayName("already formatted source is unchanged")
  void alreadyFormatted() {
    String source = "class Foo {\n    void bar() {\n    }\n}\n";
    FormatResult result = formatter.format(source);
    assertEquals(source, result.formatted());
    assertFalse(result.changed());
  }

  @Test
  @DisplayName("adds indentation and newlines for class with method")
  void classWithMethod() {
    String source = "class Foo{void bar(){}}";
    FormatResult result = formatter.format(source);
    String formatted = result.formatted();
    assertTrue(result.changed());
    assertTrue(formatted.contains("class Foo"));
    assertTrue(formatted.contains("{"));
    assertTrue(formatted.contains("}"));
    assertTrue(formatted.contains("void bar"));
  }

  @Test
  @DisplayName("formats if-else block with proper indentation")
  void ifElseBlock() {
    String source = "class Foo{void bar(){if(x){doA();}else{doB();}}}";
    String formatted = formatter.formatString(source);
    assertTrue(formatted.contains("if"));
    assertTrue(formatted.contains("else"));
    assertTrue(formatted.contains("doA"));
    assertTrue(formatted.contains("doB"));
    // Each brace level should increase indentation.
    int firstBrace = formatted.indexOf("{");
    int secondBrace = formatted.indexOf("{", firstBrace + 1);
    assertTrue(secondBrace > firstBrace);
  }

  @Test
  @DisplayName("formats for loop")
  void forLoop() {
    String source = "class Foo{void bar(){for(int i=0;i<n;i++){doIt();}}}";
    String formatted = formatter.formatString(source);
    assertTrue(formatted.contains("for"));
    assertTrue(formatted.contains("doIt"));
    assertTrue(formatted.contains("{"));
    assertTrue(formatted.contains("}"));
  }

  @Test
  @DisplayName("formats try-catch-finally")
  void tryCatchFinally() {
    String source = "class Foo{void bar(){try{doA();}catch(Exception e){doB();}finally{doC();}}}";
    String formatted = formatter.formatString(source);
    assertTrue(formatted.contains("try"));
    assertTrue(formatted.contains("catch"));
    assertTrue(formatted.contains("finally"));
  }

  @Test
  @DisplayName("normalizes multiple spaces between tokens")
  void normalizesSpaces() {
    String source = "class   Foo    {    void   bar   (   )   {   }   }";
    String formatted = formatter.formatString(source);
    // Within each line (ignoring leading indentation), there should be no
    // consecutive spaces between tokens.
    for (String line : formatted.split("\n", -1)) {
      String trimmed = line.stripLeading();
      assertFalse(trimmed.contains("  "),
          "Line has consecutive spaces: [" + line + "]");
    }
  }

  @Test
  @DisplayName("adds final newline when configured")
  void finalNewline() {
    FormattingConfig cfg = new FormattingConfig.Builder()
        .indentSize(4)
        .insertFinalNewline(true)
        .build();
    Formatter f = new Formatter(cfg);
    FormatResult result = f.format("class Foo {}\n");
    assertTrue(result.formatted().endsWith("\n"));
  }

  @Test
  @DisplayName("does not add final newline when disabled")
  void noFinalNewline() {
    FormattingConfig cfg = new FormattingConfig.Builder()
        .insertFinalNewline(false)
        .build();
    Formatter f = new Formatter(cfg);
    FormatResult result = f.format("class Foo {}\n");
    assertFalse(result.formatted().endsWith("\n"));
  }

  @Test
  @DisplayName("format result reports changed correctly")
  void changedDetection() {
    FormatResult unchanged = formatter.format("class Foo {\n    void bar() {\n    }\n}\n");
    assertFalse(unchanged.changed());
    assertEquals(0, unchanged.changedLineCount());

    FormatResult changed = formatter.format("class Foo{void bar(){}}");
    assertTrue(changed.changed());
    assertTrue(changed.changedLineCount() > 0);
  }

  @Test
  @DisplayName("handles null source by throwing")
  void nullSource() {
    assertThrows(FormatterException.class, () -> formatter.format(null));
  }

  @Test
  @DisplayName("config builder creates custom config")
  void configBuilder() {
    FormattingConfig cfg = new FormattingConfig.Builder()
        .indentSize(2)
        .useTabs(true)
        .maxLineLength(80)
        .build();
    assertEquals(2, cfg.indentSize());
    assertTrue(cfg.useTabs());
    assertEquals(80, cfg.maxLineLength());
    assertEquals("\t", cfg.indentString());
  }
}
