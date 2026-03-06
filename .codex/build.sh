#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$REPO_ROOT/.android-sdk}"
CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"

if [[ ! -x "$CMDLINE_DIR/bin/sdkmanager" ]]; then
  echo "Android SDK command-line tools not found at $CMDLINE_DIR." >&2
  echo "Run .codex/setup.sh first." >&2
  exit 1
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$CMDLINE_DIR/bin:$SDK_ROOT/platform-tools:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle-home}"

cd "$REPO_ROOT"
./gradlew :wear:compileDebugKotlin --console=plain
