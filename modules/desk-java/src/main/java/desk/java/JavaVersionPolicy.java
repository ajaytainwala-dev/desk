package desk.java;

import java.util.List;
import java.util.Set;

/**
 * Desk Java version support matrix.
 *
 * <p>Java 25 is the primary LTS baseline. Java 26 and 27 are compatibility
 * targets and must never be advertised as LTS. Java 29 is the future LTS
 * migration target.</p>
 */
public final class JavaVersionPolicy {

  /** Primary LTS baseline for Desk development and CI. */
  public static final int PRIMARY_LTS = 25;

  /** Compatibility target (non-LTS). */
  public static final int COMPAT_FEATURE = 26;

  /** Latest feature-release compatibility target (non-LTS). */
  public static final int LATEST_FEATURE_TARGET = 27;

  /** Future LTS migration target. */
  public static final int FUTURE_LTS_TARGET = 29;

  /** Default {@code --release} / project baseline for new projects. */
  public static final int DEFAULT_PROJECT_RELEASE = PRIMARY_LTS;

  private static final Set<Integer> KNOWN_LTS = Set.of(8, 11, 17, 21, 25, 29);

  private static final List<Integer> SUPPORTED_PROJECT_FEATURES =
      List.of(8, 11, 17, 21, 23, 24, 25, 26, 27, 29);

  private JavaVersionPolicy() {
  }

  public static boolean isLts(int featureVersion) {
    return KNOWN_LTS.contains(featureVersion);
  }

  public static boolean isSupportedProjectFeature(int featureVersion) {
    return SUPPORTED_PROJECT_FEATURES.contains(featureVersion)
        || featureVersion >= 8 && featureVersion <= FUTURE_LTS_TARGET;
  }

  public static String describe(int featureVersion) {
    if (featureVersion == PRIMARY_LTS) {
      return "Java " + featureVersion + " (Primary LTS)";
    }
    if (featureVersion == FUTURE_LTS_TARGET) {
      return "Java " + featureVersion + " (Future LTS target)";
    }
    if (featureVersion == COMPAT_FEATURE || featureVersion == LATEST_FEATURE_TARGET) {
      return "Java " + featureVersion + " (compatibility target, non-LTS)";
    }
    if (isLts(featureVersion)) {
      return "Java " + featureVersion + " (LTS)";
    }
    return "Java " + featureVersion + " (non-LTS)";
  }

  public static String supportMatrixMarkdown() {
    return """
        | Version | Role | LTS? |
        |---------|------|------|
        | 25 | Primary development & CI baseline | Yes |
        | 26 | Compatibility target | No |
        | 27 | Latest feature-release compatibility target | No |
        | 29 | Future LTS migration target | Expected |
        | 21 | Supported as project JDK | Yes |
        | 17 / 11 / 8 | Supported as project JDKs | Yes |
        """;
  }
}
