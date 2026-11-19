#!/usr/bin/env python3
"""Generate original HUD maneuver icons (38x41 px grid) as SVG.

All geometry is constructed here from scratch (turtle-style paths), nothing is
traced from third-party artwork. Digits use Inter Bold (SIL OFL) outlines.

Usage: python3 gen_hud.py <out_dir>
"""
import math
import os
import sys

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

W, H = 38, 41
BLUE_TOP, BLUE_BOT = "#A6DCFF", "#3D9BFF"   # route gradient (top -> bottom)
GREY = "#5E636B"                              # context roads
GAP = 1.6        # transparent gap between blue and grey
ROUND = 1.4      # arrowhead corner rounding (stroke width)

# Swap if your head unit treats 0x13/0x15 as left-hand traffic and 0x14/0x16 as right-hand.
SWAP_RING_HANDEDNESS = False


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rad(a):
    return math.radians(a)


class Turtle:
    """Path builder: heading in screen degrees (0 = right, 90 = down, -90 = up)."""

    def __init__(self, x, y, h):
        self.x, self.y, self.h = x, y, h
        self.segs = [("M", x, y)]

    def fwd(self, d):
        self.x += d * math.cos(rad(self.h))
        self.y += d * math.sin(rad(self.h))
        self.segs.append(("L", self.x, self.y))
        return self

    def turn(self, a, r):
        """a > 0: turn right (clockwise on screen), a < 0: turn left."""
        side = 1 if a > 0 else -1
        h = rad(self.h)
        cx = self.x + r * (-math.sin(h)) * side
        cy = self.y + r * (math.cos(h)) * side
        self.h += a
        h2 = rad(self.h)
        self.x = cx - r * (-math.sin(h2)) * side
        self.y = cy - r * (math.cos(h2)) * side
        self.segs.append(("A", r, 1 if abs(a) > 180 else 0, 1 if a > 0 else 0, self.x, self.y))
        return self

    def dir(self):
        return math.cos(rad(self.h)), math.sin(rad(self.h))

    def d(self, m=False):
        out = []
        for s in self.segs:
            if s[0] in "ML":
                x = W - s[1] if m else s[1]
                out.append(f"{s[0]}{fmt(x)} {fmt(s[2])}")
            else:
                _, r, la, sw, x, y = s
                if m:
                    x, sw = W - x, 1 - sw
                out.append(f"A{fmt(r)} {fmt(r)} 0 {la} {sw} {fmt(x)} {fmt(y)}")
        return " ".join(out)


def arrowhead(t, L, HW, inset=1.6):
    """Arrowhead whose base is at the turtle position; the stroke runs `inset` into it."""
    dx, dy = t.dir()
    nx, ny = -dy, dx
    bx, by = t.x, t.y
    tri = [(bx + L * dx, by + L * dy), (bx + HW * nx, by + HW * ny), (bx - HW * nx, by - HW * ny)]
    t.fwd(inset)
    return tri


def poly_d(pts, m=False):
    pts = [(W - x, y) if m else (x, y) for x, y in pts]
    return "M" + " L".join(f"{fmt(x)} {fmt(y)}" for x, y in pts) + " Z"


