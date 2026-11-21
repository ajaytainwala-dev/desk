package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JavaVersionPolicy;
import desk.java.JdkDiscovery;
import desk.java.JdkInstallation;
import desk.java.JdkProvisioner;
import desk.java.JdkSelector;
import desk.java.ToolchainContext;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code desk java ...} — JDK discovery, selection, and doctor.
 */
public final class JavaCommand implements Command {

  @Override
  public String name() {
    return "java";
  }

  @Override
  public String description() {
    return "Manage JDK discovery and workspace-scoped Java toolchains.";
  }

  @Override
  public String usage() {
    return "<list|detect|current|use|info|doctor|install|remove> [args]";
  }

  @Override
  public CommandResult execute(String[] args) {
    if (args.length == 0 || isHelp(args[0])) {
      printHelp();
      return CommandResult.success();
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    String[] rest = slice(args, 1);
    try {
      return switch (sub) {
        case "list" -> list(false);
        case "detect" -> list(true);
        case "current" -> current();
        case "use" -> use(rest);
        case "info" -> info(rest);
        case "doctor" -> doctor();
        case "install" -> install(rest);
        case "remove" -> remove(rest);
        case "policy" -> policy();
        default -> CommandResult.failure(1, "Unknown java subcommand: " + sub
            + ". Try 'desk java' for help.");
      };
    } catch (Exception e) {
      return CommandResult.failure(1, e.getMessage());
    }
  }

  private CommandResult list(boolean detectLabel) {
    List<JdkInstallation> installs = JdkDiscovery.detect();
    installs = installs.stream()
        .sorted(Comparator.comparing((JdkInstallation j) -> j.version().orElse(null),
            Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
    if (installs.isEmpty()) {
      System.out.println("No Java installations discovered.");
      return CommandResult.success();
    }
    System.out.println(detectLabel ? "Detected Java installations:" : "Java installations:");
    for (JdkInstallation j : installs) {
      System.out.println("  " + j.summaryLine());
    }
    System.out.println();
    System.out.println("Primary LTS baseline: Java " + JavaVersionPolicy.PRIMARY_LTS);
    return CommandResult.success();
  }

  private CommandResult current() {
    Path root = WorkspaceStore.current().map(Workspace::root).orElse(cwd());
    Optional<JdkInstallation> current = JdkSelector.current(root);
    if (current.isEmpty()) {
      return CommandResult.failure(1, "No JDK selected or discovered.");
    }
    System.out.println("Workspace: " + root);
    System.out.println(current.get().summaryLine());
    System.out.println();
    System.out.println(ToolchainContext.forWorkspace(root).describe());
    return CommandResult.success();
  }

  private CommandResult use(String[] args) {
    if (args.length == 0) {
      return CommandResult.failure(1, "Usage: desk java use <version|path> [--user]");
    }
    boolean user = false;
    String token = null;
    for (String arg : args) {
      if ("--user".equals(arg)) {
        user = true;
      } else if (!arg.startsWith("-")) {
        token = arg;
      }
    }
    if (token == null) {
      return CommandResult.failure(1, "Usage: desk java use <version|path> [--user]");
    }
    JdkInstallation selected;
    if (user) {
      selected = JdkSelector.useUserDefault(token);
      System.out.println("User default JDK set (system JAVA_HOME unchanged):");
    } else {
      Path root = WorkspaceStore.current().map(Workspace::root).orElseGet(() -> {
        Workspace ws = WorkspaceStore.open(cwd());
        return ws.root();
      });
      selected = JdkSelector.useForWorkspace(root, token);
      System.out.println("Workspace JDK set for " + root + " (system JAVA_HOME unchanged):");
    }
    System.out.println("  " + selected.summaryLine());
    return CommandResult.success();
  }

  private CommandResult info(String[] args) {
    if (args.length == 0) {
      return current();
    }
    JdkInstallation install = JdkSelector.resolveSelection(args[0]);
    System.out.println(install.summaryLine());
    System.out.println("home:      " + install.home());
    System.out.println("vendor:    " + install.vendor());
    System.out.println("arch:      " + install.architecture());
    System.out.println("kind:      " + install.kind());
    System.out.println("source:    " + install.source());
    System.out.println("valid:     " + install.valid());
    System.out.println("is JDK:    " + install.isDevelopmentKit());
    install.version().ifPresent(v -> {
      System.out.println("feature:   " + v.feature());
      System.out.println("LTS:       " + v.isLts());
      System.out.println("policy:    " + JavaVersionPolicy.describe(v.feature()));
    });
    if (!install.errors().isEmpty()) {
      System.out.println("issues:");
      for (String err : install.errors()) {
        System.out.println("  - " + err);
      }
    }
    return CommandResult.success();
  }

  private CommandResult doctor() {
    System.out.println("Desk Java doctor");
    System.out.println("================");
    System.out.println(JavaVersionPolicy.supportMatrixMarkdown());
    System.out.println("Desk runtime: " + System.getProperty("java.version")
        + " @ " + System.getProperty("java.home"));
    Path root = WorkspaceStore.current().map(Workspace::root).orElse(cwd());
    System.out.println("Workspace: " + root);
    List<JdkInstallation> installs = JdkDiscovery.detect();
    long jdks = installs.stream().filter(JdkInstallation::isDevelopmentKit).count();
    long invalid = installs.stream().filter(j -> !j.valid()).count();
    System.out.println("Discovered installations: " + installs.size()
        + " (JDKs: " + jdks + ", invalid: " + invalid + ")");
    boolean has25 = installs.stream().anyMatch(j ->
        j.isDevelopmentKit() && j.version().map(v -> v.matchesFeature(25)).orElse(false));
    if (!has25) {
      System.out.println("WARNING: Primary LTS Java 25 not found. "
          + "Install Temurin 25 or run 'desk java install 25 --yes' when available.");
    } else {
      System.out.println("OK: Java 25 LTS JDK available.");
    }
    JdkSelector.current(root).ifPresentOrElse(
        j -> System.out.println("Selected: " + j.summaryLine()),
        () -> System.out.println("WARNING: No selected JDK."));
    String javaHome = System.getenv("JAVA_HOME");
    System.out.println("JAVA_HOME=" + (javaHome != null ? javaHome : "(unset)"));
    System.out.println();
    System.out.println("Tips:");
    System.out.println("  desk java list");
    System.out.println("  desk java use 25");
    System.out.println("  desk java use 27");
    return CommandResult.success();
  }

  private CommandResult install(String[] args) {
    if (args.length == 0) {
      return CommandResult.failure(1, "Usage: desk java install <version> [--vendor temurin] [--yes] [--dry-run]");
    }
    int feature;
    try {
      feature = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      return CommandResult.failure(1, "Version must be a feature number, e.g. 25");
    }
    boolean yes = false;
    boolean dryRun = false;
    String vendor = "temurin";
    for (int i = 1; i < args.length; i++) {
      if ("--yes".equals(args[i]) || "-y".equals(args[i])) {
        yes = true;
      } else if ("--dry-run".equals(args[i])) {
        dryRun = true;
      } else if ("--vendor".equals(args[i]) && i + 1 < args.length) {
        vendor = args[++i];
      }
    }
    var result = JdkProvisioner.install(new JdkProvisioner.InstallRequest(
        feature, JdkProvisioner.Vendor.fromId(vendor), yes, dryRun));
    var plan = result.plan();
    System.out.println("Install status: " + result.status());
    System.out.println("Install plan:");
    System.out.println("  version:  " + plan.featureVersion()
        + (JavaVersionPolicy.isLts(plan.featureVersion()) ? " LTS" : " (non-LTS)"));
    System.out.println("  vendor:   " + plan.vendor().displayName());
    System.out.println("  target:   " + plan.targetDir());
    System.out.println("  url:      " + plan.downloadUrl());
    System.out.println("  checksum: " + plan.checksumUrl());
    System.out.println("  license:  " + plan.licenseNote());
    System.out.println(result.message());
    return switch (result.status()) {
      case PLANNED, INSTALLED -> CommandResult.success();
      case REQUIRES_CONSENT, FAILED ->
          CommandResult.failure(1, result.status().name());
    };
  }

  private CommandResult remove(String[] args) {
    if (args.length == 0) {
      return CommandResult.failure(1, "Usage: desk java remove <managed-id|version>");
    }
    boolean removed = JdkProvisioner.removeManaged(args[0]);
    System.out.println(removed ? "Removed managed JDK " + args[0]
        : "Nothing removed.");
    return CommandResult.success();
  }

  private CommandResult policy() {
    System.out.println("Desk Java version policy");
    System.out.println(JavaVersionPolicy.supportMatrixMarkdown());
    return CommandResult.success();
  }

  private void printHelp() {
    System.out.println("""
        Usage: desk java <subcommand>

          list                 List discovered Java installations
          detect               Re-scan and list installations
          current              Show JDK selected for the current workspace
          use <ver|path>       Pin JDK for this workspace (not system JAVA_HOME)
          use <ver|path> --user  Set user default JDK pin
          info [ver|path]      Show details for a JDK
          doctor               Diagnose toolchain setup
          install <ver> [--vendor temurin] [--yes] [--dry-run]
                               Plan/consent-based managed JDK install
          remove <id>          Remove a Desk-managed JDK
          policy               Print version support matrix

        Examples:
          desk java use 25
          desk java use 27
          desk java use /path/to/jdk
          desk java install 25 --dry-run
        """);
  }

  private static boolean isHelp(String arg) {
    return "--help".equals(arg) || "-h".equals(arg) || "help".equals(arg);
  }

  private static String[] slice(String[] args, int from) {
    if (args.length <= from) {
      return new String[0];
    }
    String[] rest = new String[args.length - from];
    System.arraycopy(args, from, rest, 0, rest.length);
    return rest;
  }

  private static Path cwd() {
    return Path.of("").toAbsolutePath().normalize();
  }
}
