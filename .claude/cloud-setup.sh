#!/bin/bash
# Bootstrap script for Claude Code cloud sessions on controlX2.
#
# Installs Android SDK components, Robolectric offline JARs, and writes the
# session-wide env vars Gradle/AGP need. Idempotent — safe to re-run.
set -euo pipefail

REPO_ROOT="/home/user/controlX2"
SDK_ROOT="$REPO_ROOT/.android-sdk"

# ── Helper functions ──

# download_with_retry <url> <dest>
download_with_retry() {
  local url="$1"
  local dest="$2"
  if curl -sL --fail "$url" -o "$dest"; then
    return 0
  fi
  echo "Download failed for $url — retrying in 10s..."
  sleep 10
  curl -sL --fail "$url" -o "$dest" || {
    echo "ERROR: Download failed twice for $url" >&2
    return 1
  }
}

# download_and_unzip <url> <tmp_zip> <dest_dir>
download_and_unzip() {
  local url="$1"
  local tmp_zip="$2"
  local dest_dir="$3"
  download_with_retry "$url" "$tmp_zip"
  if ! unzip -qo "$tmp_zip" -d "$dest_dir"; then
    echo "Extraction failed for $tmp_zip — re-downloading in 10s..."
    sleep 10
    rm -f "$tmp_zip"
    download_with_retry "$url" "$tmp_zip"
    unzip -qo "$tmp_zip" -d "$dest_dir" || {
      echo "ERROR: Extraction failed after re-download of $url" >&2
      return 1
    }
  fi
  rm -f "$tmp_zip"
}

# ── Install Android SDK components directly ──
mkdir -p "$SDK_ROOT"

