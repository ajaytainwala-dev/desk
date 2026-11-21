package desk.cli;

import desk.core.workspace.WorkspaceStore;
import desk.java.JavaVersionPolicy;
import desk.java.project.ProjectTemplates;

import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code desk new <name>} — create a project from a template.
 */
public final class NewCommand implements Command {

  @Override
  public String name() {
    return "new";
  }

  @Override
  public String description() {
    return "Create a new Java project from a template (default Java "
        + JavaVersionPolicy.DEFAULT_PROJECT_RELEASE + ").";
  }

  @Override
  public String usage() {
    return "<name> [--template basic|console|library|maven|gradle|testing] [--java <ver>]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
      System.out.println("""
          Usage: desk new <name> [options]

            --template <id>   basic|console|library|maven|gradle|testing (default: basic)
            --java <ver>      Project Java version (default: 25 LTS)
            --group <id>      Maven/Gradle groupId
            --package <name>  Base package name
            --main <Class>    Main class name (default: Main)
          """);
      return CommandResult.success();
    }
    try {
      String name = null;
      String template = "basic";
      int javaVer = JavaVersionPolicy.DEFAULT_PROJECT_RELEASE;
      String group = null;
      String pkg = null;
      String main = "Main";
      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        if ("--template".equals(a) && i + 1 < args.length) {
          template = args[++i];
        } else if ("--java".equals(a) && i + 1 < args.length) {
          javaVer = Integer.parseInt(args[++i]);
        } else if ("--group".equals(a) && i + 1 < args.length) {
          group = args[++i];
        } else if ("--package".equals(a) && i + 1 < args.length) {
          pkg = args[++i];
        } else if ("--main".equals(a) && i + 1 < args.length) {
          main = args[++i];
        } else if (!a.startsWith("-")) {
          name = a;
        }
      }
      if (name == null) {
        return CommandResult.failure(1, "Project name required");
      }
      Path created = ProjectTemplates.create(new ProjectTemplates.CreateRequest(
          Path.of("").toAbsolutePath().normalize(),
          name,
          group,
          name.toLowerCase(Locale.ROOT),
          pkg,
          javaVer,
          ProjectTemplates.Template.fromId(template),
          main));
      WorkspaceStore.open(created);
      System.out.println("Created project: " + created);
      System.out.println("Java version: " + JavaVersionPolicy.describe(javaVer));
      System.out.println("Template: " + template);
      System.out.println("Open with: desk open " + created);
      return CommandResult.success();
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }
}
