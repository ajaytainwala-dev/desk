package devkit.java;

import java.nio.file.Path;

/**
 * The result of running a compiled Java program.
 */
public final class RunResult {

  private final int exitCode;
  private final String output;
  private final String errorOutput;

  public RunResult(int exitCode, String output, String errorOutput) {
    this.exitCode = exitCode;
    this.output = output;
    this.errorOutput = errorOutput;
  }

  public int exitCode() {
    return exitCode;
  }

  public boolean success() {
    return exitCode == 0;
  }

  public String output() {
    return output;
  }

  public String errorOutput() {
    return errorOutput;
  }

  @Override
  public String toString() {
    return "RunResult{exitCode=" + exitCode + "}";
  }
}
