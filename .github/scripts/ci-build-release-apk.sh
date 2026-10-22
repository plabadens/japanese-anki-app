#!/usr/bin/env bash
# CI entry point for the release-signed arm64-v8a APK. Run from the repository root via
# `nix develop --command ./.github/scripts/ci-build-release-apk.sh`.
#
# Mirrors scripts/ci-build-apk.sh (same gradle flags, same unit-test gate) but assembles the
# release variant. app/build.gradle.kts reads the signing material from the environment and
# falls back to an unsigned build when it is absent, so the guards below are what keep CI from
# publishing an unsigned APK by accident.
set -euo pipefail

: "${JAPANESE_ANKI_RELEASE_KEYSTORE:?JAPANESE_ANKI_RELEASE_KEYSTORE must point at the release keystore}"
: "${JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD:?JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD is required}"
: "${JAPANESE_ANKI_RELEASE_KEY_ALIAS:?JAPANESE_ANKI_RELEASE_KEY_ALIAS is required}"

if [[ ! -f "$JAPANESE_ANKI_RELEASE_KEYSTORE" ]]; then
  printf 'release keystore not found at %s\n' "$JAPANESE_ANKI_RELEASE_KEYSTORE" >&2
  exit 1
fi

./scripts/test-core.sh

gradle --no-daemon --max-workers=2 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  testDebugUnitTest assembleRelease
