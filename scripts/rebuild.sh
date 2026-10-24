#!/usr/bin/env bash
# One-shot "edit -> see it on device" loop: builds the debug APK and, on success, reinstalls and
# relaunches it on the running emulator. Toolchain and caches come from the Nix dev shell via
# scripts/dev.sh, which enters it once per session instead of once per command, so the Gradle
# daemon and both caches stay warm. Requires an emulator already started with
# `scripts/dev.sh emulator start`.
#
# Usage:
#   scripts/rebuild.sh           # assembleDebug, then install x86_64 + relaunch
#   scripts/rebuild.sh test      # verifyFast + design render + APK, then install + relaunch
#   scripts/rebuild.sh build-only
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  test)
    scripts/dev.sh test
    scripts/dev.sh render
    ;;
  build-only | "") ;;
  *)
    printf 'rebuild.sh: unknown mode: %s\n' "$1" >&2
    exit 2
    ;;
esac

scripts/dev.sh apk

if [[ "${1:-}" == "build-only" ]]; then
  exit 0
fi

echo "== reinstall + relaunch =="
scripts/dev.sh emulator reinstall app/build/outputs/apk/debug/app-x86_64-debug.apk
