#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$REPO_ROOT/.android-sdk}"
CMDLINE_VERSION="11076708"
# Try multiple mirror URLs for Android command-line tools (proxy may block some)
CMDLINE_ZIP_URLS=(
  "https://developer.android.com/studio/command-line-tools-linux-9477386_latest.zip"
  "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
)
CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"

mkdir -p "$SDK_ROOT"

if [[ ! -x "$SDKMANAGER" ]]; then
  echo "Installing Android command line tools into $SDK_ROOT"
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT

  archive="$tmpdir/cmdline-tools.zip"
  download_success=false

  for url in "${CMDLINE_ZIP_URLS[@]}"; do
    echo "Attempting to download from: $url"
    if command -v wget >/dev/null 2>&1; then
      if wget -q "$url" -O "$archive" 2>/dev/null; then
        download_success=true
        break
      fi
    elif command -v curl >/dev/null 2>&1; then
      if curl -fsSL "$url" -o "$archive" 2>/dev/null; then
        download_success=true
        break
      fi
    fi
  done

  if [[ "$download_success" != "true" ]]; then
    echo "ERROR: Failed to download Android command-line tools from any source." >&2
    echo "ERROR: Proxy may be blocking access. Please check network configuration." >&2
    exit 1
  fi

  python3 - <<PY
import shutil
shutil.rmtree(r"$SDK_ROOT/cmdline-tools", ignore_errors=True)
PY
  mkdir -p "$CMDLINE_DIR"
  unzip -q -o "$archive" -d "$tmpdir/unpacked"
  mv "$tmpdir/unpacked/cmdline-tools/"* "$CMDLINE_DIR/"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$CMDLINE_DIR/bin:$SDK_ROOT/platform-tools:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle-home}"
mkdir -p "$GRADLE_USER_HOME"

cat > "$REPO_ROOT/local.properties" <<LOCAL_PROPERTIES
sdk.dir=$SDK_ROOT
use_local_pumpx2=false
LOCAL_PROPERTIES

echo "Accepting Android SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true

echo "Installing required Android SDK packages"
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install \
  "platform-tools" \
  "platforms;android-35" \
  "platforms;android-36" \
  "build-tools;35.0.0"

echo "Setup complete."
