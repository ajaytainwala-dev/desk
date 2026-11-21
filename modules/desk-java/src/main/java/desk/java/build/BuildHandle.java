package desk.java.build;

import desk.java.JavaException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancellable build process. Poll {@link #liveOutput()} for streaming UI;
 * call {@link #cancel()} to stop; {@link #await} for the final result.
 */
public final class BuildHandle {

  private final Process process;
  private final BuildSystem system;
  private final Path cwd;
  private final long startedAt = System.currentTimeMillis();
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<String> stdout;
  private final AtomicReference<String> stderr;
  private final Thread outThread;
  private final Thread errThread;

  private BuildHandle(Process process, BuildSystem system, Path cwd,
      AtomicReference<String> stdout, AtomicReference<String> stderr,
      Thread outThread, Thread errThread) {
    this.process = process;
    this.system = system;
    this.cwd = cwd;
    this.stdout = stdout;
    this.stderr = stderr;
    this.outThread = outThread;
    this.errThread = errThread;
  }

  public static BuildHandle start(List<String> command, Path cwd,
      Map<String, String> env, BuildSystem system) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(system, "system");
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (cwd != null) {
        pb.directory(cwd.toFile());
      }
      if (env != null) {
        pb.environment().putAll(env);
      }
      Process process = pb.start();
      AtomicReference<String> out = new AtomicReference<>("");
      AtomicReference<String> err = new AtomicReference<>("");
      Thread ot = drain(process.getInputStream(), out, "desk-build-out");
      Thread et = drain(process.getErrorStream(), err, "desk-build-err");
      return new BuildHandle(process, system, cwd, out, err, ot, et);
    } catch (Exception e) {
      throw new JavaException("Build failed to start: " + e.getMessage(), e);
    }
  }

  public boolean cancel() {
    if (!cancelled.compareAndSet(false, true)) {
      return false;
    }
    process.destroy();
    try {
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
    return true;
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  public String liveOutput() {
    return stdout.get() + stderr.get();
  }

  public BuildResult await(long timeout, TimeUnit unit) {
    try {
      boolean finished = process.waitFor(timeout, unit);
      if (!finished) {
        cancel();
        joinQuiet(outThread);
        joinQuiet(errThread);
        String combined = liveOutput() + "\nerror: build timed out\n";
        return result(124, combined);
      }
      joinQuiet(outThread);
      joinQuiet(errThread);
      if (cancelled.get()) {
        return result(130, liveOutput() + "\nerror: build cancelled\n");
      }
      return result(process.exitValue(), liveOutput());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      cancel();
      throw new JavaException("Build interrupted", e);
    }
  }

  public CompletableFuture<BuildResult> awaitAsync(long timeout, TimeUnit unit) {
    return CompletableFuture.supplyAsync(() -> await(timeout, unit));
  }

  private BuildResult result(int code, String output) {
    boolean ok = code == 0 && !cancelled.get();
    return new BuildResult(ok, code, system, output,
        BuildService.parseDiagnostics(output, cwd, system),
        System.currentTimeMillis() - startedAt);
  }

  private static Thread drain(InputStream in, AtomicReference<String> target, String name) {
    Thread t = new Thread(() -> {
      try {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) >= 0) {
          buf.write(chunk, 0, n);
          target.set(buf.toString(StandardCharsets.UTF_8));
          if (buf.size() > 8 * 1024 * 1024) {
            break;
          }
        }
      } catch (Exception ignored) {
        // ignore
      }
    }, name);
    t.setDaemon(true);
    t.start();
    return t;
  }

  private static void joinQuiet(Thread t) {
    if (t == null) {
      return;
    }
    try {
      t.join(TimeUnit.SECONDS.toMillis(30));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
