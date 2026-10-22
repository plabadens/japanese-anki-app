#!/usr/bin/env bash
set -euo pipefail

if ! command -v gradle >/dev/null; then
  echo "Run this from the Nix development shell (nix develop)." >&2
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set; run this from the Nix development shell." >&2
  exit 1
fi

aapt2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
if [[ ! -x "$aapt2" ]]; then
  echo "Android SDK Build Tools 36.0.0 not found at $aapt2." >&2
  exit 1
fi

gradle :app:testDebugUnitTest --no-daemon --max-workers=1 \
  -Pandroid.aapt2FromMavenOverride="$aapt2"
