package desk.java.quality;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Optional static-analysis tool integration. Tools are never required —
 * discovery returns empty when the binary or config is missing.
 */
public interface QualityTool {

  String name();

  /** True when the executable is on PATH or a project wrapper exists. */
  boolean available(Path workspaceRoot);

  /** Optional project config path (checkstyle.xml, pmd.xml, …). */
  Optional<Path> discoverConfig(Path workspaceRoot);

  /**
   * Runs the tool. Returns empty when unavailable. Never throws for missing tools.
   */
  List<QualityFinding> analyze(Path workspaceRoot, Path file);
}
