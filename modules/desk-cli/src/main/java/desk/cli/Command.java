package desk.cli;

/**
 * A single CLI command: a name, description, usage, and an executable action.
 */
public interface Command {

  /**
   * The command name (e.g. "version", "fmt", "edit").
   */
  String name();

  /**
   * A short description shown in help output.
   */
  String description();

  /**
   * Usage line shown in help output, or {@code null} to omit.
   */
  default String usage() {
    return null;
  }

  /**
   * Executes the command with the given arguments. Returns a
   * {@link CommandResult} describing the outcome.
   */
  CommandResult execute(String[] args);

  /**
   * Prints help for this command to the given appendable.
   */
  default void printHelp(StringBuilder out) {
    out.append("  ").append(name());
    if (usage() != null) {
      out.append(" ").append(usage());
    }
    out.append("\n    ").append(description()).append("\n");
  }
}
