#!/usr/bin/env sh
# Lightweight Gradle bootstrap for environments where the official wrapper JAR is not bundled.
set -eu
VERSION="8.11.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
BASE="$GRADLE_USER_HOME/wrapper/dists/iconforge-gradle-$VERSION"
GRADLE_HOME="$BASE/gradle-$VERSION"
if [ "${ICONFORGE_USE_SYSTEM_GRADLE:-0}" = "1" ] && command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$BASE"
    ARCHIVE="$BASE/gradle-$VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 2 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
    else
        echo "Install curl or wget, then retry." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "Install unzip, then retry." >&2
        exit 1
    fi
    unzip -q -o "$ARCHIVE" -d "$BASE"
    rm -f "$ARCHIVE"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
