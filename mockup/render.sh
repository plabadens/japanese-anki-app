#!/usr/bin/env bash
# Renders every mockup/screens/*.html to a PNG at 2x device scale (824x1830,
# from a 412x915 CSS-px viewport — the Pixel 7 reference used in SPEC.md's
# acceptance tests). Uses headless Chromium fetched via Nix, so this is
# reproducible without any project-local dependency.
#
# Verified against Chromium 153.0.8010.52 (resolved from nixpkgs at the time
# this script was written). A later nixpkgs revision may resolve a different
# pinned Chromium version; that is expected and fine — the rendering approach
# does not depend on an exact version.
#
# Usage: ./mockup/render.sh   (run from the repo root, or anywhere — it cd's
# to its own directory first)

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

SCREENS_DIR="$(pwd)/screens"
WIDTH=412
HEIGHT=915
SCALE=2
PROFILE_DIR="$(mktemp -d)"
trap 'rm -rf "$PROFILE_DIR"' EXIT

render_one() {
  local html="$1" width="$2" height="$3"
  local name png
  name="$(basename "$html" .html)"
  png="$SCREENS_DIR/$name.png"
  nix-shell -p chromium --run "chromium \
    --headless --disable-gpu --no-sandbox --hide-scrollbars \
    --user-data-dir='$PROFILE_DIR' \
    --force-device-scale-factor=$SCALE \
    --window-size=${width},${height} \
    --screenshot=$png \
    file://$html" >/dev/null 2>&1
  if [[ -f "$png" ]]; then
    local bytes dims
    bytes=$(stat -c%s "$png" 2>/dev/null || stat -f%z "$png")
    dims=$(nix-shell -p imagemagick --run "identify -format '%wx%h' '$png'" 2>/dev/null || echo "?")
    printf '%-32s %10s px  %8s bytes\n' "$name.png" "$dims" "$bytes"
  else
    printf '%-32s FAILED\n' "$name.png"
  fi
}

echo "Rendering mockup screens (Chromium headless, ${WIDTH}x${HEIGHT} @ ${SCALE}x)…"
echo

# Screens whose content is taller than one device screen (full settings list,
# the licenses page, the dev pitch-component reference) get a taller window
# so nothing is clipped, instead of scrolling/cropping. `chromium --screenshot`
# has no full-page-capture flag, so these heights are picked generously by
# hand rather than measured — a little trailing whitespace at the bottom is
# harmless; clipped content is not.
declare -A TALL_HEIGHTS=(
  ["27-settings-main"]=920
  ["32-settings-licenses"]=880
  ["00-pitch-component"]=1420
)

for html in "$SCREENS_DIR"/*.html; do
  name="$(basename "$html" .html)"
  if [[ -n "${TALL_HEIGHTS[$name]:-}" ]]; then
    render_one "$html" "$WIDTH" "${TALL_HEIGHTS[$name]}"
  else
    render_one "$html" "$WIDTH" "$HEIGHT"
  fi
done

echo
echo "Done. $(ls "$SCREENS_DIR"/*.png 2>/dev/null | wc -l) PNGs in $SCREENS_DIR."
