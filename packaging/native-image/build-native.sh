#!/bin/bash
# Build a GraalVM Native Image for Desk (Linux/macOS).
#
# Prerequisites:
#   - GraalVM JDK installed with native-image component
#   - GRAALVM_HOME set (or JAVA_HOME pointing to GraalVM)
#
# Usage:
#   ./packaging/native-image/build-native.sh [output-name]
#
# Output:
#   dist/native/desk (Linux/macOS) or dist/native/desk.exe (Windows)

set -euo pipefail

OUTPUT_NAME="${1:-desk}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Desk Native Image Build ==="
echo "Repo root: $REPO_ROOT"
echo "Output name: $OUTPUT_NAME"

# Determine GraalVM home
GRAALVM_HOME="${GRAALVM_HOME:-${JAVA_HOME:-}}"
if [ -z "$GRAALVM_HOME" ]; then
  echo "Error: GRAALVM_HOME or JAVA_HOME must be set to a GraalVM JDK with native-image." >&2
  exit 1
fi
echo "Using GRAALVM_HOME: $GRAALVM_HOME"

NATIVE_IMAGE_EXE="$GRAALVM_HOME/bin/native-image"
if [ ! -x "$NATIVE_IMAGE_EXE" ]; then
  echo "Error: native-image not found in $GRAALVM_HOME/bin. Install the native-image component." >&2
  exit 1
fi

# Build all modules
echo ""
echo "Building Maven modules..."
"$REPO_ROOT/mvnw" -B -q -DskipTests package

# Find the shaded application JAR
UBER_JAR=$(ls "$REPO_ROOT/modules/desk-cli/target"/desk-cli-*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$UBER_JAR" ]; then
  echo "Error: Application JAR not found in modules/desk-cli/target" >&2
  exit 1
fi

# Create output directory
DIST_DIR="$REPO_ROOT/dist/native"
mkdir -p "$DIST_DIR"

# Determine output executable name
OUTPUT_EXE="$DIST_DIR/$OUTPUT_NAME"
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
  OUTPUT_EXE="$OUTPUT_EXE.exe"
fi

echo ""
echo "Running native-image..."
echo "Output: $OUTPUT_EXE"

"$NATIVE_IMAGE_EXE" \
  --no-fallback \
  --class-path "$UBER_JAR" \
  -H:Name="$OUTPUT_NAME" \
  -H:Path="$DIST_DIR" \
  -H:ReflectionConfigurationFiles="$REPO_ROOT/packaging/native-image/reflect-config.json" \
  -H:ResourceConfigurationFiles="$REPO_ROOT/packaging/native-image/resource-config.json" \
  -H:JNIConfigurationFiles="$REPO_ROOT/packaging/native-image/jni-config.json" \
  -H:ProxyConfigurationFiles="$REPO_ROOT/packaging/native-image/proxy-config.json" \
  -H:+ReportExceptionStackTraces \
  -H:+ReportUnsupportedElementsAtRuntime \
  desk.cli.Main

if [ ! -f "$OUTPUT_EXE" ]; then
  echo "Error: Native image not produced at $OUTPUT_EXE" >&2
  exit 1
fi

echo ""
echo "=== Build complete ==="
echo "Native executable: $OUTPUT_EXE"
SIZE=$(du -h "$OUTPUT_EXE" | cut -f1)
echo "Size: $SIZE"
