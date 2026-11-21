package desk.cli;

import desk.core.logging.Logger;
import desk.core.logging.LoggerFactory;
import desk.core.workspace.WorkspaceStore;

import java.nio.file.Files;
import java.nio.file.Path;

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
    registry.register(new OpenCommand());
    registry.register(new NewCommand());
    registry.register(new WorkspaceCommand());
    registry.register(new JavaCommand());
    registry.register(new FmtCommand());
    registry.register(new CheckCommand());
    registry.register(new CompileCommand());
    registry.register(new BuildCommand());
    registry.register(new TestCommand());
    registry.register(new RunCommand());
    registry.register(new GitCommand());
  }

  static int run(CommandRegistry registry, String[] args) {
    if (args == null || args.length == 0) {
      // Open cwd as workspace context, then launch TUI
      try {
        WorkspaceStore.open(Path.of("").toAbsolutePath().normalize());
      } catch (Exception e) {
        LOG.debug("workspace open skipped: {}", e.toString());
      }
      return TuiLauncher.launch();
    }

    // Global flags
    if ("--help".equals(args[0]) || "-h".equals(args[0])) {
      return execute(registry, "help", new String[0]);
    }
    if ("--version".equals(args[0]) || "-V".equals(args[0])) {
      return execute(registry, "version", new String[0]);
    }

    // `desk .` or `desk /path` → open
    if (looksLikePath(args[0]) && !registry.has(args[0])) {
      return execute(registry, "open", args);
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
      System.err.println("       Run 'desk --help' or 'desk help' for available commands.");
      LOG.warn("unknown command invoked: {}", commandName);
      return 1;
    }

    return execute(registry, commandName, commandArgs);
  }

  private static int execute(CommandRegistry registry, String commandName, String[] commandArgs) {
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

  private static boolean looksLikePath(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    if (".".equals(token) || "..".equals(token)) {
      return true;
    }
    if (token.startsWith("/") || token.startsWith("./") || token.startsWith("../")) {
      return true;
    }
    if (token.length() >= 3 && Character.isLetter(token.charAt(0)) && token.charAt(1) == ':') {
      return true;
    }
    Path path = Path.of(token);
    return Files.exists(path);
  }
}
