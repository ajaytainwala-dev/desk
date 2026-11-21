package desk.java.build;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Unified diagnostic mapped to an editor location when possible.
 */
public final class BuildDiagnostic {

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  private final Path file;
  private final int line;
  private final int column;
  private final Severity severity;
  private final String message;
  private final String source;
  private final String suggestedFix;

  public BuildDiagnostic(
      Path file,
      int line,
      int column,
      Severity severity,
      String message,
      String source,
      String suggestedFix) {
    this.file = file;
    this.line = line;
    this.column = column;
    this.severity = severity != null ? severity : Severity.ERROR;
    this.message = message != null ? message : "";
    this.source = source != null ? source : "build";
    this.suggestedFix = suggestedFix;
  }

  public Optional<Path> file() {
    return Optional.ofNullable(file);
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public Severity severity() {
    return severity;
  }

  public String message() {
    return message;
  }

  public String source() {
    return source;
  }

  public Optional<String> suggestedFix() {
    return Optional.ofNullable(suggestedFix);
  }

  public String format() {
    String loc = file != null
        ? file + ":" + Math.max(line, 1) + ":" + Math.max(column, 1)
        : "(no location)";
    return severity + " " + loc + " [" + source + "] " + message;
  }
}
