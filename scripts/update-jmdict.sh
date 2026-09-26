#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
destination="$root/datasets/jmdict/JMdict_e.gz"
temporary=$(mktemp "$destination.XXXXXX")
trap 'rm -f "$temporary"' EXIT

curl --fail --location --retry 2 \
  https://www.edrdg.org/pub/Nihongo/JMdict_e.gz \
  --output "$temporary"
gzip --test "$temporary"
mv "$temporary" "$destination"
sha256sum "$destination"
printf 'Remember to update the snapshot date/hash in datasets/README.md and rebuild the bundled database/APK.\n'
