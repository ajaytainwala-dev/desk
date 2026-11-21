package desk.java.quality;

import java.nio.file.Path;

public record QualityFinding(
    Path file,
    int line,
    int column,
    String severity,
    String message,
    String source) {
}
