#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/home/alec/projects/KCast-App/.tools/jdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/home/alec/projects/KCast-App/.tools/gradle-cache}"
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/Eternal-Watch-debug.apk
