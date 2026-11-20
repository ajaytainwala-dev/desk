#!/usr/bin/env bash
# Zip a desk-portable directory for GitHub Releases.
#
# Usage:
#   ./packaging/installers/build-portable-zip.sh [portable-dir] [output-zip]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PORTABLE_DIR="${1:-$REPO_ROOT/dist/desk-portable}"
OUT_ZIP="${2:-$REPO_ROOT/dist/desk-portable-linux.zip}"
PLATFORM_LABEL="${DESK_ZIP_PLATFORM:-linux}"

if [ ! -d "$PORTABLE_DIR" ]; then
  echo "Error: portable dir not found: $PORTABLE_DIR" >&2
  exit 1
fi

# Resolve absolute paths before cd'ing into the staging dir (relative
# OUT_ZIP would otherwise be created under /tmp and fail).
if [[ "$PORTABLE_DIR" != /* ]]; then
  PORTABLE_DIR="$(cd "$PORTABLE_DIR" && pwd)"
fi
mkdir -p "$(dirname "$OUT_ZIP")"
if [[ "$OUT_ZIP" != /* ]]; then
  OUT_ZIP="$(cd "$(dirname "$OUT_ZIP")" && pwd)/$(basename "$OUT_ZIP")"
fi
rm -f "$OUT_ZIP"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -a "$PORTABLE_DIR" "$STAGE/desk-portable"

# Stable top-level folder name inside the archive
(
  cd "$STAGE"
  zip -r -q "$OUT_ZIP" desk-portable
)

# Also emit a platform-neutral alias when building linux CI artifact
ALIAS="$REPO_ROOT/dist/desk-portable.zip"
if [ "$PLATFORM_LABEL" = "linux" ]; then
  cp -f "$OUT_ZIP" "$ALIAS"
fi

echo "Wrote $OUT_ZIP"
ls -lh "$OUT_ZIP"
