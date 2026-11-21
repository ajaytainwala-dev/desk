#!/usr/bin/env bash
# Optional helper to download a reproducible JDT LS packaging for local/CI IT.
# Does not run unless explicitly invoked. Set DESK_JAVA_LS to the resulting launcher.
set -euo pipefail
ROOT="${1:-.jdtls}"
mkdir -p "$ROOT"
# Prefer a user-provided archive URL for reproducibility.
URL="${DESK_JDTLS_URL:-}"
if [[ -z "$URL" ]]; then
  echo "Set DESK_JDTLS_URL to a jdtls archive URL (official release asset)."
  echo "Then: export DESK_JAVA_LS=$ROOT/bin/jdtls  (or platform launcher)"
  exit 2
fi
ARCHIVE="$ROOT/jdtls.tgz"
curl -fsSL "$URL" -o "$ARCHIVE"
tar -xzf "$ARCHIVE" -C "$ROOT"
echo "Extracted to $ROOT — set DESK_JAVA_LS to the jdtls launcher and DESK_JDTLS_IT=1"
