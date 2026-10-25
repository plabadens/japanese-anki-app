#!/usr/bin/env bash
# Shared Gradle options for CI stages. Run inside the flake's development shell.
set -euo pipefail
: "${ANDROID_HOME:?Run inside the Nix development shell}"

# CI has network: a cold cache after dependency changes must be able to resolve artifacts.
# Reproducible offline builds remain the Nix derivation's job.
exec gradle --no-daemon --configuration-cache --build-cache --max-workers=2 --console=plain \
  -Panki.testForks=2 \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  "$@"
