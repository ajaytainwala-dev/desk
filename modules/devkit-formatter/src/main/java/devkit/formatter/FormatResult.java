package devkit.formatter;

/**
 * The result of formatting a source file.
 *
 * <p>Contains the formatted text plus metadata about what changed, so callers
 * can decide whether to write the result back or just report it.</p>
 */
public final class FormatResult {

  private final String original;
  private final String formatted;
  private final boolean changed;

  public FormatResult(String original, String formatted) {
    this.original = original;
    this.formatted = formatted;
    this.changed = !original.equals(formatted);
  }

  /**
   * The original (unformatted) source text.
   */
  public String original() {
    return original;
  }

  /**
   * The formatted source text.
   */
  public String formatted() {
    return formatted;
  }

  /**
   * Whether formatting changed the source text.
   */
  public boolean changed() {
    return changed;
  }

  /**
   * A unified-style diff summary: number of changed lines, or 0 if unchanged.
   */
  public int changedLineCount() {
    if (!changed) {
      return 0;
    }
    String[] origLines = original.split("\n", -1);
    String[] fmtLines = formatted.split("\n", -1);
    int count = 0;
    int max = Math.max(origLines.length, fmtLines.length);
    for (int i = 0; i < max; i++) {
      String o = i < origLines.length ? origLines[i] : null;
      String f = i < fmtLines.length ? fmtLines[i] : null;
      if (!java.util.Objects.equals(o, f)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public String toString() {
    return "FormatResult{changed=" + changed + ", changedLines="
        + changedLineCount() + "}";
  }
}
