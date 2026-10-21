#!/usr/bin/env bash
# CI entry point for the Nix-backed debug build. Run from the repository root via
# `nix develop --command ./scripts/ci-build-apk.sh`.
set -euo pipefail

./scripts/test-core.sh

gradle --no-daemon --max-workers=2 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  testDebugUnitTest assembleDebug