class Icon:
    def __init__(self, sw):
        self.sw = sw
        self.routes = []     # Turtle objects (blue strokes)
        self.heads = []      # triangles (blue)
        self.blue_fill = []  # extra filled blue shapes: (svg-element-string-builder)
        self.grey = []       # (kind, data, width)
        self.text = None     # pre-built path d (never mirrored)
        self.fade = None     # y where the faded grey road becomes solid

    # bbox of everything except off-canvas stems (approximate, for centering)
    def xbounds(self):
        xs = []
        for t in self.routes:
            for s in t.segs:
                x = s[1] if s[0] in "ML" else s[4]
                xs += [x - self.sw / 2, x + self.sw / 2]
        for tri in self.heads:
            xs += [p[0] - ROUND / 2 for p in tri] + [p[0] + ROUND / 2 for p in tri]
        for kind, data, w in self.grey:
            if kind == "circle":
                cx, cy, r = data
                xs += [cx - r - w / 2, cx + r + w / 2]
            elif kind in ("turtle", "fade"):
                for s in data.segs:
                    x = s[1] if s[0] in "ML" else s[4]
                    xs += [x - w / 2, x + w / 2]
        return min(xs), max(xs)

    def svg(self, m=False):
        o = []
        o.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">')
        o.append("<defs>")
        o.append(f'<linearGradient id="b" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{H}">'
                 f'<stop offset="0" stop-color="{BLUE_TOP}"/><stop offset="1" stop-color="{BLUE_BOT}"/></linearGradient>')
        if self.grey:
            o.append(f'<mask id="k" maskUnits="userSpaceOnUse" x="-8" y="-8" width="{W + 16}" height="{H + 16}">')
            o.append(f'<rect x="-8" y="-8" width="{W + 16}" height="{H + 16}" fill="#fff"/>')
            for t in self.routes:
                o.append(f'<path d="{t.d(m)}" fill="none" stroke="#000" stroke-width="{fmt(self.sw + 2 * GAP)}" '
                         f'stroke-linejoin="round"/>')
            for tri in self.heads:
                o.append(f'<path d="{poly_d(tri, m)}" fill="#000" stroke="#000" '
                         f'stroke-width="{fmt(ROUND + 2 * GAP)}" stroke-linejoin="round"/>')
            for el in self.blue_fill:
                o.append(el(m, mask=True))
            o.append("</mask>")
        if self.fade is not None:
            o.append(f'<linearGradient id="f" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{fmt(self.fade)}">'
                     f'<stop offset="0" stop-color="{GREY}" stop-opacity="0.25"/>'
                     f'<stop offset="1" stop-color="{GREY}"/></linearGradient>')
        o.append("</defs>")
        if self.grey:
            o.append(f'<g mask="url(#k)" fill="none" stroke="{GREY}">')
            for kind, data, w in self.grey:
                if kind == "circle":
                    cx, cy, r = data
                    cx = W - cx if m else cx
                    o.append(f'<circle cx="{fmt(cx)}" cy="{fmt(cy)}" r="{fmt(r)}" stroke-width="{fmt(w)}"/>')
                elif kind == "turtle":
                    o.append(f'<path d="{data.d(m)}" stroke-width="{fmt(w)}"/>')
                elif kind == "fade":
                    o.append(f'<path d="{data.d(m)}" stroke="url(#f)" stroke-width="{fmt(w)}"/>')
                elif kind == "fill":
                    o.append(data(m))
            o.append("</g>")
        for t in self.routes:
            o.append(f'<path d="{t.d(m)}" fill="none" stroke="url(#b)" stroke-width="{fmt(self.sw)}" '
                     f'stroke-linejoin="round"/>')
        for tri in self.heads:
            o.append(f'<path d="{poly_d(tri, m)}" fill="url(#b)" stroke="url(#b)" '
                     f'stroke-width="{fmt(ROUND)}" stroke-linejoin="round"/>')
        for el in self.blue_fill:
            o.append(el(m, mask=False))
        if self.text:
            o.append(f'<path d="{self.text}" fill="url(#b)"/>')
        o.append("</svg>")
        return "\n".join(o) + "\n"


def centered(build, x0, step=1.0):
    """Build at x0, then shift so the drawing is horizontally centred (snapped to `step`)."""
    ic = build(x0)
    a, b = ic.xbounds()
    shift = round((W / 2 - (a + b) / 2) / step) * step
    return build(x0 + shift) if shift else ic


# ---------------------------------------------------------------- simple turns
T_SW, T_L, T_HW = 6, 9, 7.5


def turn_left(x):
    ic = Icon(T_SW)
    t = Turtle(x, H + 3, -90).fwd(H + 3 - 24)
    t.turn(-90, 6)
    t.fwd(t.x - (6 + T_L))                      # tip at x = 6
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    ic.grey.append(("fade", Turtle(x, 0, 90).fwd(24), T_SW))
    ic.fade = 24
    return ic


