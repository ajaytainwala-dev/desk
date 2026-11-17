package devkit.cli;

import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

/**
 * Application entry point. Parses arguments and dispatches to the
 * appropriate command from the registry.
 *
 * <p>No business logic lives here; it only routes to commands.</p>
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {
    // utility class
  }

  public static void main(String[] args) {
    CommandRegistry registry = new CommandRegistry();
    bootstrap(registry);

    int exitCode = run(registry, args);
    System.exit(exitCode);
  }

  private static void bootstrap(CommandRegistry registry) {
    registry.register(new VersionCommand());
    registry.register(new HelpCommand(registry));
    registry.register(new EditCommand());
    registry.register(new FmtCommand());
    registry.register(new CompileCommand());
    registry.register(new RunCommand());
  }

  private static int run(CommandRegistry registry, String[] args) {
    if (args == null || args.length == 0) {
      // No command: print help and suggest the available commands.
      StringBuilder out = new StringBuilder();
      out.append(AppVersion.fullVersion()).append("\n\n");
      registry.printHelp(out);
      System.out.print(out);
      return 0;
    }

    String commandName = args[0];
    String[] commandArgs;
    if (args.length > 1) {
      commandArgs = new String[args.length - 1];
      System.arraycopy(args, 1, commandArgs, 0, args.length - 1);
    } else {
      commandArgs = new String[0];
    }

    if (!registry.has(commandName)) {
      System.err.println("error: unknown command '" + commandName + "'.");
      System.err.println("       Run 'devkit --help' or 'devkit help' for available commands.");
      LOG.warn("unknown command invoked: {}", commandName);
      return 1;
    }

    Command command = registry.get(commandName);
    try {
      CommandResult result = command.execute(commandArgs);
      if (result.message() != null && !result.isSuccess()) {
        System.err.println(result.message());
      }
      return result.exitCode();
    } catch (Exception e) {
      LOG.error("command '{}' failed", commandName, e);
      System.err.println("error: command '" + commandName + "' failed: " + e.getMessage());
      return 1;
    }
  }
}
