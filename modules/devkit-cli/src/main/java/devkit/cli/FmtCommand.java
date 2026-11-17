package devkit.cli;

import devkit.core.filesystem.FileSystem;
import devkit.core.logging.Logger;
import devkit.core.logging.LoggerFactory;
import devkit.formatter.Formatter;
import devkit.formatter.FormatResult;
import devkit.formatter.FormattingConfig;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The "fmt" command: formats Java source files.
 *
 * <p>Without {@code --check}, the file is formatted in place. With
 * {@code --check}, the command exits non-zero if the file is not already
 * formatted, without modifying it.</p>
 */
public class FmtCommand implements Command {

  private static final Logger LOG = LoggerFactory.getLogger(FmtCommand.class);

  private final Formatter formatter = new Formatter(FormattingConfig.DEFAULT);

  @Override
  public String name() {
    return "fmt";
  }

  @Override
  public String description() {
    return "Format a Java source file.";
  }

  @Override
  public String usage() {
    return "[--check] <file>";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      StringBuilder out = new StringBuilder();
      printHelp(out);
      System.out.print(out);
      return CommandResult.success();
    }

    boolean checkOnly = false;
    int fileIndex = 0;
    if ("--check".equals(args[0])) {
      checkOnly = true;
      fileIndex = 1;
    }

    if (fileIndex >= args.length) {
      System.err.println("error: no file specified.");
      System.err.println("       Usage: devkit fmt [--check] <file>");
      return CommandResult.failure(2, "no file specified");
    }

    Path file = Paths.get(args[fileIndex]);
    LOG.info("format requested for file: {} (checkOnly={})", file, checkOnly);

    String source;
    try {
      source = FileSystem.readString(file);
    } catch (Exception e) {
      System.err.println("error: unable to read file " + file + ": " + e.getMessage());
      return CommandResult.failure(1, "unable to read file");
    }

    FormatResult result = formatter.format(source);

    if (checkOnly) {
      if (result.changed()) {
        System.err.println("file is not formatted: " + file);
        return CommandResult.failure(1, "file is not formatted");
      }
      System.out.println("file is formatted: " + file);
      return CommandResult.success();
    }

    try {
      FileSystem.writeString(file, result.formatted());
      LOG.info("file formatted: {} ({} changed lines)", file,
          result.changedLineCount());
      System.out.println("formatted: " + file
          + " (" + result.changedLineCount() + " changed lines)");
      return CommandResult.success();
    } catch (Exception e) {
      System.err.println("error: unable to write file " + file + ": " + e.getMessage());
      return CommandResult.failure(1, "unable to write file");
    }
  }
}
