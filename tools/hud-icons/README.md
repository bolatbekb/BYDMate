# HUD maneuver icons (fork)

The fork's own maneuver pictures for the windshield HUD, `app/src/main/assets/navi_fork/0x<gaode hex>.png`. They are 38×41 RGBA, the same format and names as the author's `assets/navi`, and were drawn from scratch. The digits are outlines of Inter Bold (SIL OFL).

The app uses them only while Settings → «Дисплей» → HUD → «Использовать свои иконки для навигатора» is on. The order is: this set → the author's `assets/navi` → the navigator's own picture.

- `svg/0x<hex>.svg` — the source of every shipped PNG.
- `gen_hud.py` — the geometry. It writes `svg/` and `codes.md`, and needs `fontTools` and Inter Bold (`FONT` at the top of the file).
- `render_hud.js` — renders `svg/` to `png/38x41` (and 2×–4×) with Playwright (`npm i playwright`).
- `codes.md` — what each code means. The rings are counter-clockwise (right-hand traffic) except 0x14, 0x16, 0x17 and 0x23–0x2c (left-hand).

Regenerate:

    python3 tools/hud-icons/gen_hud.py <work-dir> && node tools/hud-icons/render_hud.js <work-dir>

Then copy `<work-dir>/png/38x41/0x*.png` into `app/src/main/assets/navi_fork/`. Leave out `0x0.png`: code 0 never gets an icon.
