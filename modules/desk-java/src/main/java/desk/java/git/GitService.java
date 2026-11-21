package desk.java.git;

import desk.java.JavaException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the system {@code git} CLI. Never logs credentials or
 * tokens — only command results with sanitized arguments.
 */
public final class GitService {

  public record Result(int exitCode, String output) {
  }

  private GitService() {
  }

  public static boolean isRepository(Path root) {
    return Files.isDirectory(root.resolve(".git"))
        || run(root, List.of("rev-parse", "--is-inside-work-tree")).exitCode() == 0;
  }

  public static Result status(Path root) {
    ensureRepo(root);
    return run(root, List.of("status", "--short", "--branch"));
  }

  public static Result branch(Path root) {
    ensureRepo(root);
    return run(root, List.of("branch", "--show-current"));
  }

  public static Result createBranch(Path root, String name) {
    ensureRepo(root);
    if (name == null || name.isBlank() || name.startsWith("-")) {
      throw new JavaException("Invalid branch name");
    }
    return run(root, List.of("switch", "-c", name));
  }

  public static Result diff(Path root, String[] paths) {
    ensureRepo(root);
    List<String> cmd = new ArrayList<>();
    cmd.add("diff");
    if (paths != null) {
      for (String p : paths) {
        if (p != null && !p.startsWith("-")) {
          cmd.add(p);
        }
      }
    }
    return run(root, cmd);
  }

  public static Result log(Path root, int limit) {
    ensureRepo(root);
    return run(root, List.of("log", "--oneline", "-n", Integer.toString(Math.max(1, limit))));
  }

  public static Result add(Path root, String[] paths) {
    ensureRepo(root);
    List<String> cmd = new ArrayList<>();
    cmd.add("add");
    if (paths == null || paths.length == 0) {
      cmd.add("-A");
    } else {
      for (String p : paths) {
        if (p != null && !p.isBlank()) {
          cmd.add(p);
        }
      }
    }
    return run(root, cmd);
  }

  public static Result commit(Path root, String[] args) {
    ensureRepo(root);
    String message = null;
    for (int i = 0; i < args.length; i++) {
      if (("-m".equals(args[i]) || "--message".equals(args[i])) && i + 1 < args.length) {
        message = args[++i];
      }
    }
    if (message == null || message.isBlank()) {
      throw new JavaException("Usage: desk git commit -m \"message\"");
    }
    return run(root, List.of("commit", "-m", message));
  }

  public static Result push(Path root) {
    ensureRepo(root);
    return run(root, List.of("push"));
  }

  public static Result pull(Path root) {
    ensureRepo(root);
    return run(root, List.of("pull", "--ff-only"));
  }

  private static void ensureRepo(Path root) {
    if (!isRepository(root)) {
      throw new JavaException("Not a git repository: " + root);
    }
  }

  private static Result run(Path root, List<String> gitArgs) {
    List<String> command = new ArrayList<>();
    command.add(gitExecutable());
    command.addAll(gitArgs);
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(root.toFile());
      pb.redirectErrorStream(true);
      // Prevent git from prompting for credentials in non-interactive contexts
      pb.environment().put("GIT_TERMINAL_PROMPT", "0");
      Process process = pb.start();
      StringBuilder out = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          out.append(sanitize(line)).append('\n');
        }
      }
      boolean finished = process.waitFor(2, TimeUnit.MINUTES);
      int code = finished ? process.exitValue() : 124;
      if (!finished) {
        process.destroyForcibly();
      }
      return new Result(code, out.toString());
    } catch (Exception e) {
      throw new JavaException("git failed: " + e.getMessage(), e);
    }
  }

  /** Redact obvious token-like substrings from git output. */
  static String sanitize(String line) {
    if (line == null) {
      return "";
    }
    return line
        .replaceAll("(?i)(token|password|authorization)=\\S+", "$1=***")
        .replaceAll("ghp_[A-Za-z0-9]+", "ghp_***")
        .replaceAll("gho_[A-Za-z0-9]+", "gho_***");
  }

  private static String gitExecutable() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
        ? "git.exe"
        : "git";
  }
}
