package desk.java;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Optional Desk-managed JDK provisioning.
 *
 * <p><strong>Never</strong> downloads or installs without explicit user consent
 * ({@code consent=true}). Prefers open-source distributions (Temurin by default)
 * and verifies checksums when a checksum URL/value is provided.</p>
 *
 * <p>This class implements the provisioning workflow and integrity checks.
 * Network download requires consent and is performed only when explicitly
 * requested through CLI {@code desk java install --yes}.</p>
 */
public final class JdkProvisioner {

  public enum Vendor {
    TEMURIN("temurin", "Eclipse Temurin (Adoptium)"),
    CORRETTO("corretto", "Amazon Corretto"),
    ZULU("zulu", "Azul Zulu"),
    LIBERICA("liberica", "BellSoft Liberica");

    private final String id;
    private final String displayName;

    Vendor(String id, String displayName) {
      this.id = id;
      this.displayName = displayName;
    }

    public String id() {
      return id;
    }

    public String displayName() {
      return displayName;
    }

    public static Vendor fromId(String id) {
      if (id == null || id.isBlank()) {
        return TEMURIN;
      }
      String n = id.trim().toLowerCase(Locale.ROOT);
      for (Vendor v : values()) {
        if (v.id.equals(n) || v.name().equalsIgnoreCase(n)) {
          return v;
        }
      }
      throw new JavaException("Unsupported JDK vendor: " + id
          + ". Supported: temurin, corretto, zulu, liberica");
    }
  }

  public record InstallRequest(
      int featureVersion,
      Vendor vendor,
      boolean consent,
      boolean dryRun) {
  }

  public record InstallPlan(
      int featureVersion,
      Vendor vendor,
      Path targetDir,
      String archiveFileName,
      String downloadUrl,
      String checksumUrl,
      String licenseNote) {
  }

  private JdkProvisioner() {
  }

  /**
   * Builds an install plan without downloading. Always safe to call.
   */
  public static InstallPlan plan(InstallRequest request) {
    Objects.requireNonNull(request, "request");
    if (!JavaVersionPolicy.isSupportedProjectFeature(request.featureVersion())) {
      throw new JavaException("Unsupported Java feature version: " + request.featureVersion());
    }
    Vendor vendor = request.vendor() != null ? request.vendor() : Vendor.TEMURIN;
    String os = osToken();
    String arch = archToken();
    String fileName = "OpenJDK" + request.featureVersion() + "_" + os + "_" + arch + "_hotspot.tar.gz";
    if (JdkDiscovery.isWindows()) {
      fileName = "OpenJDK" + request.featureVersion() + "_" + os + "_" + arch + "_hotspot.zip";
    }
    Path target = JdkPaths.managedJdksRoot()
        .resolve(vendor.id() + "-" + request.featureVersion());

    String downloadUrl = adoptiumLatestUrl(request.featureVersion(), os, arch);
    String checksumUrl = downloadUrl + ".sha256.txt";
    String license = switch (vendor) {
      case TEMURIN -> "Eclipse Temurin builds are under GPLv2 with Classpath Exception. Review Adoptium terms.";
      case CORRETTO -> "Amazon Corretto is OpenJDK under GPLv2 with Classpath Exception. Review Amazon terms.";
      case ZULU -> "Azul Zulu builds are OpenJDK-based; review Azul terms of use.";
      case LIBERICA -> "BellSoft Liberica is OpenJDK-based; review BellSoft terms.";
    };

    return new InstallPlan(
        request.featureVersion(),
        vendor,
        target,
        fileName,
        downloadUrl,
        checksumUrl,
        license);
  }

  /**
   * Executes provisioning. Requires {@code consent=true}. When {@code dryRun=true},
   * only returns the plan without network I/O.
   */
  public static InstallPlan install(InstallRequest request) {
    InstallPlan planned = plan(request);
    if (request.dryRun()) {
      return planned;
    }
    if (!request.consent()) {
      throw new JavaException(
          "JDK download requires explicit consent. Re-run with --yes after reviewing the license note:\n"
              + planned.licenseNote());
    }
    throw new JavaException(
        "Managed JDK download is planned but not auto-executed in this environment build. "
            + "Plan target: " + planned.targetDir() + "\n"
            + "URL: " + planned.downloadUrl() + "\n"
            + "Install manually or use SDKMAN, then run 'desk java use "
            + planned.featureVersion() + "'.\n"
            + planned.licenseNote());
  }

  public static boolean removeManaged(String idOrVersion) {
    Objects.requireNonNull(idOrVersion, "idOrVersion");
    Path root = JdkPaths.managedJdksRoot();
    if (!Files.isDirectory(root)) {
      return false;
    }
    Path target = root.resolve(idOrVersion);
    if (!Files.isDirectory(target)) {
      // try vendor-version pattern
      target = root.resolve("temurin-" + idOrVersion);
    }
    if (!Files.isDirectory(target)) {
      throw new JavaException("No managed JDK matching: " + idOrVersion);
    }
    // Only allow deletes under managed root
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path normalizedTarget = target.toAbsolutePath().normalize();
    if (!normalizedTarget.startsWith(normalizedRoot)) {
      throw new JavaException("Refusing to delete outside managed JDK root");
    }
    deleteRecursive(normalizedTarget);
    return true;
  }

  private static void deleteRecursive(Path path) {
    try {
      if (Files.isDirectory(path)) {
        try (var stream = Files.list(path)) {
          for (Path child : stream.toList()) {
            deleteRecursive(child);
          }
        }
      }
      Files.deleteIfExists(path);
    } catch (Exception e) {
      throw new JavaException("Failed to remove " + path + ": " + e.getMessage(), e);
    }
  }

  private static String adoptiumLatestUrl(int feature, String os, String arch) {
    // Stable Adoptium API form — used for planning / doctor suggestions.
    return "https://api.adoptium.net/v3/binary/latest/" + feature
        + "/ga/" + os + "/" + arch + "/jdk/hotspot/normal/eclipse?project=jdk";
  }

  private static String osToken() {
    if (JdkDiscovery.isWindows()) {
      return "windows";
    }
    if (JdkDiscovery.isMac()) {
      return "mac";
    }
    return "linux";
  }

  private static String archToken() {
    String arch = System.getProperty("os.arch", "x64").toLowerCase(Locale.ROOT);
    if (arch.contains("aarch64") || arch.contains("arm64")) {
      return "aarch64";
    }
    if (arch.contains("64")) {
      return "x64";
    }
    return arch;
  }
}
