package desk.java.lsp;

import desk.java.JavaException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Downloads a pinned Eclipse JDT Language Server distribution into Desk's
 * managed cache. Never uses machine-specific hardcoded paths.
 */
public final class JdtLsProvisioner {

  /** Pinned milestone — update deliberately with checksum. */
  public static final String VERSION = "1.57.0";
  public static final String ARCHIVE =
      "jdt-language-server-1.57.0-202602261110.tar.gz";
  public static final String BASE_URL =
      "https://download.eclipse.org/jdtls/milestones/" + VERSION + "/";
  public static final String EXPECTED_SHA256 =
      "f7ffa93fe1bbbea95dac13dd97cdcd25c582d6e56db67258da0dcceb2302601e";

  private JdtLsProvisioner() {
  }

  public static Path defaultInstallRoot() {
    String home = System.getProperty("user.home");
    if (home == null || home.isBlank()) {
      home = System.getProperty("java.io.tmpdir");
    }
    return Path.of(home, ".desk", "jdtls", VERSION).toAbsolutePath().normalize();
  }

  public static Path launcher(Path installRoot) {
    Path unix = installRoot.resolve("bin").resolve("jdtls");
    if (Files.isRegularFile(unix)) {
      return unix;
    }
    Path win = installRoot.resolve("bin").resolve("jdtls.bat");
    if (Files.isRegularFile(win)) {
      return win;
    }
    throw new JavaException("jdtls launcher missing under " + installRoot);
  }

  /**
   * Returns existing install or downloads+extracts the pinned archive.
   */
  public static Path ensureInstalled(Path installRoot) throws Exception {
    Path root = installRoot == null ? defaultInstallRoot() : installRoot.toAbsolutePath().normalize();
    Path marker = root.resolve(".desk-jdtls-ok");
    if (Files.isRegularFile(marker)) {
      try {
        return launcher(root);
      } catch (JavaException ignored) {
        // reinstall
      }
    }
    Files.createDirectories(root.getParent());
    Path staging = Files.createTempDirectory(root.getParent(), "jdtls-staging-");
    try {
      Path archive = staging.resolve(ARCHIVE);
      downloadVerified(BASE_URL + ARCHIVE, archive, EXPECTED_SHA256);
      Path extract = staging.resolve("extract");
      Files.createDirectories(extract);
      untar(archive, extract);
      // Official archive extracts bin/ plugins/ at top level
      Path content = extract;
      if (!Files.isDirectory(content.resolve("bin"))) {
        try (Stream<Path> kids = Files.list(extract)) {
          content = kids.filter(Files::isDirectory).findFirst().orElse(extract);
        }
      }
      if (Files.exists(root)) {
        deleteRecursive(root);
      }
      Files.move(content, root);
      Files.writeString(marker, VERSION + "\n" + EXPECTED_SHA256 + "\n", StandardCharsets.UTF_8);
      Path launch = launcher(root);
      launch.toFile().setExecutable(true);
      return launch;
    } finally {
      deleteRecursive(staging);
    }
  }

  private static void downloadVerified(String url, Path dest, String expectedSha) throws Exception {
    HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMinutes(5))
        .header("User-Agent", "desk-jdtls-provisioner/0.1")
        .GET()
        .build();
    HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new JavaException("JDT LS download HTTP " + resp.statusCode() + " for " + url);
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new java.security.DigestInputStream(resp.body(), digest)) {
      Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
    }
    String actual = toHex(digest.digest());
    if (!expectedSha.equalsIgnoreCase(actual)) {
      Files.deleteIfExists(dest);
      throw new JavaException("JDT LS SHA-256 mismatch: expected " + expectedSha + " got " + actual);
    }
  }

  private static void untar(Path archive, Path dest) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(
        "tar", "-xzf", archive.toAbsolutePath().toString(), "-C", dest.toAbsolutePath().toString());
    pb.redirectErrorStream(true);
    Process p = pb.start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (p.waitFor() != 0) {
      throw new JavaException("tar extract failed: " + out);
    }
    Path destNorm = dest.toAbsolutePath().normalize();
    try (Stream<Path> walk = Files.walk(destNorm)) {
      walk.forEach(path -> {
        if (!path.toAbsolutePath().normalize().startsWith(destNorm)) {
          throw new JavaException("Tar slip: " + path);
        }
      });
    }
  }

  private static void deleteRecursive(Path root) {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
          // ignore
        }
      });
    } catch (IOException ignored) {
      // ignore
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format(Locale.ROOT, "%02x", b));
    }
    return sb.toString();
  }
}
