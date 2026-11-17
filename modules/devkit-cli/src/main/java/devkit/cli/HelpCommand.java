package devkit.cli;

import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

/**
 * The "help" command: prints usage and the list of available commands.
 */
public class HelpCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(HelpCommand.class);
  private final CommandRegistry registry;

  public HelpCommand(CommandRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() {
    return "help";
  }

  @Override
  public String description() {
    return "Print help information.";
  }

  @Override
  public String usage() {
    return "[command]";
  }

  @Override
  public CommandResult execute(String[] args) {
    StringBuilder out = new StringBuilder();
    if (args.length == 0) {
      registry.printHelp(out);
    } else {
      Command command = registry.get(args[0]);
      if (command == null) {
        out.append("Unknown command: ").append(args[0]).append("\n\n");
        registry.printHelp(out);
        System.out.print(out);
        return CommandResult.failure(1, "unknown command: " + args[0]);
      }
      command.printHelp(out);
    }
    System.out.print(out);
    LOG.debug("help command executed for args={}", java.util.Arrays.toString(args));
    return CommandResult.success();
  }
}
