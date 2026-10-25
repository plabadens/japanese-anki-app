#!/usr/bin/env bash
# CI entry point for the release-signed arm64-v8a APK. Run from the repository root via
# `nix develop --command ./.github/scripts/ci-build-release-apk.sh`.
#
# Full local CI by default. --build-only is for the workflow job gated by lint and tests.
# app/build.gradle.kts falls back to unsigned builds without signing material; these guards
# keep CI from publishing one accidentally.
set -euo pipefail

: "${JAPANESE_ANKI_RELEASE_KEYSTORE:?JAPANESE_ANKI_RELEASE_KEYSTORE must point at the release keystore}"
: "${JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD:?JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD is required}"
: "${JAPANESE_ANKI_RELEASE_KEY_ALIAS:?JAPANESE_ANKI_RELEASE_KEY_ALIAS is required}"

if [[ ! -f "$JAPANESE_ANKI_RELEASE_KEYSTORE" ]]; then
  printf 'release keystore not found at %s\n' "$JAPANESE_ANKI_RELEASE_KEYSTORE" >&2
  exit 1
fi

if (( $# == 0 )); then
  scripts/ci-gradle.sh verifyFast
  # Keep the Roborazzi catalogue in its own invocation; it owns the preview test filter.
  scripts/ci-gradle.sh :app:renderDesignStates assembleRelease
elif [[ $# == 1 && $1 == --build-only ]]; then
  scripts/ci-gradle.sh assembleRelease
else
  printf 'Usage: %s [--build-only]\n' "$0" >&2
  exit 2
fi