def slight_left(x):
    ic = Icon(T_SW)
    t = Turtle(x, H + 3, -90).fwd(H + 3 - 27)
    t.turn(-45, 7)
    tip_y = 5.0
    d = (t.y - tip_y) / math.sin(rad(45)) - T_L
    t.fwd(d)
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    ic.grey.append(("fade", Turtle(x, 0, 90).fwd(27), T_SW))
    ic.fade = 27
    return ic


def sharp_left(x):
    ic = Icon(T_SW)
    t = Turtle(x, H + 3, -90).fwd(H + 3 - 21)
    t.turn(-135, 5)
    tip_x = 5.5
    d = (t.x - tip_x) / math.cos(rad(45)) - T_L
    t.fwd(d)
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    ic.grey.append(("fade", Turtle(x, 0, 90).fwd(21), T_SW))
    ic.fade = 21
    return ic


def uturn_left(x):
    ic = Icon(T_SW)
    t = Turtle(x, H + 3, -90).fwd(H + 3 - 23)
    t.turn(-180, 8)
    t.fwd(31 - t.y)                              # base at y = 31, tip at 40
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    ic.grey.append(("fade", Turtle(x, 0, 90).fwd(23), T_SW))
    ic.fade = 23
    return ic


def straight(_x=19):
    ic = Icon(T_SW)
    t = Turtle(19, H + 3, -90).fwd(H + 3 - 11)   # base y = 11, tip y = 2
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    return ic


def straight_dashed(_x=19):
    ic = Icon(T_SW)
    t = Turtle(19, 25, -90).fwd(25 - 11)
    ic.heads.append(arrowhead(t, T_L, T_HW))
    ic.routes.append(t)
    ic.routes.append(Turtle(19, 28.5, -90).fwd(-4.5))      # dash 28.5..33
    ic.routes.append(Turtle(19, 36.5, -90).fwd(-(H + 3 - 36.5)))  # dash 36.5..edge
    return ic


# ---------------------------------------------------------------- roundabouts
R_SW, R_L, R_HW = 5, 7, 6
R_R, R_F = 8.0, 1.5    # ring radius (centre line) and fillet radius
RING_W = 4             # grey ring width


def ring_route(ic, cx, cy, R, f, s_in, exit_dir=None, exit_off=0.0, d_tip=0.0,
               end_theta=None, L=R_L, HW=R_HW):
    """Counter-clockwise (right-hand traffic) route: enter from the bottom on x = cx + s_in,
    go round the ring, then either leave along a straight exit line (direction exit_dir,
    offset exit_off to the right of it; arrow tip at distance d_tip) or stop on the ring
    at screen angle end_theta with an arrowhead along the ring."""
    a = s_in + f
    yf = cy + math.sqrt((R + f) ** 2 - a ** 2)
    th1 = math.degrees(math.atan2(yf - cy, a))
    t = Turtle(cx + s_in, H + 3, -90).fwd(H + 3 - yf)
    t.turn(th1, f)
    if exit_dir is None:
        beta = (th1 - end_theta) % 360
        t.turn(-beta, R)
        tri = arrowhead(t, L, HW)
    else:
        ex, ey = math.cos(rad(exit_dir)), math.sin(rad(exit_dir))
        nrx, nry = -ey, ex
        qx, qy = exit_off * nrx + f * nrx, exit_off * nry + f * nry
        qe = qx * ex + qy * ey
        tt = -qe + math.sqrt(qe * qe - (qx * qx + qy * qy) + (R + f) ** 2)
        c2x, c2y = qx + tt * ex, qy + tt * ey
        th2 = math.degrees(math.atan2(c2y, c2x))
        beta = (th1 - th2) % 360
        t.turn(-beta, R)
        alpha2 = (exit_dir - (th2 - 90)) % 360
        t.turn(alpha2, f)
        t.fwd((d_tip - L) - tt)
        tri = arrowhead(t, L, HW)
    ic.routes.append(t)
    ic.heads.append(tri)


