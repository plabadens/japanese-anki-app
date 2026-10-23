#!/usr/bin/env bash
# One-shot "edit -> see it on device" loop: builds the debug APK (inside the Nix dev
# shell, so the incremental Gradle cache under .tmp-nix is reused) and, on success,
# reinstalls + relaunches on the running emulator via emulator.sh. Run from the repo
# root; requires `nix develop` tooling (invokes it itself, no need to already be inside
# a devshell) and an emulator already started with `nix run .#emulator-wrapper -- start`.
#
# Usage:
#   scripts/rebuild.sh           # assembleDebug, then install x86_64 + relaunch
#   scripts/rebuild.sh test       # full Robolectric gate + render + APK, then install x86_64 + relaunch
#   scripts/rebuild.sh build-only
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "test" ]]; then
  echo "== gradle verifyFast =="
  nix develop --command bash -c 'gradle --offline --configuration-cache --build-cache -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" verifyFast'
  echo "== gradle :app:renderDesignStates assembleDebug =="
  nix develop --command bash -c 'gradle --offline --configuration-cache --build-cache -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" :app:renderDesignStates assembleDebug'
else
  echo "== gradle assembleDebug =="
  nix develop --command bash -c 'gradle --offline --configuration-cache --build-cache -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" assembleDebug'
fi

if [[ "${1:-}" == "build-only" ]]; then
  exit 0
fi

echo "== reinstall + relaunch =="
nix run .#emulator-wrapper -- reinstall app/build/outputs/apk/debug/app-x86_64-debug.apk
