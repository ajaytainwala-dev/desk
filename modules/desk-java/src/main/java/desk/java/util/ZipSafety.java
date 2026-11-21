package desk.java.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Shared archive extraction guards (zip-slip prevention).
 */
public final class ZipSafety {

  private ZipSafety() {
  }

  /**
   * Resolves {@code entryName} under {@code destRoot}, rejecting path traversal.
   */
  public static Path resolveEntry(Path destRoot, String entryName) throws IOException {
    Path dest = destRoot.toAbsolutePath().normalize();
    Path out = dest.resolve(entryName).normalize();
    if (!out.startsWith(dest)) {
      throw new IOException("Zip slip blocked: " + entryName);
    }
    return out;
  }
}
