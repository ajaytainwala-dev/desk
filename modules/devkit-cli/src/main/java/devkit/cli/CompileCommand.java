package devkit.cli;

import devkit.core.filesystem.FileSystem;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;
import devkit.java.CompileResult;
import devkit.java.JavaSourceCompiler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The "compile" command: compiles a Java source file.
 *
 * <p>Compiles the given source file using the platform's javac compiler
 * and writes class files to a {@code devkit-out} directory next to the
 * source file.</p>
 */
public class CompileCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(CompileCommand.class);

  @Override
  public String name() {
    return "compile";
  }

  @Override
  public String description() {
    return "Compile a Java source file.";
  }

  @Override
  public String usage() {
    return "<file>";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      StringBuilder out = new StringBuilder();
      printHelp(out);
      System.out.print(out);
      return CommandResult.success();
    }

    Path sourceFile = Paths.get(args[0]);
    LOG.info("compile requested for file: {}", sourceFile);

    if (!FileSystem.isFile(sourceFile)) {
      System.err.println("error: source file not found: " + sourceFile);
      return CommandResult.failure(1, "source file not found");
    }

    Path outputDir = sourceFile.getParent();
    if (outputDir == null) {
      outputDir = Paths.get(".");
    }
    outputDir = outputDir.resolve("devkit-out");
    List<Path> classpath = new ArrayList<>();

    try {
      CompileResult result = JavaSourceCompiler.compile(sourceFile, outputDir, classpath);
      if (result.success()) {
        System.out.println("compiled: " + sourceFile
            + " -> " + result.outputDirectory());
        return CommandResult.success();
      } else {
        System.err.println("compilation failed: " + sourceFile);
        if (result.errorOutput() != null && !result.errorOutput().isEmpty()) {
          System.err.println(result.errorOutput());
        }
        return CommandResult.failure(1, "compilation failed");
      }
    } catch (Exception e) {
      LOG.error("compilation failed for {}", sourceFile, e);
      System.err.println("error: compilation failed: " + e.getMessage());
      return CommandResult.failure(1, "compilation failed");
    }
  }
}
