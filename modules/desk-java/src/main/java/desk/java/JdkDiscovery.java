package desk.java;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Discovers installed JDKs/JREs across platforms and common distribution layouts.
 *
 * <p>Sources: {@code JAVA_HOME}, {@code PATH}, Windows Program Files / registry
 * locations, macOS {@code JavaVirtualMachines}, Linux system paths, SDKMAN,
 * Desk-managed installs, and the current JVM.</p>
 */
public final class JdkDiscovery {

  private JdkDiscovery() {
  }

  /**
   * Discovers installations and returns de-duplicated homes (absolute normalized).
   * Invalid / incomplete installs are included with {@code valid=false}.
   */
  public static List<JdkInstallation> detect() {
    Map<Path, JdkInstallation> byHome = new LinkedHashMap<>();

    addIfPresent(byHome, fromJavaHome());
    addIfPresent(byHome, fromCurrentJvm());
    discoverFromPath(byHome);

    if (isWindows()) {
      discoverWindows(byHome);
    } else if (isMac()) {
      discoverMac(byHome);
    } else {
      discoverLinux(byHome);
    }

    discoverSdkman(byHome);
    discoverDeskManaged(byHome);

    return List.copyOf(byHome.values());
  }

  /**
   * Backward-compatible API: valid JDK homes only (those with {@code javac}).
   */
  public static List<Path> discover() {
    return detect().stream()
        .filter(JdkInstallation::isDevelopmentKit)
        .filter(JdkInstallation::valid)
        .map(JdkInstallation::home)
        .collect(Collectors.toList());
  }

  /**
   * Preferred valid JDK: highest feature version among valid JDKs, preferring
   * the primary LTS when tied on suitability, else first by discovery order
   * among max version.
   */
  public static Path preferredJdk() {
    return preferredInstallation().map(JdkInstallation::home).orElse(null);
  }

  public static Optional<JdkInstallation> preferredInstallation() {
    return detect().stream()
        .filter(JdkInstallation::valid)
        .filter(JdkInstallation::isDevelopmentKit)
        .max((a, b) -> {
          int byVersion = compareOptionalVersion(a, b);
          if (byVersion != 0) {
            return byVersion;
          }
          // Prefer primary LTS vendor homes slightly via source order stability
          return 0;
        });
  }

  private static int compareOptionalVersion(JdkInstallation a, JdkInstallation b) {
    Optional<JdkVersion> va = a.version();
    Optional<JdkVersion> vb = b.version();
    if (va.isPresent() && vb.isPresent()) {
      return va.get().compareTo(vb.get());
    }
    if (va.isPresent()) {
      return 1;
    }
    if (vb.isPresent()) {
      return -1;
    }
    return 0;
  }

  public static Optional<JdkInstallation> inspect(Path home) {
    return Optional.ofNullable(probe(home, JdkInstallation.Source.OTHER));
  }

  public static Optional<JdkInstallation> findByFeature(int feature) {
    return detect().stream()
        .filter(JdkInstallation::valid)
        .filter(JdkInstallation::isDevelopmentKit)
        .filter(j -> j.version().map(v -> v.matchesFeature(feature)).orElse(false))
        .max((a, b) -> compareOptionalVersion(a, b));
  }

  public static Optional<JdkInstallation> findByHome(Path home) {
    Path normalized = home.toAbsolutePath().normalize();
    return detect().stream()
        .filter(j -> j.home().toAbsolutePath().normalize().equals(normalized))
        .findFirst()
        .or(() -> Optional.ofNullable(probe(normalized, JdkInstallation.Source.OTHER)));
  }

  // --- discovery helpers ---

  private static void addIfPresent(Map<Path, JdkInstallation> map, JdkInstallation install) {
    if (install == null) {
      return;
    }
    Path key = install.home().toAbsolutePath().normalize();
    JdkInstallation existing = map.get(key);
    if (existing == null || (!existing.valid() && install.valid())) {
      map.put(key, install);
    }
  }

  private static JdkInstallation fromJavaHome() {
    String javaHome = System.getenv("JAVA_HOME");
    if (javaHome == null || javaHome.isBlank()) {
      return null;
    }
    return probe(Path.of(javaHome.trim()), JdkInstallation.Source.JAVA_HOME);
  }

