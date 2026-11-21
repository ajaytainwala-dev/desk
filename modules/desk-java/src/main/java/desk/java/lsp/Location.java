package desk.java.lsp;

import java.nio.file.Path;

public record Location(Path file, int line, int column) {
}
