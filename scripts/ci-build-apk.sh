#!/usr/bin/env bash
# Full local CI by default. --build-only is for the workflow job gated by lint and tests.
# Run from the repository root inside the Nix development shell.
set -euo pipefail

if (( $# == 0 )); then
  scripts/ci-gradle.sh verifyFast
  # Roborazzi owns the test filter: render in a separate invocation from verifyFast.
  scripts/ci-gradle.sh :app:renderDesignStates assembleDebug
elif [[ $# == 1 && $1 == --build-only ]]; then
  scripts/ci-gradle.sh assembleDebug
else
  printf 'Usage: %s [--build-only]\n' "$0" >&2
  exit 2
fi
