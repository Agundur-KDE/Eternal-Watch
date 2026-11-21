#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
source tools/android_env.sh
python3 tools/check_translations.py
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/Eternal-Watch-debug.apk
