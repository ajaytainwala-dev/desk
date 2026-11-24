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
SHARE_ROOT="${XDG_DATA_HOME:-$HOME/.local/share}/desk"
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
mkdir -p "$INSTALL_DIR" "$SHARE_ROOT"

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

# Resolve the real portable root after unzip. Release zips nest under desk-portable/.
find_portable_root() {
  local root="$1"
  if [ -x "$root/bin/desk" ] && [ -d "$root/runtime" ]; then
    printf '%s\n' "$root"
    return 0
  fi
  local nested
  for nested in "$root"/desk-portable "$root"/*/ ; do
    [ -d "$nested" ] || continue
    if [ -x "$nested/bin/desk" ] && [ -d "$nested/runtime" ]; then
      printf '%s\n' "$nested"
      return 0
    fi
  done
  return 1
}

install_tui_into() {
  local dest_dir="$1"
  mkdir -p "$dest_dir"
  local tui_asset="desk-tui-${PLATFORM}"
  if download_asset "$tui_asset"; then
    cp "$TMP/$tui_asset" "$dest_dir/desk-tui"
    chmod +x "$dest_dir/desk-tui"
    echo "Installed TUI: $dest_dir/desk-tui"
    return 0
  fi
  echo "Warning: $tui_asset not available — TUI launch may be unavailable until it is installed." >&2
  return 1
}

write_path_wrapper() {
  local portable_bin="$1"
  local tui_bin="$2"
  cat > "$INSTALL_DIR/desk" << EOF
#!/bin/sh
# Desk CLI wrapper installed by desk-cli-setup.sh
export DESK_TUI="\${DESK_TUI:-$tui_bin}"
exec "$portable_bin" "\$@"
EOF
  chmod +x "$INSTALL_DIR/desk"
}

if [[ "$ASSET" == *.zip ]]; then
  need unzip
  unzip -q "$TMP/$ASSET" -d "$TMP/extract"

  PORTABLE_ROOT=""
  if PORTABLE_ROOT="$(find_portable_root "$TMP/extract")"; then
    echo "Portable root: $PORTABLE_ROOT"
    rm -rf "$SHARE_ROOT/portable"
    mkdir -p "$SHARE_ROOT"
    cp -R "$PORTABLE_ROOT" "$SHARE_ROOT/portable"
    install_tui_into "$SHARE_ROOT/portable/bin" || true
    # Prefer bundled desk-tui; fall back to any existing path.
    TUI_PATH="$SHARE_ROOT/portable/bin/desk-tui"
    if [ ! -x "$TUI_PATH" ]; then
      TUI_PATH="$SHARE_ROOT/portable/bin/desk-tui"
    fi
    write_path_wrapper "$SHARE_ROOT/portable/bin/desk" "$TUI_PATH"
  elif [ -x "$TMP/extract/desk" ]; then
    cp "$TMP/extract/desk" "$INSTALL_DIR/desk"
    chmod +x "$INSTALL_DIR/desk"
  else
    FOUND="$(find "$TMP/extract" -type f \( -name desk -o -name desk-tui \) | head -1 || true)"
    if [ -z "$FOUND" ]; then
      echo "Error: zip did not contain a desk portable layout or binary" >&2
      exit 1
    fi
    # Only copy a standalone binary when it is not the Java shell launcher alone.
    if head -c 2 "$FOUND" | grep -q $'#!'; then
      echo "Error: zip contained a launcher script without its runtime. Re-download desk-portable-${PLATFORM}.zip" >&2
      exit 1
    fi
    cp "$FOUND" "$INSTALL_DIR/desk"
    chmod +x "$INSTALL_DIR/desk"
  fi
else
  # Standalone native / TUI asset — install as desk, and still try to fetch TUI name.
  cp "$TMP/$ASSET" "$INSTALL_DIR/desk"
  chmod +x "$INSTALL_DIR/desk"
  if [[ "$ASSET" == desk-tui-* ]]; then
    cp "$INSTALL_DIR/desk" "$INSTALL_DIR/desk-tui"
    chmod +x "$INSTALL_DIR/desk-tui"
    echo "Note: installed TUI-only binary. CLI commands need desk-portable-${PLATFORM}.zip." >&2
  else
    install_tui_into "$INSTALL_DIR" || true
  fi
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
  echo "Warning: 'desk version' failed — check the install output above." >&2
fi
echo "Done."
