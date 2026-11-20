package desk.cli;

/**
 * The result of executing a command: an exit code and an optional message.
 */
public final class CommandResult {

  public static final CommandResult SUCCESS = new CommandResult(0, null);

  private final int exitCode;
  private final String message;

  public CommandResult(int exitCode, String message) {
    this.exitCode = exitCode;
    this.message = message;
  }

  public int exitCode() {
    return exitCode;
  }

  public boolean isSuccess() {
    return exitCode == 0;
  }

  public String message() {
    return message;
  }

  public static CommandResult success() {
    return SUCCESS;
  }

  public static CommandResult failure(int exitCode, String message) {
    return new CommandResult(exitCode, message);
  }

  @Override
  public String toString() {
    return "CommandResult{exitCode=" + exitCode + ", message='" + message + "'}";
  }
}
