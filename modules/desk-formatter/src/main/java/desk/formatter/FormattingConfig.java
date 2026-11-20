package desk.formatter;

import java.util.Objects;

/**
 * Configuration for the Java source formatter.
 *
 * <p>All settings have sensible defaults. The formatter is immutable once
 * constructed, so a single instance can be shared across threads.</p>
 */
public final class FormattingConfig {

  /** Number of spaces per indentation level (default 4). */
  public static final int DEFAULT_INDENT_SIZE = 4;

  /** Maximum line length before wrapping is attempted (default 120). */
  public static final int DEFAULT_MAX_LINE_LENGTH = 120;

  /** Default formatting configuration. */
  public static final FormattingConfig DEFAULT = new FormattingConfig();

  private final int indentSize;
  private final boolean useTabs;
  private final int maxLineLength;
  private final boolean insertFinalNewline;
  private final boolean keepBlankLines;
  private final boolean spaceBeforeOpenBrace;
  private final boolean spaceAfterComma;
  private final boolean spaceAroundOperators;

  private FormattingConfig() {
    this(DEFAULT_INDENT_SIZE, false, DEFAULT_MAX_LINE_LENGTH, true, true,
        true, true, true);
  }

  private FormattingConfig(int indentSize, boolean useTabs, int maxLineLength,
      boolean insertFinalNewline, boolean keepBlankLines,
      boolean spaceBeforeOpenBrace, boolean spaceAfterComma,
      boolean spaceAroundOperators) {
    this.indentSize = indentSize;
    this.useTabs = useTabs;
    this.maxLineLength = maxLineLength;
    this.insertFinalNewline = insertFinalNewline;
    this.keepBlankLines = keepBlankLines;
    this.spaceBeforeOpenBrace = spaceBeforeOpenBrace;
    this.spaceAfterComma = spaceAfterComma;
    this.spaceAroundOperators = spaceAroundOperators;
  }

  public int indentSize() {
    return indentSize;
  }

  public boolean useTabs() {
    return useTabs;
  }

  public int maxLineLength() {
    return maxLineLength;
  }

  public boolean insertFinalNewline() {
    return insertFinalNewline;
  }

  public boolean keepBlankLines() {
    return keepBlankLines;
  }

  public boolean spaceBeforeOpenBrace() {
    return spaceBeforeOpenBrace;
  }

  public boolean spaceAfterComma() {
    return spaceAfterComma;
  }

  public boolean spaceAroundOperators() {
    return spaceAroundOperators;
  }

  /**
   * Returns the indentation string for a single level (spaces or a tab).
   */
  public String indentString() {
    if (useTabs) {
      return "\t";
    }
    return " ".repeat(indentSize);
  }

  public Builder toBuilder() {
    return new Builder(indentSize, useTabs, maxLineLength, insertFinalNewline,
        keepBlankLines, spaceBeforeOpenBrace, spaceAfterComma, spaceAroundOperators);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FormattingConfig)) return false;
    FormattingConfig that = (FormattingConfig) o;
    return indentSize == that.indentSize
        && useTabs == that.useTabs
        && maxLineLength == that.maxLineLength
        && insertFinalNewline == that.insertFinalNewline
        && keepBlankLines == that.keepBlankLines
        && spaceBeforeOpenBrace == that.spaceBeforeOpenBrace
        && spaceAfterComma == that.spaceAfterComma
        && spaceAroundOperators == that.spaceAroundOperators;
  }

  @Override
  public int hashCode() {
    return Objects.hash(indentSize, useTabs, maxLineLength, insertFinalNewline,
        keepBlankLines, spaceBeforeOpenBrace, spaceAfterComma, spaceAroundOperators);
  }

  @Override
  public String toString() {
    return "FormattingConfig{indentSize=" + indentSize + ", useTabs=" + useTabs
        + ", maxLineLength=" + maxLineLength + ", insertFinalNewline="
        + insertFinalNewline + ", keepBlankLines=" + keepBlankLines
        + ", spaceBeforeOpenBrace=" + spaceBeforeOpenBrace
        + ", spaceAfterComma=" + spaceAfterComma
        + ", spaceAroundOperators=" + spaceAroundOperators + "}";
  }

  /**
   * Builder for {@link FormattingConfig}.
   */
  public static final class Builder {

    private int indentSize = DEFAULT_INDENT_SIZE;
    private boolean useTabs = false;
    private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
    private boolean insertFinalNewline = true;
    private boolean keepBlankLines = true;
    private boolean spaceBeforeOpenBrace = true;
    private boolean spaceAfterComma = true;
    private boolean spaceAroundOperators = true;

    public Builder() {
      // defaults
    }

    private Builder(int indentSize, boolean useTabs, int maxLineLength,
        boolean insertFinalNewline, boolean keepBlankLines,
        boolean spaceBeforeOpenBrace, boolean spaceAfterComma,
        boolean spaceAroundOperators) {
      this.indentSize = indentSize;
      this.useTabs = useTabs;
      this.maxLineLength = maxLineLength;
      this.insertFinalNewline = insertFinalNewline;
      this.keepBlankLines = keepBlankLines;
      this.spaceBeforeOpenBrace = spaceBeforeOpenBrace;
      this.spaceAfterComma = spaceAfterComma;
      this.spaceAroundOperators = spaceAroundOperators;
    }

    public Builder indentSize(int indentSize) {
      this.indentSize = indentSize;
      return this;
    }

    public Builder useTabs(boolean useTabs) {
      this.useTabs = useTabs;
      return this;
    }

    public Builder maxLineLength(int maxLineLength) {
      this.maxLineLength = maxLineLength;
      return this;
    }

    public Builder insertFinalNewline(boolean insertFinalNewline) {
      this.insertFinalNewline = insertFinalNewline;
      return this;
    }

    public Builder keepBlankLines(boolean keepBlankLines) {
      this.keepBlankLines = keepBlankLines;
      return this;
    }

    public Builder spaceBeforeOpenBrace(boolean spaceBeforeOpenBrace) {
      this.spaceBeforeOpenBrace = spaceBeforeOpenBrace;
      return this;
    }

    public Builder spaceAfterComma(boolean spaceAfterComma) {
      this.spaceAfterComma = spaceAfterComma;
      return this;
    }

    public Builder spaceAroundOperators(boolean spaceAroundOperators) {
      this.spaceAroundOperators = spaceAroundOperators;
      return this;
    }

    public FormattingConfig build() {
      return new FormattingConfig(indentSize, useTabs, maxLineLength,
          insertFinalNewline, keepBlankLines, spaceBeforeOpenBrace,
          spaceAfterComma, spaceAroundOperators);
    }
  }
}
