package desk.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of CLI commands. Commands are looked up by name, so argument
 * parsing is decoupled from the application entry point.
 */
public final class CommandRegistry {

  private final Map<String, Command> commands = new LinkedHashMap<>();

  public void register(Command command) {
    commands.put(command.name(), command);
  }

  public Command get(String name) {
    return commands.get(name);
  }

  public boolean has(String name) {
    return commands.containsKey(name);
  }

  public Map<String, Command> commands() {
    return Map.copyOf(commands);
  }

  public void printHelp(StringBuilder out) {
    out.append("Usage: desk <command> [arguments]\n\n");
    out.append("Available commands:\n");
    for (Command command : commands.values()) {
      command.printHelp(out);
    }
    out.append("\nRun 'desk <command> --help' for help on a specific command.\n");
  }
}
