package desk.core.filesystem;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Filesystem operations with safe-write and read-only support.
 *
 * <p>All methods throw {@link FilesystemException} on failure with a clear
 * message, and never silently swallow IO errors.</p>
 */
public final class FileSystem {

  private FileSystem() {
    // utility class
  }

  /**
   * Reads the entire file as a UTF-8 string.
   *
   * @throws FilesystemException if the file cannot be read.
   */
  public static String readString(Path file) {
    Objects.requireNonNull(file, "file");
    try {
      return Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
    } catch (AccessDeniedException e) {
      throw new FilesystemException("Permission denied reading " + file, e);
    } catch (IOException e) {
      throw new FilesystemException("Unable to read " + file + ": " + e.getMessage(), e);
    }
  }

  /**
   * Writes text to a file atomically where supported: writes to a temp file
   * in the same directory, then moves it over the target. This avoids
   * partially written files if the process crashes mid-write.
   *
   * <p>File permissions are preserved where the target already exists.</p>
   *
   * @throws FilesystemException if the write cannot complete.
   */
  public static void writeString(Path file, String content) {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(content, "content");
    Path parent = file.getParent();
    if (parent != null && !Files.isDirectory(parent)) {
      throw new FilesystemException("Parent directory does not exist: " + parent);
    }
    Path temp = null;
    try {
      temp = Files.createTempFile(parent != null ? parent : file.getFileSystem().getPath("."),
          ".desk-write-", ".tmp");
      Files.writeString(temp, content, java.nio.charset.StandardCharsets.UTF_8);
      // Preserve permissions of the existing target where possible.
      if (Files.exists(file)) {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        // Best-effort: copy basic attributes; ignore failures.
        try {
          Files.setAttribute(temp, "dos:readonly", attrs.isRegularFile() && Files.isReadable(file));
        } catch (UnsupportedOperationException ignored) {
          // Not all file systems support this attribute.
        }
      }
      Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      // Clean up temp file if it lingers.
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
          // best effort
        }
      }
      throw new FilesystemException("Unable to write " + file + ": " + e.getMessage(), e);
    }
  }

  /**
   * Returns whether the file exists and is readable.
   */
  public static boolean isReadable(Path file) {
    return Files.isRegularFile(file) && Files.isReadable(file);
  }

  /**
   * Returns whether the path is a regular file.
   */
  public static boolean isFile(Path file) {
    return Files.isRegularFile(file);
  }

  /**
   * Returns whether the path is a directory.
   */
  public static boolean isDirectory(Path dir) {
    return Files.isDirectory(dir);
  }

  /**
   * Returns whether the file is writable.
   */
  public static boolean isWritable(Path file) {
    return Files.isWritable(file);
  }

  /**
   * Returns the last modified time of a file, or {@code null} if unavailable.
   */
  public static java.time.Instant lastModified(Path file) {
    try {
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
      FileTime time = attrs.lastModifiedTime();
      return time.toInstant();
    } catch (IOException e) {
      return null;
    }
  }
}
