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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional PMD integration — skips when {@code pmd} is not on PATH. */
public final class PmdRunner implements QualityTool {

  private static final Pattern LINE = Pattern.compile(
      "^(.+\\.java):(\\d+):\\s*(.*)$");

  @Override
  public String name() {
    return "pmd";
  }

  @Override
  public boolean available(Path workspaceRoot) {
    return which("pmd").isPresent()
        || Files.isRegularFile(workspaceRoot.resolve("pmd.xml"))
        || Files.isRegularFile(workspaceRoot.resolve("config/pmd/ruleset.xml"));
  }

  @Override
  public Optional<Path> discoverConfig(Path workspaceRoot) {
    Path a = workspaceRoot.resolve("pmd.xml");
    if (Files.isRegularFile(a)) {
      return Optional.of(a);
    }
    Path b = workspaceRoot.resolve("config/pmd/ruleset.xml");
    return Files.isRegularFile(b) ? Optional.of(b) : Optional.empty();
  }

  @Override
  public List<QualityFinding> analyze(Path workspaceRoot, Path file) {
    Optional<String> bin = which("pmd");
    if (bin.isEmpty()) {
      return List.of();
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(bin.get());
    cmd.add("check");
    cmd.add("-d");
    cmd.add(file.toString());
    cmd.add("-f");
    cmd.add("text");
    discoverConfig(workspaceRoot).ifPresent(cfg -> {
      cmd.add("-R");
      cmd.add(cfg.toString());
    });
    try {
      ProcessIo.Result result = ProcessIo.run(cmd, workspaceRoot, Map.of(), 2, TimeUnit.MINUTES);
      List<QualityFinding> findings = new ArrayList<>();
      for (String line : (result.stdout() + "\n" + result.stderr()).split("\\R")) {
        Matcher m = LINE.matcher(line.trim());
        if (m.find()) {
          findings.add(new QualityFinding(
              Path.of(m.group(1)), Integer.parseInt(m.group(2)), 1,
              "WARNING", m.group(3), "pmd"));
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
