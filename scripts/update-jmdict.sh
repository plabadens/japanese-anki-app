#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
destination="$root/datasets/jmdict/JMdict_e.gz"
temporary=$(mktemp "$destination.XXXXXX")
headers="$temporary.headers"
trap 'rm -f "$temporary" "$headers"' EXIT

curl --fail --location --retry 2 \
  --dump-header "$headers" \
  https://www.edrdg.org/pub/Nihongo/JMdict_e.gz \
  --output "$temporary"
gzip --test "$temporary"
last_modified=$(awk 'tolower($1) == "last-modified:" { $1 = ""; sub(/^[[:space:]]+/, ""); value = $0 } END { print value }' "$headers")
mv "$temporary" "$destination"
printf 'Upstream Last-Modified: %s\n' "$last_modified"
sha256sum "$destination"
printf 'Update the snapshot date/hash and build metadata, then rebuild/test the bundled database and APK.\n'
