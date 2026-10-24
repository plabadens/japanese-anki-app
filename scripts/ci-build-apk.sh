#!/usr/bin/env bash
# CI entry point for the Nix-backed debug build. Run from the repository root via
# `nix develop --command ./scripts/ci-build-apk.sh`.
set -euo pipefail

# Not --offline: the actions/cache key includes gradle/libs.versions.toml, so the first run after
# any dependency change always starts with a cold cache and would fail to resolve the new artifact.
# The runner has network; reproducible offline builds are the Nix derivation's job, not this script's.
gradle --no-daemon --configuration-cache --build-cache --max-workers=2 --console=plain \
  -Panki.testForks=2 \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  verifyFast

# Roborazzi's record task owns the unit-test filter while rendering the catalogue, so keep it in
# a separate Gradle invocation from verifyFast, which deliberately excludes those render tests.
gradle --no-daemon --configuration-cache --build-cache --max-workers=2 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:renderDesignStates assembleDebug
