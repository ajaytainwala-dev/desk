package desk.java;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Safe process execution helpers: concurrent stdout/stderr drain, deadlines,
 * and forcible termination. Prevents classic stream-deadlock hangs.
 */
public final class ProcessIo {

  public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {
  }

  private ProcessIo() {
  }

  public static Result run(
      List<String> command,
      java.nio.file.Path workingDirectory,
      Map<String, String> extraEnv,
      long timeout,
      TimeUnit unit) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (workingDirectory != null) {
        pb.directory(workingDirectory.toFile());
      }
      pb.redirectErrorStream(false);
      if (extraEnv != null) {
        pb.environment().putAll(extraEnv);
      }
      Process process = pb.start();

      AtomicReference<String> stdoutRef = new AtomicReference<>("");
      AtomicReference<String> stderrRef = new AtomicReference<>("");
      Thread outThread = drainAsync(process.getInputStream(), stdoutRef, "desk-proc-out");
      Thread errThread = drainAsync(process.getErrorStream(), stderrRef, "desk-proc-err");

      boolean finished = process.waitFor(timeout, unit);
      if (!finished) {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
        joinQuiet(outThread, 2, TimeUnit.SECONDS);
        joinQuiet(errThread, 2, TimeUnit.SECONDS);
        return new Result(124, stdoutRef.get(), stderrRef.get() + "\nerror: process timed out\n", true);
      }
      joinQuiet(outThread, 30, TimeUnit.SECONDS);
      joinQuiet(errThread, 30, TimeUnit.SECONDS);
      return new Result(process.exitValue(), stdoutRef.get(), stderrRef.get(), false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JavaException("Process interrupted: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new JavaException("Process failed to start: " + e.getMessage(), e);
    }
  }

  private static Thread drainAsync(InputStream in, AtomicReference<String> target, String name) {
    Thread t = new Thread(() -> {
      try {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) >= 0) {
          buf.write(chunk, 0, n);
          // Cap unbounded output at 8 MiB to avoid OOM from runaway tools
          if (buf.size() > 8 * 1024 * 1024) {
            buf.write("\n...[output truncated]...\n".getBytes(StandardCharsets.UTF_8));
            break;
          }
        }
        target.set(buf.toString(StandardCharsets.UTF_8));
      } catch (Exception e) {
        target.set("");
      }
    }, name);
    t.setDaemon(true);
    t.start();
    return t;
  }

  private static void joinQuiet(Thread t, long timeout, TimeUnit unit) {
    try {
      t.join(unit.toMillis(timeout));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
