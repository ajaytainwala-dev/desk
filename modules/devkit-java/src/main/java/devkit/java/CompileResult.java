package devkit.java;

import java.nio.file.Path;

/**
 * The result of compiling Java source code.
 */
public final class CompileResult {

  private final boolean success;
  private final String output;
  private final String errorOutput;
  private final Path outputDirectory;

  public CompileResult(boolean success, String output, String errorOutput,
      Path outputDirectory) {
    this.success = success;
    this.output = output;
    this.errorOutput = errorOutput;
    this.outputDirectory = outputDirectory;
  }

  public boolean success() {
    return success;
  }

  public String output() {
    return output;
  }

  public String errorOutput() {
    return errorOutput;
  }

  public Path outputDirectory() {
    return outputDirectory;
  }

  @Override
  public String toString() {
    return "CompileResult{success=" + success
        + ", outputDirectory=" + outputDirectory + "}";
  }
}
