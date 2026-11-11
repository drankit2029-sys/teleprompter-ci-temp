#!/usr/bin/env bash
# Run the API-level compatibility check (Android Lint NewApi) and ktlint.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./gradlew :app:lintDebug

KTLINT_BIN="$(command -v ktlint 2>/dev/null || true)"
if [ -z "$KTLINT_BIN" ] && [ -x "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/cmdline-tools/latest/bin/ktlint" ]; then
    KTLINT_BIN="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/cmdline-tools/latest/bin/ktlint"
fi
if [ -n "$KTLINT_BIN" ]; then
    mapfile -d '' files < <(find app/src -name '*.kt' -print0)
    if [ "${#files[@]}" -gt 0 ]; then
        "$KTLINT_BIN" "${files[@]}"
    fi
else
    echo "ktlint not found; install it from https://github.com/pinterest/ktlint" >&2
fi
