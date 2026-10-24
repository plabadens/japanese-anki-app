#!/usr/bin/env bash
# Fast local build loop.
#
# The flake still owns the toolchain — pinned JDK, Android SDK, Robolectric jars, CJK fonts —
# but it is entered once per session instead of once per command. `nix develop` spends ~12 s
# evaluating before Gradle starts, and every fresh shell gets a different daemon context, so the
# old habit of wrapping each command threw away the warm daemon, the configuration cache and the
# build cache on every edit. This captures the exported environment once (keyed on flake.nix and
# flake.lock), then runs Gradle directly so all three stay warm.
#
# Usage:
#   scripts/dev.sh test [<test-pattern>…]   # verifyFast; with patterns, only matching classes
#   scripts/dev.sh render                   # renderDesignStates -> build/design-states
#   scripts/dev.sh apk                      # assembleDebug (both ABIs)
#   scripts/dev.sh emulator <args…>         # scripts/emulator.sh in the same environment
#   scripts/dev.sh gradle <args…>           # any Gradle invocation
#   scripts/dev.sh env                      # print the cached environment file
#
# The two preview catalogues are excluded from the unit-test task by the build and only run
# under `render`; passing one of those class names to `test` finds no tests by design.
#
# DEV_GRADLE_ONLINE=1 drops --offline for the one run after a dependency change.
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/dev.sh test [<test-pattern>…]   # verifyFast; with patterns, only matching classes
  scripts/dev.sh render                   # renderDesignStates -> build/design-states
  scripts/dev.sh apk                      # assembleDebug (both ABIs)
  scripts/dev.sh emulator <args…>         # scripts/emulator.sh in the same environment
  scripts/dev.sh gradle <args…>           # any Gradle invocation
  scripts/dev.sh env                      # print the cached environment file

The preview catalogues are excluded from the unit-test task and only run under `render`.
DEV_GRADLE_ONLINE=1 drops --offline for the run after a dependency change.
USAGE
  exit 2
}

env_file=build/dev-env.sh
stamp_file=build/dev-env.stamp
stamp=$(cat flake.nix flake.lock | sha256sum | cut -d' ' -f1)

if [[ ! -s $env_file || ! -s $stamp_file || $(cat "$stamp_file") != "$stamp" ]]; then
  printf 'dev.sh: capturing the Nix dev-shell environment (once per flake change)\n' >&2
  mkdir -p build
  nix develop --command bash -c 'export -p' >"$env_file.tmp"
  mv "$env_file.tmp" "$env_file"
  printf '%s\n' "$stamp" >"$stamp_file"
fi

# shellcheck disable=SC1090
source "$env_file"
: "${ANDROID_HOME:?dev.sh: cached environment has no ANDROID_HOME; delete $env_file and retry}"

gradle_args=(
  --configuration-cache
  --build-cache
  --console=plain
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"
)
if [[ ${DEV_GRADLE_ONLINE:-0} != 1 ]]; then
  gradle_args+=(--offline)
fi

command=${1:-}
[[ -n $command ]] || usage
shift

case "$command" in
  test)
    if (( $# == 0 )); then
      gradle "${gradle_args[@]}" verifyFast
    else
      filters=()
      for pattern in "$@"; do filters+=(--tests "$pattern"); done
      gradle "${gradle_args[@]}" :app:testDebugUnitTest "${filters[@]}"
    fi
    ;;
  render)
    gradle "${gradle_args[@]}" :app:renderDesignStates
    ;;
  apk)
    gradle "${gradle_args[@]}" assembleDebug
    ;;
  emulator)
    (( $# > 0 )) || usage
    exec scripts/emulator.sh "$@"
    ;;
  gradle)
    (( $# > 0 )) || usage
    gradle "${gradle_args[@]}" "$@"
    ;;
  env)
    printf '%s\n' "$env_file"
    ;;
  *)
    usage
    ;;
esac
