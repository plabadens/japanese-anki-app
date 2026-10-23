#!/usr/bin/env bash
# CI entry point for the release-signed arm64-v8a APK. Run from the repository root via
# `nix develop --command ./.github/scripts/ci-build-release-apk.sh`.
#
# Mirrors scripts/ci-build-apk.sh (same Robolectric unit and catalogue gates) but assembles the
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

# Same reasoning as scripts/ci-build-apk.sh: a dependency change invalidates the cache key, and an
# offline build cannot resolve the new artifact on that first cold run. The runner has network.
gradle --no-daemon --configuration-cache --build-cache --max-workers=2 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  verifyFast

# Keep the Roborazzi catalogue in its own invocation; it owns the preview test filter.
gradle --no-daemon --configuration-cache --build-cache --max-workers=2 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:renderDesignStates assembleRelease
