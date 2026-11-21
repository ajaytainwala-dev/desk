package desk.java.quality;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Error Prone is typically a compiler plugin, not a standalone CLI.
 * Desk detects project configuration and reports guidance — it does not
 * duplicate JDT LS diagnostics.
 */
public final class ErrorProneHints implements QualityTool {

  @Override
  public String name() {
    return "errorprone";
  }

  @Override
  public boolean available(Path workspaceRoot) {
    return discoverConfig(workspaceRoot).isPresent();
  }

  @Override
  public Optional<Path> discoverConfig(Path workspaceRoot) {
    Path pom = workspaceRoot.resolve("pom.xml");
    if (Files.isRegularFile(pom)) {
      try {
        String text = Files.readString(pom);
        if (text.contains("error-prone") || text.contains("errorprone")) {
          return Optional.of(pom);
        }
      } catch (Exception ignored) {
        // ignore
      }
    }
    Path gradle = workspaceRoot.resolve("build.gradle");
    if (Files.isRegularFile(gradle)) {
      try {
        if (Files.readString(gradle).contains("errorprone")
            || Files.readString(gradle).contains("net.ltgt.errorprone")) {
          return Optional.of(gradle);
        }
      } catch (Exception ignored) {
        // ignore
      }
    }
    return Optional.empty();
  }

  @Override
  public List<QualityFinding> analyze(Path workspaceRoot, Path file) {
    // No standalone execution — compile with Error Prone via the project's build.
    return List.of();
  }
}