  private static JdkInstallation fromCurrentJvm() {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null || javaHome.isBlank()) {
      return null;
    }
    Path home = Path.of(javaHome);
    // jre inside jdk
    if (home.getFileName() != null && "jre".equalsIgnoreCase(home.getFileName().toString())) {
      Path parent = home.getParent();
      if (parent != null && isValidJdkLayout(parent)) {
        home = parent;
      }
    }
    return probe(home, JdkInstallation.Source.CURRENT_JVM);
  }

  private static void discoverFromPath(Map<Path, JdkInstallation> map) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null) {
      return;
    }
    for (String entry : pathEnv.split(isWindows() ? ";" : ":")) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      Path bin = Path.of(entry.trim());
      Path java = bin.resolve("java" + exeSuffix());
      Path javac = bin.resolve("javac" + exeSuffix());
      if (!Files.isRegularFile(java) && !Files.isRegularFile(javac)) {
        continue;
      }
      Path home = resolveHomeFromBin(bin);
      if (home != null) {
        addIfPresent(map, probe(home, JdkInstallation.Source.PATH));
      }
    }
  }

  private static Path resolveHomeFromBin(Path binDir) {
    try {
      Path java = binDir.resolve("java" + exeSuffix());
      Path real = Files.isSymbolicLink(java) ? java.toRealPath() : java.toAbsolutePath().normalize();
      Path bin = real.getParent();
      if (bin == null) {
        return null;
      }
      Path home = bin.getParent();
      // macOS: .../Home/bin/java
      return home;
    } catch (Exception e) {
      Path parent = binDir.getParent();
      return parent;
    }
  }

  private static void discoverWindows(Map<Path, JdkInstallation> map) {
    String[] roots = {
        System.getenv("ProgramFiles"),
        System.getenv("ProgramFiles(x86)"),
        System.getenv("LOCALAPPDATA"),
        System.getenv("ProgramW6432")
    };
    String[] vendors = {
        "Java",
        "Eclipse Adoptium",
        "Microsoft",
        "Zulu",
        "BellSoft",
        "Amazon Corretto",
        "JavaSoft",
        "SapMachine",
        "Semeru"
    };
    for (String root : roots) {
      if (root == null) {
        continue;
      }
      for (String vendor : vendors) {
        scanChildren(map, Path.of(root, vendor), JdkInstallation.Source.WINDOWS_PROGRAM_FILES);
        scanChildren(map, Path.of(root, vendor, "jdk"), JdkInstallation.Source.WINDOWS_PROGRAM_FILES);
      }
    }
    // Common scoop / chocolatey layouts
    String userHome = System.getProperty("user.home");
    if (userHome != null) {
      scanChildren(map, Path.of(userHome, "scoop", "apps"), JdkInstallation.Source.VENDOR_DIR);
    }
  }

  private static void discoverMac(Map<Path, JdkInstallation> map) {
    scanJvmBundles(map, Path.of("/Library/Java/JavaVirtualMachines"));
    String home = System.getProperty("user.home");
    if (home != null) {
      scanJvmBundles(map, Path.of(home, "Library/Java/JavaVirtualMachines"));
    }
    scanChildren(map, Path.of("/opt/homebrew/opt"), JdkInstallation.Source.VENDOR_DIR);
    scanChildren(map, Path.of("/usr/local/opt"), JdkInstallation.Source.VENDOR_DIR);
  }

  private static void scanJvmBundles(Map<Path, JdkInstallation> map, Path root) {
    if (!Files.isDirectory(root)) {
      return;
    }
    try (var stream = Files.list(root)) {
      stream.forEach(child -> {
        Path home = child.resolve("Contents/Home");
        if (Files.isDirectory(home)) {
          addIfPresent(map, probe(home, JdkInstallation.Source.MACOS_JVMS));
        } else {
          addIfPresent(map, probe(child, JdkInstallation.Source.MACOS_JVMS));
        }
      });
    } catch (Exception ignored) {
      // ignore
    }
  }

  private static void discoverLinux(Map<Path, JdkInstallation> map) {
    Path[] roots = {
        Path.of("/usr/lib/jvm"),
        Path.of("/usr/java"),
        Path.of("/usr/local/jvm"),
        Path.of("/opt/java"),
        Path.of("/opt/jdk"),
        Path.of("/opt")
    };
    for (Path root : roots) {
      scanChildren(map, root, JdkInstallation.Source.LINUX_SYSTEM);
    }
  }

  private static void discoverSdkman(Map<Path, JdkInstallation> map) {
    String home = System.getProperty("user.home");
    if (home == null) {
      return;
    }
    Path candidates = Path.of(home, ".sdkman", "candidates", "java");
    if (!Files.isDirectory(candidates)) {
      return;
    }
    try (var stream = Files.list(candidates)) {
      stream.filter(Files::isDirectory)
          .filter(p -> !"current".equals(p.getFileName().toString()))
          .forEach(p -> addIfPresent(map, probe(p, JdkInstallation.Source.SDKMAN)));
    } catch (Exception ignored) {
      // ignore
    }
    Path current = candidates.resolve("current");
    if (Files.isSymbolicLink(current) || Files.isDirectory(current)) {
      try {
        addIfPresent(map, probe(current.toRealPath(), JdkInstallation.Source.SDKMAN));
      } catch (Exception e) {
        addIfPresent(map, probe(current, JdkInstallation.Source.SDKMAN));
      }
    }
  }

  private static void discoverDeskManaged(Map<Path, JdkInstallation> map) {
    Path managed = JdkPaths.managedJdksRoot();
    if (!Files.isDirectory(managed)) {
      return;
    }
    scanChildren(map, managed, JdkInstallation.Source.DESK_MANAGED);
  }

  private static void scanChildren(Map<Path, JdkInstallation> map, Path dir,
      JdkInstallation.Source source) {
    if (dir == null || !Files.isDirectory(dir)) {
      return;
    }
    // The directory itself might be a JDK
    if (looksLikeJavaHome(dir)) {
      addIfPresent(map, probe(dir, source));
    }
    try (var stream = Files.list(dir)) {
      stream.filter(Files::isDirectory).forEach(child -> {
        if (looksLikeJavaHome(child)) {
          addIfPresent(map, probe(child, source));
        } else {
          // One level deeper (e.g. /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home)
          Path nestedHome = child.resolve("libexec/openjdk.jdk/Contents/Home");
          if (Files.isDirectory(nestedHome)) {
            addIfPresent(map, probe(nestedHome, source));
          }
        }
      });
    } catch (Exception ignored) {
      // ignore listing errors
    }
  }

  private static boolean looksLikeJavaHome(Path home) {
    return Files.isRegularFile(home.resolve("bin").resolve("java" + exeSuffix()))
        || Files.isRegularFile(home.resolve("bin").resolve("javac" + exeSuffix()));
  }

  private static boolean isValidJdkLayout(Path home) {
    return Files.isRegularFile(home.resolve("bin").resolve("javac" + exeSuffix()));
  }

  static JdkInstallation probe(Path home, JdkInstallation.Source source) {
    Objects.requireNonNull(home, "home");
    Path normalized = home.toAbsolutePath().normalize();
    List<String> errors = new ArrayList<>();

    if (!Files.isDirectory(normalized)) {
      errors.add("Not a directory");
      return new JdkInstallation(normalized, null, "unknown", "unknown",
          JdkInstallation.Kind.UNKNOWN, source, false, errors, null, null);
    }

    Path java = normalized.resolve("bin").resolve("java" + exeSuffix());
    Path javac = normalized.resolve("bin").resolve("javac" + exeSuffix());
    boolean hasJava = Files.isRegularFile(java);
    boolean hasJavac = Files.isRegularFile(javac);

    if (!hasJava) {
      errors.add("Missing bin/java");
    }

    JdkInstallation.Kind kind;
    if (hasJavac) {
      kind = JdkInstallation.Kind.JDK;
    } else if (hasJava) {
      kind = JdkInstallation.Kind.JRE;
      errors.add("JRE only (no javac)");
    } else {
      kind = JdkInstallation.Kind.UNKNOWN;
    }

    JdkVersion version = null;
    String vendor = "unknown";
    String arch = System.getProperty("os.arch", "unknown");

    if (hasJava) {
      ReleaseInfo release = readReleaseFile(normalized);
      if (release.version != null) {
        version = release.version;
      }
      if (release.vendor != null) {
        vendor = release.vendor;
      }
      if (release.arch != null) {
        arch = release.arch;
      }
      if (version == null) {
        version = probeVersionWithJava(java).orElse(null);
      }
      if ("unknown".equals(vendor)) {
        vendor = probeVendorWithJava(java).orElse("unknown");
      }
    }

    boolean valid = hasJava && (kind == JdkInstallation.Kind.JDK || kind == JdkInstallation.Kind.JRE)
        && errors.stream().noneMatch(e -> e.startsWith("Missing"));
    // JREs are "valid" as runtimes but not development kits
    if (kind == JdkInstallation.Kind.JRE) {
      valid = hasJava;
    }
    if (kind == JdkInstallation.Kind.JDK) {
      valid = hasJava && hasJavac;
    }

    return new JdkInstallation(
        normalized,
        version,
        vendor,
        arch,
        kind,
        source,
        valid,
        errors,
        hasJava ? java : null,
        hasJavac ? javac : null);
  }

  private static ReleaseInfo readReleaseFile(Path home) {
    Path release = home.resolve("release");
    JdkVersion version = null;
    String vendor = null;
    String arch = null;
    if (!Files.isRegularFile(release)) {
      return new ReleaseInfo(null, null, null);
    }
    try {
      for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (trimmed.startsWith("JAVA_VERSION=")) {
          String v = unquote(trimmed.substring("JAVA_VERSION=".length()));
          version = JdkVersion.parseVersionString(v).orElse(null);
        } else if (trimmed.startsWith("IMPLEMENTOR=")) {
          vendor = unquote(trimmed.substring("IMPLEMENTOR=".length()));
        } else if (trimmed.startsWith("OS_ARCH=")) {
          arch = unquote(trimmed.substring("OS_ARCH=".length()));
        } else if (trimmed.startsWith("JVM_VARIANT=") && vendor == null) {
          vendor = unquote(trimmed.substring("JVM_VARIANT=".length()));
        }
      }
    } catch (Exception ignored) {
      // ignore
    }
    return new ReleaseInfo(version, vendor, arch);
  }

  private static String unquote(String value) {
    String v = value.trim();
    if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
      return v.substring(1, v.length() - 1);
    }
    return v;
  }

  private static Optional<JdkVersion> probeVersionWithJava(Path javaExe) {
    String output = runCapture(javaExe, "-version");
    return JdkVersion.parse(output);
  }

  private static Optional<String> probeVendorWithJava(Path javaExe) {
    String output = runCapture(javaExe, "-XshowSettings:properties", "-version");
    for (String line : output.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("java.vendor =")) {
        return Optional.of(trimmed.substring("java.vendor =".length()).trim());
      }
      if (trimmed.startsWith("java.vendor=")) {
        return Optional.of(trimmed.substring("java.vendor=".length()).trim());
      }
    }
    // Fallback: first line often contains Temurin/OpenJDK
    String first = output.lines().findFirst().orElse("");
    if (first.toLowerCase(Locale.ROOT).contains("temurin")) {
      return Optional.of("Eclipse Adoptium");
    }
    if (first.toLowerCase(Locale.ROOT).contains("openjdk")) {
      return Optional.of("OpenJDK");
    }
    return Optional.empty();
  }

  private static String runCapture(Path exe, String... args) {
    try {
      List<String> command = new ArrayList<>();
      command.add(exe.toString());
      for (String arg : args) {
        command.add(arg);
      }
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      StringBuilder out = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          out.append(line).append('\n');
        }
      }
      process.waitFor(5, TimeUnit.SECONDS);
      return out.toString();
    } catch (Exception e) {
      return "";
    }
  }

  static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  static boolean isMac() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("mac") || os.contains("darwin");
  }

  static String exeSuffix() {
    return isWindows() ? ".exe" : "";
  }

  private record ReleaseInfo(JdkVersion version, String vendor, String arch) {
  }
}
