package devkit.cli;

import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;

/**
 * The "version" command: prints the application version and exits.
 */
public class VersionCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(VersionCommand.class);

  @Override
  public String name() {
    return "version";
  }

  @Override
  public String description() {
    return "Print the application version.";
  }

  @Override
  public String usage() {
    return "[--short]";
  }

  @Override
  public CommandResult execute(String[] args) {
    boolean shortForm = false;
    for (String arg : args) {
      if ("--help".equals(arg) || "-h".equals(arg)) {
        printHelp(new StringBuilder());
        return CommandResult.success();
      }
      if ("--short".equals(arg)) {
        shortForm = true;
      }
    }
    if (shortForm) {
      System.out.println(AppVersion.VERSION);
    } else {
      System.out.println(AppVersion.fullVersion());
    }
    LOG.debug("version command executed (short={})", shortForm);
    return CommandResult.success();
  }
}
