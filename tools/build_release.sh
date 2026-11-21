#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
source tools/android_env.sh
python3 tools/package_release.py "$@"
