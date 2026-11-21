package desk.cli;

import desk.core.workspace.Workspace;
import desk.core.workspace.WorkspaceStore;
import desk.java.JavaVersionPolicy;
import desk.java.JdkDiscovery;
import desk.java.JdkInstallation;
import desk.java.JdkSelector;
import desk.java.git.GitService;
import desk.java.lsp.LanguageServerLauncher;
import desk.java.project.ProjectDetector;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code desk doctor} — aggregate environment and workspace health check.
 */
public final class DoctorCommand implements Command {

  @Override
  public String name() {
    return "doctor";
  }

  @Override
  public String description() {
    return "Diagnose Desk, JDK, project, Git, and LSP setup.";
  }

  @Override
  public CommandResult execute(String[] args) {
    System.out.println("Desk doctor");
    System.out.println("===========");
    System.out.println(JavaVersionPolicy.supportMatrixMarkdown());
    System.out.println("Desk runtime: " + System.getProperty("java.version")
        + " @ " + System.getProperty("java.home"));

    Path root = WorkspaceStore.current().map(Workspace::root)
        .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
    System.out.println("Workspace: " + root);

    var jdks = JdkDiscovery.detect();
    System.out.println("JDKs discovered: " + jdks.size());
    boolean has25 = jdks.stream().anyMatch(j ->
        j.isDevelopmentKit() && j.version().map(v -> v.matchesFeature(25)).orElse(false));
    System.out.println(has25 ? "OK: Java 25 LTS available" : "WARN: Java 25 LTS not found");
    JdkSelector.current(root).ifPresent(j -> System.out.println("Selected: " + j.summaryLine()));

    try {
      var project = ProjectDetector.detect(root);
      System.out.println("Project: " + project.summary());
    } catch (Exception e) {
      System.out.println("Project: (unable to detect) " + e.getMessage());
    }

    System.out.println("Git: " + (GitService.isRepository(root) ? "repository" : "not a git repo"));
    var ls = LanguageServerLauncher.discover();
    System.out.println("JDT LS: " + (ls.map(Path::toString).orElse("not found (javac diagnostics fallback)")));

    Path session = root.resolve(".desk").resolve("session.json");
    System.out.println("Session file: " + (Files.isRegularFile(session) ? session : "(none)"));

    // Also run java doctor tips
    new JavaCommand().execute(new String[]{"doctor"});
    return CommandResult.success();
  }
}
