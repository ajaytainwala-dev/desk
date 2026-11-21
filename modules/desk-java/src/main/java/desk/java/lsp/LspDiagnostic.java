package desk.java.lsp;

import java.nio.file.Path;

public record LspDiagnostic(
    Path file,
    int line,
    int column,
    int endLine,
    int endColumn,
    String severity,
    String message,
    String source) {
}
