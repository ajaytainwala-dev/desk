package desk.cli;

import desk.core.filesystem.FileSystem;
import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;
import desk.java.JavaRunner;
import desk.java.RunResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The "run" command: runs a compiled Java class.
 *
 * <p>Runs the compiled class derived from the given source file (or a class
 * name if it doesn't end in {@code .java}) using the classpath from the
 * {@code desk-out} directory next to the source file.</p>
 */
public class RunCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(RunCommand.class);

  @Override
  public String name() {
    return "run";
  }

  @Override
  public String description() {
    return "Run a compiled Java class.";
  }

  @Override
  public String usage() {
    return "<class-or-file> [args...]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      StringBuilder out = new StringBuilder();
      printHelp(out);
      System.out.print(out);
      return CommandResult.success();
    }

    String target = args[0];
    List<String> programArgs = new ArrayList<>();
    if (args.length > 1) {
      for (int i = 1; i < args.length; i++) {
        programArgs.add(args[i]);
      }
    }

    LOG.info("run requested for target: {}", target);

    Path sourceFile = Paths.get(target);
    String className;
    Path outputDir;

    if (target.endsWith(".java") && FileSystem.isFile(sourceFile)) {
      // Derive class name from file name (strip .java)
      className = sourceFile.getFileName().toString()
          .substring(0, sourceFile.getFileName().toString().length() - 5);
      Path parent = sourceFile.getParent();
      outputDir = (parent == null ? Paths.get(".") : parent).resolve("desk-out");
    } else {
      className = target;
      outputDir = Paths.get("desk-out");
    }

    List<Path> classpath = new ArrayList<>();
    classpath.add(outputDir);

    try {
      RunResult result = JavaRunner.run(className, classpath, programArgs);
      if (result.output() != null && !result.output().isEmpty()) {
        System.out.print(result.output());
      }
      if (result.errorOutput() != null && !result.errorOutput().isEmpty()) {
        System.err.print(result.errorOutput());
      }
      if (result.success()) {
        return CommandResult.success();
      } else {
        return CommandResult.failure(result.exitCode(),
            "program exited with code " + result.exitCode());
      }
    } catch (Exception e) {
      LOG.error("execution failed for {}", target, e);
      System.err.println("error: execution failed: " + e.getMessage());
      return CommandResult.failure(1, "execution failed");
    }
  }
}
