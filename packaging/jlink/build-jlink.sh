#!/bin/bash
# Build a jlink portable runtime distribution for Desk (Linux/macOS).
#
# Usage:
#   ./packaging/jlink/build-jlink.sh [output-dir]
#
# Output layout (default: dist/desk-portable/):
#   bin/
#     desk            (shell launcher)
#   lib/
#     desk-cli-...-uber.jar
#   runtime/
#     bin/java          (custom jlink JRE)
#   config/
#   README.txt

set -euo pipefail

OUTPUT_DIR="${1:-dist/desk-portable}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Desk jlink Portable Build ==="
echo "Repo root: $REPO_ROOT"
echo "Output dir: $OUTPUT_DIR"

# Step 1: Determine Java home
JAVA_HOME="${JAVA_HOME:-}"
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | cut -d= -f2 | tr -d ' ')
fi
if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME is not set and could not be detected." >&2
  exit 1
fi
echo "Using JAVA_HOME: $JAVA_HOME"

JAVA_EXE="$JAVA_HOME/bin/java"
JLINK_EXE="$JAVA_HOME/bin/jlink"

if [ ! -x "$JLINK_EXE" ]; then
  echo "Error: jlink not found at $JLINK_EXE. A JDK is required (not just a JRE)." >&2
  exit 1
fi

# Step 2: Build all modules (skip when CI already packaged desk-cli)
echo ""
if [ "${DESK_SKIP_MVN:-0}" = "1" ]; then
  echo "[1/4] Skipping Maven build (DESK_SKIP_MVN=1)"
  UBER_JAR_CHECK=$(ls "$REPO_ROOT/modules/desk-cli/target"/desk-cli-*.jar 2>/dev/null | grep -v original | head -1 || true)
  if [ -z "$UBER_JAR_CHECK" ]; then
    echo "Error: DESK_SKIP_MVN=1 but no desk-cli uber JAR in modules/desk-cli/target" >&2
    exit 1
  fi
else
  echo "[1/4] Building Maven modules..."
  bash "$REPO_ROOT/.github/scripts/mvnw-retry.sh" -B -q -DskipTests package
fi

# Step 3: Create output directory structure
echo ""
echo "[2/4] Creating distribution layout..."
if [[ "$OUTPUT_DIR" = /* ]]; then
  DIST_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
else
  DIST_DIR="$(mkdir -p "$REPO_ROOT/$OUTPUT_DIR" && cd "$REPO_ROOT/$OUTPUT_DIR" && pwd)"
fi
BIN_DIR="$DIST_DIR/bin"
LIB_DIR="$DIST_DIR/lib"
RUNTIME_DIR="$DIST_DIR/runtime"
CONFIG_DIR="$DIST_DIR/config"

mkdir -p "$BIN_DIR" "$LIB_DIR" "$CONFIG_DIR"
# jlink refuses to write into an existing directory (e.g. checked-in dist/desk-portable/runtime).
rm -rf "$RUNTIME_DIR"

# Step 4: Build jlink runtime
echo ""
echo "[3/4] Building jlink runtime image..."

JLINK_MODULES=(
  java.base
  java.logging
  java.xml
  java.naming
  java.security.jgss
  java.security.sasl
  jdk.compiler
  jdk.unsupported
  jdk.crypto.ec
)
MODULE_LIST=$(IFS=','; echo "${JLINK_MODULES[*]}")

echo "jlink modules: $MODULE_LIST"

"$JLINK_EXE" \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules "$MODULE_LIST" \
  --output "$RUNTIME_DIR" \
  --compress=2 \
  --no-header-files \
  --no-man-pages \
  --vm=server

RUNTIME_JAVA="$RUNTIME_DIR/bin/java"
if [ ! -x "$RUNTIME_JAVA" ]; then
  echo "Error: jlink did not produce runtime/bin/java at $RUNTIME_JAVA" >&2
  exit 1
fi
echo "jlink runtime created at $RUNTIME_DIR"

# Step 5: Copy application JARs
echo ""
echo "[4/4] Copying application JARs..."

UBER_JAR=$(ls "$REPO_ROOT/modules/desk-cli/target"/desk-cli-*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$UBER_JAR" ]; then
  echo "Error: Uber JAR not found in modules/desk-cli/target" >&2
  exit 1
fi

# Stable jar name so launchers and the TUI (DESK_JAR / lib lookup) stay in sync.
cp "$UBER_JAR" "$LIB_DIR/desk-cli.jar"
echo "Copied: $(basename "$UBER_JAR") -> desk-cli.jar"

# Optional: bundle a prebuilt TUI binary as bin/desk-tui (set DESK_TUI_BINARY).
if [ -n "${DESK_TUI_BINARY:-}" ] && [ -f "$DESK_TUI_BINARY" ]; then
  cp "$DESK_TUI_BINARY" "$BIN_DIR/desk-tui"
  chmod +x "$BIN_DIR/desk-tui"
  echo "Bundled TUI: $BIN_DIR/desk-tui"
fi

# Step 6: Create launch scripts
echo ""
echo "Creating launch scripts..."

cat > "$BIN_DIR/desk" << 'LAUNCHER'
#!/bin/sh
# Desk launcher
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_JAVA="$SCRIPT_DIR/../runtime/bin/java"
APP_JAR="$SCRIPT_DIR/../lib/desk-cli.jar"
# Prefer a bundled native TUI over re-executing this shell script.
if [ -z "${DESK_TUI:-}" ] && [ -x "$SCRIPT_DIR/desk-tui" ]; then
  DESK_TUI="$SCRIPT_DIR/desk-tui"
  export DESK_TUI
fi

if [ ! -x "$RUNTIME_JAVA" ]; then
  echo "Error: runtime Java not found at $RUNTIME_JAVA" >&2
  exit 1
fi

if [ ! -f "$APP_JAR" ]; then
  echo "Error: application JAR not found at $APP_JAR" >&2
  exit 1
fi

# Prefer the bundled editor when DESK_TUI is unset.
if [ -z "${DESK_TUI:-}" ] && [ -x "$TUI_BIN" ]; then
  export DESK_TUI="$TUI_BIN"
fi

exec "$RUNTIME_JAVA" -jar "$APP_JAR" "$@"
LAUNCHER
chmod +x "$BIN_DIR/desk"

# README
cat > "$DIST_DIR/README.txt" << 'README'
Desk Portable Distribution
============================

This distribution contains a custom Java runtime (built with jlink) and the
Desk application. No system Java installation is required.

Directory layout:
  bin/
    desk        - Shell launcher (Git Bash, WSL, Linux, macOS)
    desk-tui    - Native TUI editor (when bundled)
  lib/
    desk-cli.jar   - Application JAR (all dependencies embedded)
  runtime/
    Custom JRE (jlink image) - contains only the JDK modules needed by Desk
  config/
    Configuration files (created on first run)

Usage:
  Linux/macOS: bin/desk [command] [args]
  Git Bash:    ./bin/desk [command] [args]

Examples:
  bin/desk              # interactive TUI (requires desk-tui)
  bin/desk version
  bin/desk help
  bin/desk fmt src/Main.java
  bin/desk java list

No installation required: extract this archive anywhere and run the launcher.
README

echo ""
echo "=== Build complete ==="
echo "Distribution: $DIST_DIR"
echo "Launcher:     $BIN_DIR/desk"