def ring_icon(exit_dir, d_tip, cy=21.5, exit_off=2.0, s_in=2.0, HW=R_HW, grey_ring=True):
    def build(cx):
        ic = Icon(R_SW)
        if grey_ring:
            ic.grey.append(("circle", (cx, cy, R_R), RING_W))
        ring_route(ic, cx, cy, R_R, R_F, s_in, exit_dir=exit_dir, exit_off=exit_off, d_tip=d_tip, HW=HW)
        return ic
    return centered(build, 19.5)


def ring_enter():
    R = 8.5
    def build(cx):
        ic = Icon(R_SW)
        cy = 19.5
        ic.grey.append(("circle", (cx, cy, R), RING_W))
        ring_route(ic, cx, cy, R, R_F, 2, end_theta=-35, L=7, HW=5.5)
        return ic
    return centered(build, 19.5)


def ring_exit():
    """Leaving the ring (right-hand traffic: arrive from the right, exit straight up)."""
    ic = Icon(R_SW)
    cx, ca, Ra, f = 19.5, 38.0, 13.0, 4.0
    arch = Turtle(cx - Ra, H + 3, -90).fwd(H + 3 - ca)
    arch.turn(180, Ra)
    arch.fwd(H + 3 - ca)
    ic.grey.append(("turtle", arch, R_SW))
    beta = math.degrees(math.acos(f / (Ra + f)))
    t = Turtle(cx + Ra, H + 3, -90).fwd(H + 3 - ca)
    t.turn(-beta, Ra)
    t.turn(beta, f)
    L, HW = 8, 7
    t.fwd(t.y - (2 + L))
    ic.heads.append(arrowhead(t, L, HW))
    ic.routes.append(t)
    return ic


# numbered exits
N_SW, N_R = 4, 12.0


def digits_path(s, cx, cy, height):
    font = TTFont(FONT)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    track = -0.03 * font["head"].unitsPerEm
    glyphs, x = [], 0
    for ch in s:
        g = cmap[ord(ch)]
        glyphs.append((g, x))
        x += font["hmtx"][g][0] + track
    bp = BoundsPen(gs)
    for g, gx in glyphs:
        gs[g].draw(TransformPen(bp, (1, 0, 0, 1, gx, 0)))
    x0, y0, x1, y1 = bp.bounds
    k = height / (y1 - y0)
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    sp = SVGPathPen(gs, ntos=lambda v: fmt(v))
    for g, gx in glyphs:
        tp = TransformPen(sp, (k, 0, 0, -k, cx - (mx - gx) * k, cy + my * k))
        gs[g].draw(tp)
    return sp.getCommands()


def ring_numbered(n):
    ic = Icon(N_SW)
    cx, cy = 19, 17.5
    ic.grey.append(("circle", (cx, cy, N_R), N_SW))
    ring_route(ic, cx, cy, N_R, 3.0, 0, end_theta=-112, L=6.5, HW=5)
    ic.text = digits_path(str(n), cx, cy + 0.2, 10.5 if n < 10 else 9.5)
    return ic


# ---------------------------------------------------------------- finish flag
def finish():
    ic = Icon(3)
    pole = Turtle(9.5, H + 3, -90).fwd(H + 3 - 5)
    ic.routes.append(pole)
    x0, y0, cw, ch, cols, rows = 11, 5, 6, 5, 4, 3

    def cells(fill_parity, colour):
        def el(m, mask=False):
            parts = []
            for r in range(rows):
                for c in range(cols):
                    if (r + c) % 2 != fill_parity:
                        continue
                    x = x0 + c * cw
                    if m:
                        x = W - x - cw
                    parts.append(f"M{x} {y0 + r * ch}h{cw}v{ch}h{-cw}Z")
            fill = "#000" if mask else colour
            return f'<path d="{"".join(parts)}" fill="{fill}" stroke="none"/>'
        return el

    clip_w = cols * cw
    ic.blue_fill.append(cells(0, "url(#b)"))
    ic.grey.append(("fill", lambda m: cells(1, GREY)(m).replace('stroke="none"', 'stroke="none"'), 0))
    # rounded flag corners via a top-level clip is overkill at this size; keep crisp squares
    pole_cap = lambda m, mask=False: (f'<circle cx="{fmt(W - 9.5 if m else 9.5)}" cy="5" r="1.5" '
                                      f'fill="{"#000" if mask else "url(#b)"}"/>')
    ic.blue_fill.append(pole_cap)
    _ = clip_w
    return ic


