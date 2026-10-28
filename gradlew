#!/usr/bin/env sh
# Lightweight Gradle bootstrap for this sample project (the standard wrapper JAR is not required).
set -eu
GRADLE_VERSION="8.9"
if [ -n "${GRADLE_USER_HOME:-}" ]; then
  USER_GRADLE_HOME="$GRADLE_USER_HOME"
else
  USER_GRADLE_HOME="$HOME/.gradle"
fi
DIST_DIR="$USER_GRADLE_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/android-ai-pet"
GRADLE_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  ZIP_PATH="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Downloading Gradle ${GRADLE_VERSION} on first run..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_PATH" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "Please install curl or wget to download Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP_PATH" -d "$DIST_DIR"
  rm -f "$ZIP_PATH"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
