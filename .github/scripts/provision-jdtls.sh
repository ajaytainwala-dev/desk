#!/usr/bin/env bash
# Provision pinned Eclipse JDT LS for CI / local live tests.
set -euo pipefail
VERSION="${DESK_JDTLS_VERSION:-1.57.0}"
ARCHIVE="${DESK_JDTLS_ARCHIVE:-jdt-language-server-1.57.0-202602261110.tar.gz}"
SHA256="${DESK_JDTLS_SHA256:-f7ffa93fe1bbbea95dac13dd97cdcd25c582d6e56db67258da0dcceb2302601e}"
BASE="https://download.eclipse.org/jdtls/milestones/${VERSION}"
ROOT="${1:-${RUNNER_TEMP:-/tmp}/desk-jdtls/${VERSION}}"
mkdir -p "$ROOT"
MARKER="$ROOT/.desk-jdtls-ok"
if [[ -f "$MARKER" && -x "$ROOT/bin/jdtls" ]]; then
  echo "JDT LS already provisioned at $ROOT"
  echo "DESK_JAVA_LS=$ROOT/bin/jdtls"
  exit 0
fi
STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
curl -fsSL "$BASE/$ARCHIVE" -o "$STAGING/$ARCHIVE"
echo "$SHA256  $STAGING/$ARCHIVE" | sha256sum -c -
mkdir -p "$STAGING/extract"
tar -xzf "$STAGING/$ARCHIVE" -C "$STAGING/extract"
rm -rf "$ROOT"
mkdir -p "$(dirname "$ROOT")"
mv "$STAGING/extract" "$ROOT"
chmod +x "$ROOT/bin/jdtls" || true
echo "$VERSION" > "$MARKER"
echo "Installed JDT LS $VERSION -> $ROOT"
echo "DESK_JAVA_LS=$ROOT/bin/jdtls"
