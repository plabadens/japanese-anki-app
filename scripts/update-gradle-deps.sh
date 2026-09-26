#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
update_script="$(nix build --no-link --print-out-paths .#apk.mitmCache.updateScript)"
exec "$update_script"
