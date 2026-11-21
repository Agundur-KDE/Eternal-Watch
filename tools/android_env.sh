#!/usr/bin/env bash
# Optional local toolchain fallback; ordinary SDK/JDK environment variables win.
EW_PROJECTS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && -x "$EW_PROJECTS_ROOT/KCast-App/.tools/jdk/bin/java" ]]; then
    export JAVA_HOME="$EW_PROJECTS_ROOT/KCast-App/.tools/jdk"
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$EW_PROJECTS_ROOT/KCast-App/.tools/android-sdk" ]]; then
    export ANDROID_HOME="$EW_PROJECTS_ROOT/KCast-App/.tools/android-sdk"
fi
if [[ -z "${GRADLE_USER_HOME:-}" && -d "$EW_PROJECTS_ROOT/KCast-App/.tools/gradle-cache" ]]; then
    export GRADLE_USER_HOME="$EW_PROJECTS_ROOT/KCast-App/.tools/gradle-cache"
fi
unset EW_PROJECTS_ROOT
