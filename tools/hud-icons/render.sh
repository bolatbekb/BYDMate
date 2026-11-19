#!/usr/bin/env bash
# fork: regenerate the fork's HUD maneuver icons into app/src/main/assets/navi_fork (38x41, like assets/navi).
# Each drawing is fitted to the frame: measured on a 384 px render, then its viewBox is cropped to the drawing.
# Needs python3 and chromium. Usage: tools/hud-icons/render.sh [work-dir]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
WORK=${1:-$HOME/hud-icons-work}; OUT=$ROOT/app/src/main/assets/navi_fork
mkdir -p "$WORK/html" "$WORK/big" "$OUT"   # chromium (snap) cannot read /tmp, so the work dir lives in $HOME
python3 -I "$HERE/draw.py" "$WORK"
shot() {
  printf '<html><body style="margin:0;background:transparent"><img src="file://%s" width=%s height=%s></body></html>' "$1" "$3" "$4" > "$WORK/html/x.html"
  chromium --headless=old --disable-gpu --no-sandbox --allow-file-access-from-files --hide-scrollbars \
    --default-background-color=00000000 --window-size="$3,$4" --screenshot="$2" "file://$WORK/html/x.html" >/dev/null 2>&1
}
for f in "$WORK"/src/*.svg; do
  c=$(basename "$f" .svg)
  shot "$f" "$WORK/big/$c.png" 384 384
  python3 -I "$HERE/fit.py" "$f" "$WORK/big/$c.png"
  shot "$f" "$OUT/$c.png" 38 41
done
echo "$(ls "$OUT"/0x*.png | wc -l) icons in $OUT"
