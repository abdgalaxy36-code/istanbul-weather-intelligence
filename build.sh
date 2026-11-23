#!/bin/sh
# Builds the debug and release APKs and copies them to dist/.
#
#   ./build.sh              debug + release
#   ./build.sh debug        debug only
#   ./build.sh release      release only
#
# Environment overrides (all optional):
#   JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME, AAPT2, APKSIGNER
#
# On Termux / ARM the Android Gradle Plugin's bundled aapt2 cannot run, so the
# local aapt2 is passed through -android.aapt2FromMavenOverride.
set -eu
cd "$(dirname "$0")"

export ANDROID_HOME=${ANDROID_HOME:-/data/data/com.termux/files/usr/tmp/opencode/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export JAVA_HOME=${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk}
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle}
export PATH="$JAVA_HOME/bin:$PATH"

AAPT2=${AAPT2:-/data/data/com.termux/files/usr/bin/aapt2}
APKSIGNER=${APKSIGNER:-$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | head -1)}
KS=${KS:-debug.keystore}
KS_PASS=${KS_PASS:-android}
KEY_ALIAS=${KEY_ALIAS:-androiddebugkey}

TARGET=${1:-all}

if [ "$TARGET" = "all" ] || [ "$TARGET" = "debug" ]; then
    ./gradlew :app:assembleDebug --no-daemon \
        -Pandroid.aapt2FromMavenOverride="$AAPT2"
fi

if [ "$TARGET" = "all" ] || [ "$TARGET" = "release" ]; then
    ./gradlew :app:assembleRelease --no-daemon \
        -Pandroid.aapt2FromMavenOverride="$AAPT2"
fi

mkdir -p dist

if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    cp app/build/outputs/apk/debug/app-debug.apk dist/istanbul-weather-intelligence-debug.apk
fi

if [ -f app/build/outputs/apk/release/app-release.apk ]; then
    cp app/build/outputs/apk/release/app-release.apk dist/istanbul-weather-intelligence-release.apk
    if [ -n "$APKSIGNER" ]; then
        bash "$APKSIGNER" verify --verbose dist/istanbul-weather-intelligence-release.apk
    fi
fi

ls -l dist
echo "BUILD DONE -> $PWD/dist"
