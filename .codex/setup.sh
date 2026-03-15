#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$REPO_ROOT/.android-sdk}"
CMDLINE_VERSION="11076708"
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"

mkdir -p "$SDK_ROOT"

if [[ ! -x "$SDKMANAGER" ]]; then
  echo "Installing Android command line tools into $SDK_ROOT"
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT

  archive="$tmpdir/cmdline-tools.zip"
  if command -v wget >/dev/null 2>&1; then
    wget -q "$CMDLINE_ZIP_URL" -O "$archive"
  elif command -v curl >/dev/null 2>&1; then
    curl -fsSL "$CMDLINE_ZIP_URL" -o "$archive"
  else
    echo "ERROR: Neither wget nor curl is available to download Android tools." >&2
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
