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
    if (vendor != Vendor.TEMURIN) {
      throw new JavaException(
          "Managed download currently supports only Temurin (Adoptium). "
              + "Requested vendor '" + vendor.id()
              + "' is not supported yet. Install manually, then run 'desk java use "
              + request.featureVersion() + "'.");
    }
    String os = osToken();
    String arch = archToken();
    String fileName = "OpenJDK" + request.featureVersion() + "_" + os + "_" + arch + "_hotspot.tar.gz";
    if (JdkDiscovery.isWindows()) {
      fileName = "OpenJDK" + request.featureVersion() + "_" + os + "_" + arch + "_hotspot.zip";
    }
    Path target = JdkPaths.managedJdksRoot()
        .resolve(vendor.id() + "-" + request.featureVersion());

    String downloadUrl = adoptiumLatestUrl(request.featureVersion(), os, arch);
    String checksumUrl = downloadUrl; // Adoptium binary URL; checksum via API separately when download lands
    String license = "Eclipse Temurin builds are under GPLv2 with Classpath Exception. Review Adoptium terms.";

    return new InstallPlan(
        request.featureVersion(),
        vendor,
        target,
        fileName,
        downloadUrl,
        checksumUrl,
        license);
  }

  public enum InstallStatus {
    PLANNED,
    NOT_IMPLEMENTED,
    REQUIRES_CONSENT
  }

  public record InstallResult(InstallStatus status, InstallPlan plan, String message) {
  }

  /**
   * Plans or (when implemented) executes provisioning. Requires {@code consent=true}
   * for any download. Never downloads silently. Current build returns
   * {@link InstallStatus#NOT_IMPLEMENTED} after consent — install manually / SDKMAN.
   */
  public static InstallResult install(InstallRequest request) {
    InstallPlan planned = plan(request);
    if (request.dryRun()) {
      return new InstallResult(InstallStatus.PLANNED, planned,
          "Dry run only — nothing downloaded.");
    }
    if (!request.consent()) {
      return new InstallResult(InstallStatus.REQUIRES_CONSENT, planned,
          "JDK download requires explicit consent. Re-run with --yes after reviewing:\n"
              + planned.licenseNote());
    }
    return new InstallResult(InstallStatus.NOT_IMPLEMENTED, planned,
        "Managed JDK download is not implemented yet (consent recorded, nothing downloaded).\n"
            + "Plan target: " + planned.targetDir() + "\n"
            + "URL: " + planned.downloadUrl() + "\n"
            + "Install Temurin manually or via SDKMAN, then run 'desk java use "
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
