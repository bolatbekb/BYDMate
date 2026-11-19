# HUD maneuver icons (fork)

The fork's own maneuver pictures for the windshield HUD, drawn from scratch for projection on glass:
- light blue, with no dark blue (dark blue fades on glass in daylight);
- no blur, wide strokes, large arrowheads;
- neighbouring roads in grey;
- the roundabout exit number in a corner.

The app uses them only when Settings → «Дисплей» → HUD → «Использовать свои иконки для навигатора» is on. The order is: this set, then the author's `assets/navi`, then the navigator's own picture.

- `draw.py` holds the geometry, one function per maneuver. It writes `src/0x<gaode hex>.svg` (96×96) into the folder given.
- `fit.py` crops each SVG's viewBox to its drawing, with a 0.6-unit margin and the 38:41 aspect. The drawing then fills the frame as fully as it can, without any change to its content.
- `render.sh` runs draw → fit and renders `app/src/main/assets/navi_fork/0x<hex>.png` at 38×41, the same format as the author's set.

Codes: 0x1–0x4, 0x7–0x22 (0x19–0x22 = roundabout exits 1–10), 0x23–0x2c (clockwise roundabouts for left-hand traffic, exits 1–10), 0x30 finish.
Waypoint, rest area, toll and tunnel have no fork icon; the author's icon stays.
