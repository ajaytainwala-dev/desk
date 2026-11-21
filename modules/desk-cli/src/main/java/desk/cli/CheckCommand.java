package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;
import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.CompileResult;
import desk.java.JdkSelector;
import desk.java.JavaSourceCompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code check} command: validates Java source for compile-time errors
 * without leaving artifacts in the project tree (uses a temporary output dir).
 */
public final class CheckCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(CheckCommand.class);

  @Override
  public String name() {
    return "check";
  }

  @Override
  public String description() {
    return "Check a Java source file for errors (compile diagnostics only).";
  }

  @Override
  public String usage() {
    return "[<file> | --stdin]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      StringBuilder out = new StringBuilder();
      printHelp(out);
      System.out.print(out);
      return CommandResult.success();
    }

    if ("--stdin".equals(args[0])) {
      try {
        String source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        return checkSource(source);
      } catch (IOException e) {
        System.err.println("error: unable to read stdin: " + e.getMessage());
        return CommandResult.failure(1, "unable to read stdin");
      }
    }

    Path sourceFile = Paths.get(args[0]);
    LOG.info("check requested for file: {}", sourceFile);

    if (!FileSystem.isFile(sourceFile)) {
      System.err.println("error: source file not found: " + sourceFile);
      return CommandResult.failure(1, "source file not found");
    }

    try {
      Path tempDir = Files.createTempDirectory("desk-check-");
      try {
        Path workspace = WorkspaceStore.current().map(Workspace::root)
            .orElseGet(() -> sourceFile.toAbsolutePath().getParent());
        Path jdk = JdkSelector.current(workspace).map(j -> j.home()).orElse(null);
        CompileResult result = JavaSourceCompiler.compile(sourceFile, tempDir, new ArrayList<>(), jdk);
        if (result.success()) {
          System.out.println("ok: no errors in " + sourceFile);
          return CommandResult.success();
        }
        if (result.errorOutput() != null && !result.errorOutput().isEmpty()) {
          System.err.print(result.errorOutput());
        }
        return CommandResult.failure(1, "compilation errors");
      } finally {
        deleteRecursively(tempDir);
      }
    } catch (Exception e) {
      System.err.println("error: unable to read file " + sourceFile + ": " + e.getMessage());
      return CommandResult.failure(1, "unable to read file");
    }
  }

  private CommandResult checkSource(String source) {
    try {
      Path tempDir = Files.createTempDirectory("desk-check-");
      try {
        Path tempSource = tempDir.resolve(inferPrimarySourceFileName(source));
        FileSystem.writeString(tempSource, source);
        List<Path> classpath = new ArrayList<>();
        CompileResult result = JavaSourceCompiler.compile(tempSource, tempDir, classpath);
        if (result.success()) {
          return CommandResult.success();
        }
        if (result.errorOutput() != null && !result.errorOutput().isEmpty()) {
          System.err.print(result.errorOutput());
        }
        return CommandResult.failure(1, "compilation errors");
      } finally {
        deleteRecursively(tempDir);
      }
    } catch (Exception e) {
      LOG.error("check failed", e);
      System.err.println("error: check failed: " + e.getMessage());
      return CommandResult.failure(1, "check failed");
    }
  }

  private static String inferPrimarySourceFileName(String source) {
    Pattern pattern = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
    Matcher matcher = pattern.matcher(source);
    if (matcher.find()) {
      return matcher.group(1) + ".java";
    }
    return "Snippet.java";
  }

  private static void deleteRecursively(Path root) {
    try {
      if (Files.isDirectory(root)) {
        try (var walk = Files.walk(root)) {
          walk.sorted((a, b) -> b.compareTo(a))
              .forEach(p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // best effort temp cleanup
                }
              });
        }
      }
    } catch (IOException ignored) {
      // best effort
    }
  }
}