# ---------------------------------------------------------------- catalogue
def m(fn):
    """Mirrored version marker."""
    return ("mirror", fn)


ICONS = {}


def add(code, meaning, build, mirror=False):
    ICONS[code] = (meaning, build, mirror)


add("0x1", "налево", lambda: centered(turn_left, 29))
add("0x2", "направо", lambda: centered(turn_left, 29), mirror=True)
add("0x3", "плавно налево / развилка влево", lambda: centered(slight_left, 27))
add("0x4", "плавно направо / развилка вправо", lambda: centered(slight_left, 27), mirror=True)
add("0x7", "резко налево (и съезд с шоссе влево)", lambda: centered(sharp_left, 29))
add("0x8", "резко направо (и съезд с шоссе вправо)", lambda: centered(sharp_left, 29), mirror=True)
add("0x9", "разворот налево", lambda: centered(uturn_left, 29))
add("0xa", "разворот направо", lambda: centered(uturn_left, 29), mirror=True)
add("0xb", "прямо", straight)
add("0xc", "прямо (вариант)", straight_dashed)
add("0xd", "въезд на кольцо", ring_enter)
add("0xe", "кольцо, плавно налево", lambda: ring_icon(-135, 21.5, exit_off=1.5))
add("0xf", "кольцо, налево", lambda: ring_icon(180, 21))
add("0x10", "кольцо, резко налево", lambda: ring_icon(145, 21.5, exit_off=1.5, grey_ring=False))
add("0x11", "кольцо, направо", lambda: ring_icon(0, 21))
add("0x12", "кольцо, резко направо", lambda: ring_icon(35, 21.5, exit_off=1.5))
rh_straight = lambda: ring_icon(-90, 21, cy=22.5)
rh_uturn = lambda: ring_icon(90, 20.5, cy=19.5, exit_off=7, s_in=5, HW=5.5, grey_ring=False)
add("0x13", "кольцо, прямо", rh_straight, mirror=SWAP_RING_HANDEDNESS)
add("0x14", "кольцо, прямо (левостороннее)", rh_straight, mirror=not SWAP_RING_HANDEDNESS)
add("0x15", "кольцо, разворот", rh_uturn, mirror=SWAP_RING_HANDEDNESS)
add("0x16", "кольцо, разворот (левостороннее)", rh_uturn, mirror=not SWAP_RING_HANDEDNESS)
add("0x17", "съезд с кольца (влево)", ring_exit, mirror=True)
add("0x18", "съезд с кольца", ring_exit)
for i in range(10):
    n = i + 1
    add(hex(0x19 + i), f"кольцо, съезд {n}", (lambda n=n: ring_numbered(n)))
for i in range(10):
    n = i + 1
    add(hex(0x23 + i), f"кольцо, съезд {n} (левостороннее, по картинке из архива)",
        (lambda n=n: ring_numbered(n)), mirror=True)
add("0x30", "финиш", finish)

EMPTY = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"/>\n'

if __name__ == "__main__":
    os.makedirs(f"{OUT}/svg", exist_ok=True)
    with open(f"{OUT}/svg/0x0.svg", "w") as fh:
        fh.write(EMPTY)
    lines = ["| код | что означает |", "|---|---|", "| 0x0 | нет манёвра (пусто) |"]
    for code, (meaning, build, mirror) in ICONS.items():
        ic = build()
        with open(f"{OUT}/svg/{code}.svg", "w") as fh:
            fh.write(ic.svg(mirror))
        lines.append(f"| {code} | {meaning} |")
    with open(f"{OUT}/codes.md", "w") as fh:
        fh.write("\n".join(lines) + "\n")
    print(len(ICONS) + 1, "icons")
