package desk.java.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Cross-platform process-tree termination. Unix uses {@link ProcessHandle}
 * descendants; Windows additionally attempts {@code taskkill /T} for stubborn trees.
 */
public final class ProcessTreeKiller {

  private ProcessTreeKiller() {
  }

  public static void terminate(Process process, long gracefulMs) {
    if (process == null || !process.isAlive()) {
      return;
    }
    try {
      process.toHandle().descendants().forEach(ProcessHandle::destroy);
    } catch (Exception ignored) {
      // ignore
    }
    process.destroy();
    try {
      if (!process.waitFor(gracefulMs, TimeUnit.MILLISECONDS)) {
        force(process);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      force(process);
    }
  }

  public static void force(Process process) {
    if (process == null) {
      return;
    }
    long pid = process.pid();
    try {
      process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    } catch (Exception ignored) {
      // ignore
    }
    process.destroyForcibly();
    if (isWindows()) {
      taskkillTree(pid);
    }
  }

  static void taskkillTree(long pid) {
    List<String> cmd = new ArrayList<>();
    cmd.add("taskkill");
    cmd.add("/PID");
    cmd.add(Long.toString(pid));
    cmd.add("/T");
    cmd.add("/F");
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      p.waitFor(5, TimeUnit.SECONDS);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
