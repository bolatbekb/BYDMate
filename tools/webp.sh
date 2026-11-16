#!/usr/bin/env bash
# Converts the retro homes' PNG artwork in res/drawable-nodpi to lossless WebP and checks that
# every pixel decodes back identical before replacing the PNG.
# Needs cwebp and dwebp from libwebp (https://developers.google.com/speed/webp/download):
#   CWEBP_DIR=/path/to/libwebp-x.y.z-linux-x86-64/bin tools/webp.sh
set -euo pipefail
BIN="${CWEBP_DIR:?set CWEBP_DIR to the folder with cwebp and dwebp}"
RES="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/drawable-nodpi"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
before=0; after=0
for png in "$RES"/cassette_*.png "$RES"/rs_*.png "$RES"/cd_*.png; do
  [ -e "$png" ] || continue
  name="$(basename "$png" .png)"; webp="$RES/$name.webp"
  "$BIN/cwebp" -quiet -lossless -exact -z 9 -metadata none "$png" -o "$TMP/$name.webp"
  "$BIN/dwebp" -quiet "$TMP/$name.webp" -pam -o "$TMP/$name.pam"
  java "$(dirname "$0")/SamePixels.java" "$png" "$TMP/$name.pam"
  b=$(stat -c %s "$png"); a=$(stat -c %s "$TMP/$name.webp"); before=$((before+b)); after=$((after+a))
  mv "$TMP/$name.webp" "$webp"; rm "$png"
  printf '%-34s %8d -> %8d\n' "$name" "$b" "$a"
done
printf 'total %d -> %d bytes\n' "$before" "$after"