if [[ ! -f "$SDK_ROOT/build-tools/35.0.0/aapt2" ]]; then
  echo "Downloading build-tools 35.0.0..."
  mkdir -p "$SDK_ROOT/build-tools/35.0.0"
  download_and_unzip \
    "https://dl.google.com/android/repository/build-tools_r35_linux.zip" \
    /tmp/bt35.zip \
    "$SDK_ROOT/build-tools/35.0.0/"
  mv "$SDK_ROOT/build-tools/35.0.0/android-"*/* "$SDK_ROOT/build-tools/35.0.0/" 2>/dev/null || true

  cat > "$SDK_ROOT/build-tools/35.0.0/package.xml" << 'XML'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns7="http://schemas.android.com/sdk/android/repo/repository2/03">
    <localPackage path="build-tools;35.0.0" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns7:genericDetailsType"/>
        <revision><major>35</major><minor>0</minor><micro>0</micro></revision>
        <display-name>Android SDK Build-Tools 35</display-name>
    </localPackage>
</ns2:repository>
XML
fi

if [[ ! -d "$SDK_ROOT/platforms/android-35" ]]; then
  echo "Downloading platform android-35..."
  download_and_unzip \
    "https://dl.google.com/android/repository/platform-35_r02.zip" \
    /tmp/p35.zip \
    "$SDK_ROOT/platforms/"

  cat > "$SDK_ROOT/platforms/android-35/package.xml" << 'XML'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns7="http://schemas.android.com/sdk/android/repo/repository2/03">
    <localPackage path="platforms;android-35" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns7:platformDetailsType"><api-level>35</api-level></type-details>
        <revision><major>2</major></revision>
        <display-name>Android SDK Platform 35</display-name>
    </localPackage>
</ns2:repository>
XML
fi

if [[ ! -d "$SDK_ROOT/platforms/android-36" ]]; then
  echo "Downloading platform android-36..."
  download_and_unzip \
    "https://dl.google.com/android/repository/platform-36_r02.zip" \
    /tmp/p36.zip \
    "$SDK_ROOT/platforms/"

  cat > "$SDK_ROOT/platforms/android-36/package.xml" << 'XML'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns7="http://schemas.android.com/sdk/android/repo/repository2/03">
    <localPackage path="platforms;android-36" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns7:platformDetailsType"><api-level>36</api-level></type-details>
        <revision><major>2</major></revision>
        <display-name>Android SDK Platform 36</display-name>
    </localPackage>
</ns2:repository>
XML
fi

# ── Create local.properties ──
LOCAL_PROPS_EXPECTED="sdk.dir=$SDK_ROOT
use_local_pumpx2=false"
if [[ ! -f "$REPO_ROOT/local.properties" ]] || [[ "$(cat "$REPO_ROOT/local.properties")" != "$LOCAL_PROPS_EXPECTED" ]]; then
  cat > "$REPO_ROOT/local.properties" << EOF
sdk.dir=$SDK_ROOT
use_local_pumpx2=false
EOF
  echo "local.properties written"
fi

# ── Download Robolectric offline JARs ──
# Robolectric downloads instrumented Android JARs at test time.
# mobile/build.gradle enables offline mode when CI=true (set below);
# we just need the JARs pre-placed in ~/.robolectric/.
mkdir -p ~/.robolectric

ROBOLECTRIC_REPO="https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented"

if [[ ! -f ~/.robolectric/android-all-instrumented-14-robolectric-10818077-i7.jar ]]; then
  echo "Downloading Robolectric SDK 14 (Android 34) jar..."
  download_with_retry \
    "$ROBOLECTRIC_REPO/14-robolectric-10818077-i7/android-all-instrumented-14-robolectric-10818077-i7.jar" \
    ~/.robolectric/android-all-instrumented-14-robolectric-10818077-i7.jar
fi
if [[ ! -f ~/.robolectric/android-all-instrumented-14-robolectric-10818077-i7.pom ]]; then
  echo "Downloading Robolectric SDK 14 (Android 34) pom..."
  download_with_retry \
    "$ROBOLECTRIC_REPO/14-robolectric-10818077-i7/android-all-instrumented-14-robolectric-10818077-i7.pom" \
    ~/.robolectric/android-all-instrumented-14-robolectric-10818077-i7.pom
fi

if [[ ! -f ~/.robolectric/android-all-instrumented-15-robolectric-13954326-i7.jar ]]; then
  echo "Downloading Robolectric SDK 15 (Android 35) jar..."
  download_with_retry \
    "$ROBOLECTRIC_REPO/15-robolectric-13954326-i7/android-all-instrumented-15-robolectric-13954326-i7.jar" \
    ~/.robolectric/android-all-instrumented-15-robolectric-13954326-i7.jar
fi
if [[ ! -f ~/.robolectric/android-all-instrumented-15-robolectric-13954326-i7.pom ]]; then
  echo "Downloading Robolectric SDK 15 (Android 35) pom..."
  download_with_retry \
    "$ROBOLECTRIC_REPO/15-robolectric-13954326-i7/android-all-instrumented-15-robolectric-13954326-i7.pom" \
    ~/.robolectric/android-all-instrumented-15-robolectric-13954326-i7.pom
fi

# ── Export session-wide environment variables ──
# ANDROID_HOME / ANDROID_SDK_ROOT: required for Gradle/AGP commands. Both names
#   are written because different tools (and agent sanity checks) read different
#   ones.
# CI=true: activates Robolectric offline mode in mobile/build.gradle (see the
#   `if (System.getenv('CI') == 'true')` block in testOptions). Without this,
#   Robolectric tries to fetch the instrumented JAR at test time and gets a 503
#   from Maven Central in this environment.
#
# Belt-and-suspenders: write to $CLAUDE_ENV_FILE (Claude Code's preferred
# mechanism) AND ~/.bashrc, because in past sessions a session-level
# ANDROID_HOME=$REPO_ROOT/.android-sdk literal-string env was clobbering the
# CLAUDE_ENV_FILE write and leaving agents thinking the SDK was missing.
write_env_var() {
  local target="$1"
  local key="$2"
  local value="$3"
  local expected="export $key=$value"
  [[ -z "$target" ]] && return 0
  # Skip if the file already has the exact line we'd write.
  if [[ -f "$target" ]] && grep -Fxq "$expected" "$target"; then
    return 0
  fi
  # Drop any stale export of this key, then append the fresh one.
  if [[ -f "$target" ]]; then
    sed -i "\|^export $key=|d" "$target" 2>/dev/null || true
  fi
  echo "$expected" >> "$target"
  echo "wrote $key=$value to $target"
}

for target in "${CLAUDE_ENV_FILE:-}" "$HOME/.bashrc"; do
  write_env_var "$target" ANDROID_HOME "$SDK_ROOT"
  write_env_var "$target" ANDROID_SDK_ROOT "$SDK_ROOT"
  write_env_var "$target" CI true
done

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export CI=true

# ── Sanity check ──
# A platform-level session env var sometimes ships with the literal string
# "$REPO_ROOT/.android-sdk" (REPO_ROOT unset) and overrides CLAUDE_ENV_FILE.
# This catches that drift in future sessions before agents go down a rabbit hole.
expected="$SDK_ROOT"
warn=0
for var in ANDROID_HOME ANDROID_SDK_ROOT; do
  actual="$(printenv "$var" || true)"
  if [[ "$actual" != "$expected" ]]; then
    echo "WARNING: $var=$actual (expected $expected) — check session-level env config" >&2
    warn=1
  fi
done

# ── Warm Gradle dependency cache ──
# Slow (multiple minutes), so gate on a sentinel that records the SDK + script
# version we cached against. Bump CACHE_VERSION when you change SDK components
# or anything else that should invalidate the cached resolution.
CACHE_VERSION="1"
DEPS_SENTINEL="$SDK_ROOT/.gradle-deps-warmed"
DEPS_TOKEN="$CACHE_VERSION:$SDK_ROOT"
if [[ ! -f "$DEPS_SENTINEL" ]] || [[ "$(cat "$DEPS_SENTINEL")" != "$DEPS_TOKEN" ]]; then
  echo "Warming gradle dependencies..."
  (cd "$REPO_ROOT" && ./gradlew dependencies --quiet)
  echo "$DEPS_TOKEN" > "$DEPS_SENTINEL"
else
  echo "Gradle dependencies already warmed (skipping)"
fi

echo "Setup complete!"
echo "  SDK: $SDK_ROOT"
echo "  Robolectric JARs: ~/.robolectric/"
echo ""
echo "Build:  ./gradlew :mobile:compileDebugKotlin"
echo "Test:   ./gradlew testDebugUnitTest"
[[ "$warn" -eq 1 ]] && echo "(See WARNING above — env may still be misconfigured at session level.)"
exit 0
