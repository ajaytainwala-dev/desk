#!/usr/bin/env bash
# Desk CLI setup for macOS / Linux.
# Downloads the latest release assets and installs `desk` onto PATH.
#
# Usage:
#   curl -fsSL https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup.sh | bash
#   DESK_INSTALL_DIR="$HOME/bin" bash desk-cli-setup.sh
set -euo pipefail

REPO="${DESK_REPO:-ajaytainwala-dev/desk}"
INSTALL_DIR="${DESK_INSTALL_DIR:-$HOME/.local/bin}"
SHARE_DIR="${DESK_SHARE_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/desk}"
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
echo "Share dir: $SHARE_DIR"
mkdir -p "$INSTALL_DIR" "$SHARE_DIR/bin"

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

# Prefer platform-specific portable zip, then native binary names produced by CI.
# Do NOT fall back to desk-portable.zip on macOS — that asset is the Linux jlink alias.
CANDIDATES=(
  "desk-portable-${PLATFORM}.zip"
  "desk-native-${PLATFORM}"
)
if [ "$PLATFORM" = "linux" ]; then
  CANDIDATES+=("desk-portable.zip" "desk")
fi

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

# Locate the portable distribution root inside an extracted zip.
# Handles both flat layouts (bin/desk) and nested (desk-portable/bin/desk).
find_portable_root() {
  local extract_dir="$1"
  local launcher
  launcher="$(find "$extract_dir" -type f \( -name desk -o -name desk.cmd \) 2>/dev/null | head -1 || true)"
  if [ -z "$launcher" ]; then
    return 1
  fi
  local bin_dir root
  bin_dir="$(cd "$(dirname "$launcher")" && pwd)"
  root="$(cd "$bin_dir/.." && pwd)"
  if [ -d "$root/runtime" ] || [ -d "$root/lib" ]; then
    printf '%s\n' "$root"
    return 0
  fi
  return 1
}

install_portable_root() {
  local root="$1"
  local portable="$SHARE_DIR/portable"
  echo "Installing portable runtime to $portable…"
  rm -rf "$portable"
  mkdir -p "$SHARE_DIR"
  cp -R "$root" "$portable"
  # Ensure launcher is executable
  chmod +x "$portable/bin/desk" 2>/dev/null || true
  cat > "$INSTALL_DIR/desk" << EOF
#!/bin/sh
# Desk CLI wrapper — forwards to the portable jlink distribution.
export DESK_TUI="\${DESK_TUI:-$SHARE_DIR/bin/desk-tui}"
exec "$portable/bin/desk" "\$@"
EOF
  chmod +x "$INSTALL_DIR/desk"
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
  if PORTABLE_ROOT="$(find_portable_root "$TMP/extract")"; then
    install_portable_root "$PORTABLE_ROOT"
  elif [ -x "$TMP/extract/desk" ]; then
    cp "$TMP/extract/desk" "$INSTALL_DIR/desk"
    chmod +x "$INSTALL_DIR/desk"
  else
    FOUND="$(find "$TMP/extract" -type f -name desk | head -1 || true)"
    if [ -z "$FOUND" ]; then
      echo "Error: zip did not contain a desk binary or portable layout" >&2
      exit 1
    fi
    # Last resort: only copy a native binary, never a bare shell launcher alone.
    if head -c 4 "$FOUND" | grep -q $'\x7fELF' \
      || head -c 4 "$FOUND" | od -An -tx1 | grep -qi 'fe ed fa'; then
      cp "$FOUND" "$INSTALL_DIR/desk"
      chmod +x "$INSTALL_DIR/desk"
    else
      echo "Error: zip contained a desk launcher without its runtime/lib tree" >&2
      exit 1
    fi
  fi
else
  cp "$TMP/$ASSET" "$INSTALL_DIR/desk"
  chmod +x "$INSTALL_DIR/desk"
fi

# Always try to install the Rust TUI alongside the CLI (named desk-tui to avoid
# colliding with the CLI launcher script also called desk).
TUI_ASSET="desk-tui-${PLATFORM}"
if download_asset "$TUI_ASSET"; then
  cp "$TMP/$TUI_ASSET" "$SHARE_DIR/bin/desk-tui"
  chmod +x "$SHARE_DIR/bin/desk-tui"
  # Also place a copy inside portable bin when that layout exists.
  if [ -d "$SHARE_DIR/portable/bin" ]; then
    cp "$SHARE_DIR/bin/desk-tui" "$SHARE_DIR/portable/bin/desk-tui"
    chmod +x "$SHARE_DIR/portable/bin/desk-tui"
  fi
  echo "Installed TUI: $SHARE_DIR/bin/desk-tui"
else
  echo "Warning: $TUI_ASSET not found — 'desk' (no args) needs DESK_TUI or a built TUI binary." >&2
fi

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
else
  echo "Warning: 'desk version' failed — check the install paths above." >&2
fi
echo "Done."
