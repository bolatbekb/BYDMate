#!/usr/bin/env bash
# Builds the Graphite gallery photos: tools/graphite/img_src/*.jpg -> cropped 920x440 -> lossy WebP in
# res/drawable-nodpi (each must stay <= 60 000 bytes; quality steps 80 -> 72 -> 65).
# Needs cwebp from libwebp (https://developers.google.com/speed/webp/download) and Java 17:
#   CWEBP_DIR=/path/to/libwebp-x.y.z-linux-x86-64/bin tools/graphite/photos.sh
set -euo pipefail
BIN="${CWEBP_DIR:?set CWEBP_DIR to the folder with cwebp}"
HERE="$(cd "$(dirname "$0")" && pwd)"
RES="$(cd "$HERE/../.." && pwd)/app/src/main/res/drawable-nodpi"
LIMIT=60000
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
java "$HERE/GraphitePhotos.java" "$HERE/img_src" "$TMP"
mkdir -p "$RES"
for png in "$TMP"/gr_photo_*.png; do
  name="$(basename "$png" .png)"; out="$RES/$name.webp"; ok=""
  for q in 80 72 65; do
    "$BIN/cwebp" -quiet -q "$q" -m 6 -metadata none "$png" -o "$out"
    size=$(stat -c %s "$out")
    if [ "$size" -le "$LIMIT" ]; then ok=1; break; fi
  done
  [ -n "$ok" ] || { echo "$name: still $size bytes at q 65 (limit $LIMIT)" >&2; exit 1; }
  printf '%-22s q%-3d %7d bytes\n' "$name" "$q" "$size"
done
