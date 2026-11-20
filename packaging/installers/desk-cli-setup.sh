#!/usr/bin/env bash
# Desk CLI setup for macOS / Linux.
# Downloads the latest release assets and installs `desk` onto PATH.
#
# Usage:
#   curl -fsSL https://github.com/ajaytainwala-dev/javaproject/releases/latest/download/desk-cli-setup.sh | bash
#   DESK_INSTALL_DIR="$HOME/bin" bash desk-cli-setup.sh
set -euo pipefail

REPO="${DESK_REPO:-ajaytainwala-dev/javaproject}"
INSTALL_DIR="${DESK_INSTALL_DIR:-$HOME/.local/bin}"
API="https://api.github.com/repos/${REPO}/releases/latest"

echo "=== Desk CLI setup ==="

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Error: required command '$1' not found" >&2
    exit 1
  }
}

need curl
need uname

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|amd64) ARCH_TAG="x86_64" ;;
  aarch64|arm64) ARCH_TAG="aarch64" ;;
  *) ARCH_TAG="$ARCH" ;;
esac

case "$OS" in
  linux*) PLATFORM="linux" ;;
  darwin*) PLATFORM="macos" ;;
  *)
    echo "Unsupported OS: $OS" >&2
    exit 1
    ;;
esac

echo "Platform: $PLATFORM ($ARCH_TAG)"
echo "Install dir: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Resolving latest release…"
RELEASE_JSON="$(curl -fsSL -H 'Accept: application/vnd.github+json' "$API")"
TAG="$(printf '%s' "$RELEASE_JSON" | sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
if [ -z "$TAG" ]; then
  echo "Error: could not determine latest release tag" >&2
  exit 1
fi
echo "Latest release: $TAG"

# Prefer portable zip, then native binary names produced by CI.
CANDIDATES=(
  "desk-portable-${PLATFORM}.zip"
  "desk-portable.zip"
  "desk-native-${PLATFORM}"
  "desk"
)

download_asset() {
  local name="$1"
  local url="https://github.com/${REPO}/releases/download/${TAG}/${name}"
  if curl -fsI "$url" >/dev/null 2>&1; then
    echo "Downloading $name…"
    curl -fL "$url" -o "$TMP/$name"
    return 0
  fi
  return 1
}

ASSET=""
for name in "${CANDIDATES[@]}"; do
  if download_asset "$name"; then
    ASSET="$name"
    break
  fi
done

if [ -z "$ASSET" ]; then
  echo "Error: no matching release asset for $PLATFORM. See https://github.com/${REPO}/releases/tag/${TAG}" >&2
  exit 1
fi

if [[ "$ASSET" == *.zip ]]; then
  need unzip
  unzip -q "$TMP/$ASSET" -d "$TMP/extract"
  # Prefer portable launcher, else native binary named desk
  if [ -x "$TMP/extract/bin/desk" ]; then
    cp "$TMP/extract/bin/desk" "$INSTALL_DIR/desk"
    # Keep a copy of portable layout under ~/.local/share/desk if jlink runtime present
    if [ -d "$TMP/extract/runtime" ]; then
      SHARE="${XDG_DATA_HOME:-$HOME/.local/share}/desk"
      mkdir -p "$SHARE"
      rm -rf "$SHARE/portable"
      cp -R "$TMP/extract" "$SHARE/portable"
      cat > "$INSTALL_DIR/desk" << EOF
#!/bin/sh
exec "$SHARE/portable/bin/desk" "\$@"
EOF
      chmod +x "$INSTALL_DIR/desk"
    fi
  elif [ -x "$TMP/extract/desk" ]; then
    cp "$TMP/extract/desk" "$INSTALL_DIR/desk"
  else
    FOUND="$(find "$TMP/extract" -type f -name desk | head -1 || true)"
    if [ -z "$FOUND" ]; then
      echo "Error: zip did not contain a desk binary" >&2
      exit 1
    fi
    cp "$FOUND" "$INSTALL_DIR/desk"
  fi
else
  cp "$TMP/$ASSET" "$INSTALL_DIR/desk"
fi

chmod +x "$INSTALL_DIR/desk"

case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    echo ""
    echo "Add to your shell profile:"
    echo "  export PATH=\"$INSTALL_DIR:\$PATH\""
    ;;
esac

echo ""
echo "Installed: $INSTALL_DIR/desk"
if "$INSTALL_DIR/desk" version >/dev/null 2>&1; then
  "$INSTALL_DIR/desk" version || true
fi
echo "Done."
