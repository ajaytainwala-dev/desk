package desk.java;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed Java version components. Distinguishes feature releases from LTS
 * according to Desk's version policy — never marks non-LTS versions as LTS.
 */
public final class JdkVersion implements Comparable<JdkVersion> {

  private static final Pattern VERSION_PATTERN = Pattern.compile(
      "(?:java|openjdk)\\s+version\\s+\"([^\"]+)\"|"
          + "([0-9]+(?:\\.[0-9]+){0,3}(?:[+][0-9]+)?)",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern FEATURE = Pattern.compile("^([0-9]+)");

  private final int feature;
  private final int interim;
  private final int update;
  private final int patch;
  private final String raw;

  public JdkVersion(int feature, int interim, int update, int patch, String raw) {
    if (feature < 1) {
      throw new IllegalArgumentException("feature version must be >= 1");
    }
    this.feature = feature;
    this.interim = Math.max(0, interim);
    this.update = Math.max(0, update);
    this.patch = Math.max(0, patch);
    this.raw = Objects.requireNonNull(raw, "raw");
  }

  public int feature() {
    return feature;
  }

  public int interim() {
    return interim;
  }

  public int update() {
    return update;
  }

  public int patch() {
    return patch;
  }

  public String raw() {
    return raw;
  }

  /**
   * Returns whether this feature release is an OpenJDK LTS according to
   * Desk's policy table (8, 11, 17, 21, 25, and expected 29).
   */
  public boolean isLts() {
    return JavaVersionPolicy.isLts(feature);
  }

  public boolean matchesFeature(int featureVersion) {
    return this.feature == featureVersion;
  }

  public static Optional<JdkVersion> parse(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher m = VERSION_PATTERN.matcher(text);
    String candidate = null;
    if (m.find()) {
      candidate = m.group(1) != null ? m.group(1) : m.group(2);
    }
    if (candidate == null) {
      Matcher featureOnly = FEATURE.matcher(text.trim());
      if (featureOnly.find()) {
        int f = Integer.parseInt(featureOnly.group(1));
        return Optional.of(new JdkVersion(f, 0, 0, 0, text.trim()));
      }
      return Optional.empty();
    }
    return parseVersionString(candidate);
  }

  public static Optional<JdkVersion> parseVersionString(String version) {
    if (version == null || version.isBlank()) {
      return Optional.empty();
    }
    String cleaned = version.trim();
    // Legacy 1.8.x → feature 8
    if (cleaned.startsWith("1.")) {
      String[] legacy = cleaned.split("[._+\\-]");
      if (legacy.length >= 2) {
        try {
          int feature = Integer.parseInt(legacy[1]);
          int update = legacy.length > 2 ? parseIntSafe(legacy[2]) : 0;
          return Optional.of(new JdkVersion(feature, 0, update, 0, cleaned));
        } catch (NumberFormatException ignored) {
          return Optional.empty();
        }
      }
    }
    String[] parts = cleaned.split("[.+\\-_]");
    try {
      int feature = Integer.parseInt(parts[0]);
      int interim = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
      int update = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
      int patch = parts.length > 3 ? parseIntSafe(parts[3]) : 0;
      return Optional.of(new JdkVersion(feature, interim, update, patch, cleaned));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static int parseIntSafe(String s) {
    try {
      return Integer.parseInt(s.replaceAll("[^0-9].*", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public int compareTo(JdkVersion other) {
    int c = Integer.compare(feature, other.feature);
    if (c != 0) {
      return c;
    }
    c = Integer.compare(interim, other.interim);
    if (c != 0) {
      return c;
    }
    c = Integer.compare(update, other.update);
    if (c != 0) {
      return c;
    }
    return Integer.compare(patch, other.patch);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JdkVersion that)) {
      return false;
    }
    return feature == that.feature
        && interim == that.interim
        && update == that.update
        && patch == that.patch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(feature, interim, update, patch);
  }

  @Override
  public String toString() {
    if (interim == 0 && update == 0 && patch == 0) {
      return Integer.toString(feature);
    }
    return feature + "." + interim + "." + update + (patch > 0 ? "." + patch : "");
  }
}
