package desk.java.quality;

import desk.java.ProcessIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Optional SpotBugs integration. Requires {@code spotbugs} on PATH and
 * compiled classes; returns empty when unavailable.
 */
public final class SpotBugsRunner implements QualityTool {

  @Override
  public String name() {
    return "spotbugs";
  }

  @Override
  public boolean available(Path workspaceRoot) {
    return which("spotbugs").isPresent();
  }

  @Override
  public Optional<Path> discoverConfig(Path workspaceRoot) {
    Path a = workspaceRoot.resolve("spotbugs.xml");
    return Files.isRegularFile(a) ? Optional.of(a) : Optional.empty();
  }

  @Override
  public List<QualityFinding> analyze(Path workspaceRoot, Path file) {
    Optional<String> bin = which("spotbugs");
    if (bin.isEmpty()) {
      return List.of();
    }
    // SpotBugs analyzes class files; without a built target we report nothing.
    Path classes = workspaceRoot.resolve("target/classes");
    if (!Files.isDirectory(classes)) {
      classes = workspaceRoot.resolve("build/classes/java/main");
    }
    if (!Files.isDirectory(classes)) {
      return List.of();
    }
    List<String> cmd = List.of(bin.get(), "-textui", classes.toString());
    try {
      ProcessIo.Result result = ProcessIo.run(cmd, workspaceRoot, Map.of(), 3, TimeUnit.MINUTES);
      List<QualityFinding> findings = new ArrayList<>();
      for (String line : (result.stdout() + "\n" + result.stderr()).split("\\R")) {
        if (line.contains("Bug:") || line.contains("[ERROR]") || line.contains("[WARN]")) {
          findings.add(new QualityFinding(file, 1, 1, "WARNING", line.trim(), "spotbugs"));
        }
      }
      return findings;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static Optional<String> which(String name) {
    String path = System.getenv("PATH");
    if (path == null) {
      return Optional.empty();
    }
    boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    for (String entry : path.split(win ? ";" : ":")) {
      Path p = Path.of(entry, win ? name + ".bat" : name);
      if (Files.isRegularFile(p)) {
        return Optional.of(p.toString());
      }
    }
    return Optional.empty();
  }
}
