#!/usr/bin/env python3
"""Converts the Graphite design SVG icons (24x24, stroke 1.5) to Android VectorDrawables.

    python3 tools/graphite/icons.py tools/graphite/icons app/src/main/res/drawable

ic_<name>.svg -> gr_ic_<name>.xml. Every shape becomes a <path>; colour is applied by tint in code.
Stdlib only. Fails (naming the file) on any element it does not know.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SVG = "{http://www.w3.org/2000/svg}"
STROKE = ('strokeColor="#FFFFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" '
          'android:strokeLineJoin="round" android:fillColor="#00000000"')
FILL = 'fillColor="#FFFFFFFF"'


def n(v):
    s = ("%.4f" % float(v)).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def circle(e):
    cx, cy, r = (float(e.get(k)) for k in ("cx", "cy", "r"))
    return f"M{n(cx - r)},{n(cy)} a{n(r)},{n(r)} 0 1,0 {n(2 * r)},0 a{n(r)},{n(r)} 0 1,0 {n(-2 * r)},0"


def rect(e):
    x, y, w, h = (float(e.get(k)) for k in ("x", "y", "width", "height"))
    r = min(float(e.get("rx") or e.get("ry") or 0), w / 2, h / 2)
    if r == 0:
        return f"M{n(x)},{n(y)} h{n(w)} v{n(h)} h{n(-w)} z"
    a = f"a{n(r)},{n(r)} 0 0 1"
    return (f"M{n(x + r)},{n(y)} h{n(w - 2 * r)} {a} {n(r)},{n(r)} v{n(h - 2 * r)} {a} {n(-r)},{n(r)} "
            f"h{n(-(w - 2 * r))} {a} {n(-r)},{n(-r)} v{n(-(h - 2 * r))} {a} {n(r)},{n(-r)} z")


def points(e, close):
    nums = [float(v) for v in e.get("points").replace(",", " ").split()]
    pts = list(zip(nums[0::2], nums[1::2]))
    return "M" + " L".join(f"{n(x)},{n(y)}" for x, y in pts) + (" z" if close else "")


def line(e):
    x1, y1, x2, y2 = (float(e.get(k)) for k in ("x1", "y1", "x2", "y2"))
    return f"M{n(x1)},{n(y1)} L{n(x2)},{n(y2)}"


def convert(src: Path) -> str:
    out = []
    for e in ET.parse(src).getroot():
        tag = e.tag.replace(SVG, "")
        if tag == "path":
            d = e.get("d")
        elif tag == "circle":
            d = circle(e)
        elif tag == "rect":
            d = rect(e)
        elif tag == "line":
            d = line(e)
        elif tag in ("polyline", "polygon"):
            d = points(e, tag == "polygon")
        else:
            raise SystemExit(f"{src.name}: unsupported element <{tag}>")
        paint = FILL if e.get("fill") == "currentColor" else STROKE
        out.append(f'    <path\n        android:pathData="{d}"\n        android:{paint} />')
    body = "\n".join(out)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            f"{body}\n</vector>\n")


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: icons.py IN_DIR OUT_DIR")
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    dst.mkdir(parents=True, exist_ok=True)
    files = sorted(src.glob("ic_*.svg"))
    for f in files:
        (dst / f"gr_{f.stem}.xml").write_text(convert(f), encoding="utf-8")
    print(f"{len(files)} icons -> {dst}")


if __name__ == "__main__":
    main()
