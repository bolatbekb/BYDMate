#!/usr/bin/env python3
"""BYDMate HUD maneuver icons, drawn from scratch: parametric SVG, 96x96 viewBox."""
import math, os, sys
OUT = sys.argv[1]; os.makedirs(f"{OUT}/src", exist_ok=True)
GREY, SW_BLUE, SW_GREY = "#6b7787", 14, 10
HEAD_L, HEAD_W = 20, 32
DEFS = ('<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="96" gradientUnits="userSpaceOnUse">'
        '<stop offset="0" stop-color="#e3f9ff"/><stop offset="1" stop-color="#5cc4ff"/></linearGradient>'
        '<filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="1.6" result="g"/>'
        '<feMerge><feMergeNode in="g"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>')
def svg(body): return f'<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96">{DEFS}{body}</svg>'
def grey(d): return f'<path d="{d}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}" stroke-linecap="round" stroke-linejoin="round"/>'
def head(tx, ty, ang, L=None, W=None):
    L = L or HEAD_L; W = W or HEAD_W
    a = math.radians(ang); dx, dy = math.cos(a), math.sin(a); px, py = -dy, dx
    bx, by = tx - L*dx, ty - L*dy
    pts = [(tx, ty), (bx + W/2*px, by + W/2*py), (bx - W/2*px, by - W/2*py)]
    return " ".join(f"{x:.1f},{y:.1f}" for x, y in pts), (bx, by)
def blue(d, tip, ang, L=None, W=None):
    pts, _ = head(tip[0], tip[1], ang, L, W)
    return (f'<g><path d="{d}" fill="none" stroke="url(#b)" stroke-width="{SW_BLUE}" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<polygon points="{pts}" fill="url(#b)" stroke="url(#b)" stroke-width="3" stroke-linejoin="round"/></g>')
def leg_to(x0, y0, ang, length, L=None):  # shaft from (x0,y0) toward ang, returns path tail and tip
    L = L or HEAD_L
    a = math.radians(ang); bx, by = x0 + length*math.cos(a), y0 + length*math.sin(a)
    return f"L{bx:.1f},{by:.1f}", (bx + L*math.cos(a), by + L*math.sin(a))
def mirror(s): return s.replace('<svg ', '<svg ').replace(DEFS, DEFS + '<g transform="translate(96,0) scale(-1,1)">', 1).replace('</svg>', '</g></svg>')
# --- crossroads
def straight():
    return svg(blue("M48,90 V30", (48, 8), -90))
def turn(dir_ang, ctx):
    a = math.radians(dir_ang); kx, ky = 48, 52
    t, tip = leg_to(kx + 8*math.cos(a), ky + 8*math.sin(a), dir_ang, 16)
    return svg(grey(ctx) + blue(f"M48,88 V{ky+8} Q{kx},{ky} {kx+8*math.cos(a):.1f},{ky+8*math.sin(a):.1f} {t}", tip, dir_ang))
def left(): return turn(180, "M48,52 V8")
def slight_left(): return turn(-125, "M48,52 V8")
def sharp_left():
    t, tip = leg_to(30, 62, 135, 0)
    return svg(grey("M48,28 V6") + blue("M48,90 V28 L27,49", (13, 63), 135))
def uturn_left():
    return svg(blue("M64,90 V38 A16,16 0 0 0 32,38 V56", (32, 78), 90))
# --- roundabouts (right-hand traffic: counter-clockwise on screen)
CX, CY, R = 46, 46, 16
RH_L, RH_W = 18, 28
def ring_pt(deg, r=R): a = math.radians(deg); return CX + r*math.cos(a), CY + r*math.sin(a)
def arc(start_deg, sweep):  # counter-clockwise on screen = decreasing angle
    parts, done, cur = [], 0, start_deg
    while done < sweep - 1e-6:
        step = min(170, sweep - done); nxt = cur - step
        x, y = ring_pt(nxt); parts.append(f"A{R},{R} 0 0 0 {x:.1f},{y:.1f}"); cur, done = nxt, done + step
    return " ".join(parts)
def roundabout(exit_deg, number=None, stubs=False):
    sweep = (90 - exit_deg) % 360 or 360
    sx, sy = ring_pt(90); ex, ey = ring_pt(exit_deg)
    t, tip = leg_to(ex, ey, exit_deg, 11, RH_L)
    g = f'<circle cx="{CX}" cy="{CY}" r="{R}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}"/>'
    if stubs:
        for d in (0, -90, 180):
            if abs(((d - exit_deg + 180) % 360) - 180) > 20:
                x0, y0 = ring_pt(d, R + 5); x1, y1 = ring_pt(d, R + 25); g += grey(f"M{x0:.1f},{y0:.1f} L{x1:.1f},{y1:.1f}")
    body = g + blue(f"M{CX},90 V{sy:.1f} {arc(90, sweep)} {t}", tip, exit_deg, RH_L, RH_W)
    if number is not None:
        fs = 30 if number >= 10 else 34
        body += (f'<text x="94" y="93" text-anchor="end" font-family="Inter,Roboto,Arial,sans-serif" '
                 f'font-weight="800" font-size="{fs}" fill="#ffffff" stroke="#1565c0" stroke-width="3" stroke-linejoin="round" paint-order="stroke">{number}</text>')
    return svg(body)
def ring_exit():  # leave the ring to the right
    sx, sy = ring_pt(160); ex, ey = ring_pt(0); t, tip = leg_to(ex, ey, 0, 9)
    g = f'<path d="M{ring_pt(200)[0]:.1f},{ring_pt(200)[1]:.1f} A{R},{R} 0 0 0 {ring_pt(-20)[0]:.1f},{ring_pt(-20)[1]:.1f}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}" stroke-linecap="round"/>'
    return svg(g + blue(f"M{sx:.1f},{sy:.1f} A{R},{R} 0 0 0 {ex:.1f},{ey:.1f} {t}", tip, 0))

def _arc_ccw(cx, cy, r, start_deg, sweep):
    parts, done, cur = [], 0, start_deg
    while done < sweep - 1e-6:
        step = min(170, sweep - done); nxt = cur - step
        a = math.radians(nxt); parts.append(f"A{r},{r} 0 0 0 {cx + r*math.cos(a):.1f},{cy + r*math.sin(a):.1f}"); cur, done = nxt, done + step
    return " ".join(parts)
def ring_uturn():  # right-hand traffic: in on the right, round the top, out on the left
    cx, cy, r = 48, 40, 20
    a30, a150 = math.radians(30), math.radians(150)
    x1, y1 = cx + r*math.cos(a30), cy + r*math.sin(a30); x2, y2 = cx + r*math.cos(a150), cy + r*math.sin(a150)
    g = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}"/>'
    d = f"M{x1:.1f},{90 - SW_BLUE/2:.1f} V{y1:.1f} {_arc_ccw(cx, cy, r, 30, 240)} L{x2:.1f},72"
    return svg(g + blue(d, (x2, 90), 90, 18, 26))
def ring_neutral(number):  # exit unknown: the arrow runs round the ring, no exit leg
    cx, cy, r = 44, 42, 20
    end = 200; sweep = (90 - (end - 360)) % 360
    a = math.radians(end); ex, ey = cx + r*math.cos(a), cy + r*math.sin(a)
    dx, dy = math.sin(a), -math.cos(a); L = 16
    g = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}"/>'
    d = f"M{cx},92 V{cy + r:.1f} {_arc_ccw(cx, cy, r, 90, sweep)}"
    ang = math.degrees(math.atan2(dy, dx))
    body = g + blue(d, (ex + L*dx, ey + L*dy), ang, L, 26)
    fs = 30 if number >= 10 else 34
    body += (f'<text x="94" y="93" text-anchor="end" font-family="Inter,Roboto,Arial,sans-serif" font-weight="800" '
             f'font-size="{fs}" fill="#ffffff" stroke="#1565c0" stroke-width="3" stroke-linejoin="round" paint-order="stroke">{number}</text>')
    return svg(body)


def ring_exit_far(number, exit_deg=135):  # ring up and to the right, a long exit leg down-left, like the sample
    cx, cy, r = 54, 36, 15
    sweep = (90 - exit_deg) % 360 or 360
    a = math.radians(exit_deg); ex, ey = cx + r*math.cos(a), cy + r*math.sin(a)
    L, Wd, leg = 18, 28, 17
    bx, by = ex + leg*math.cos(a), ey + leg*math.sin(a)
    g = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{GREY}" stroke-width="{SW_GREY}"/>'
    d = f"M{cx},92 V{cy + r:.1f} {_arc_ccw(cx, cy, r, 90, sweep)} L{bx:.1f},{by:.1f}"
    body = g + blue(d, (bx + L*math.cos(a), by + L*math.sin(a)), exit_deg, L, Wd)
    if number is None: return svg(body)
    fs = 25 if number >= 10 else 32
    body += (f'<text x="95" y="93" text-anchor="end" font-family="Inter,Roboto,Arial,sans-serif" font-weight="800" '
             f'font-size="{fs}" fill="#ffffff" stroke="#1565c0" stroke-width="3" stroke-linejoin="round" paint-order="stroke">{number}</text>')
    return svg(body)

def finish():  # a waving checkered flag on a pole, light cells for the windshield
    X0, X1, COLS, ROWS = 27, 93, 6, 4
    def top(x): return 11 + 5*math.sin((x - X0)/(X1 - X0)*2*math.pi)
    def hgt(x): return 46 - 6*(x - X0)/(X1 - X0)
    def pt(x, f): return (x, top(x) + f*hgt(x))
    w = (X1 - X0)/COLS; cells = ""
    for r in range(ROWS):
        for c in range(COLS):
            xa, xb = X0 + c*w, X0 + (c + 1)*w
            xs = [xa + (xb - xa)*k/4 for k in range(5)]
            upper = [pt(x, r/ROWS) for x in xs]; lower = [pt(x, (r + 1)/ROWS) for x in reversed(xs)]
            poly = " ".join(f"{x:.1f},{y:.1f}" for x, y in upper + lower)
            col = "#ffffff" if (r + c) % 2 == 0 else "#3fb0ff"
            cells += f'<polygon points="{poly}" fill="{col}"/>'
    xs = [X0 + (X1 - X0)*k/36 for k in range(37)]
    outline = " ".join(f"{x:.1f},{y:.1f}" for x, y in [pt(x, 0) for x in xs] + [pt(x, 1) for x in reversed(xs)])
    return svg(f'<ellipse cx="22" cy="89" rx="14" ry="4" fill="{GREY}"/>'
               '<path d="M22,89 V9" stroke="#e3f9ff" stroke-width="6" stroke-linecap="round"/>'
               '<circle cx="22" cy="7" r="5" fill="#ffffff"/>'
               + cells + f'<polygon points="{outline}" fill="none" stroke="#bfeaff" stroke-width="1.6" stroke-linejoin="round"/>')
def with_number(svg_s, n, left):
    fs = 25 if n >= 10 else 32
    x, anchor = (2, "start") if left else (94, "end")
    t = (f'<text x="{x}" y="93" text-anchor="{anchor}" font-family="Inter,Roboto,Arial,sans-serif" font-weight="800" '
         f'font-size="{fs}" fill="#ffffff" stroke="#1565c0" stroke-width="3" stroke-linejoin="round" paint-order="stroke">{n}</text>')
    i = svg_s.rindex("</svg>"); return svg_s[:i] + t + svg_s[i:]
EXIT = {1: 0, 2: -90, 3: 180}
icons = {
 0x1: left(), 0x2: mirror(left()), 0x3: slight_left(), 0x4: mirror(slight_left()),
 0x7: sharp_left(), 0x8: mirror(sharp_left()), 0x9: uturn_left(), 0xa: mirror(uturn_left()),
 0xb: straight(), 0xc: straight(),
 0xd: roundabout(-45), 0xe: roundabout(-135), 0xf: roundabout(180), 0x10: ring_exit_far(None),
 0x11: roundabout(0), 0x12: roundabout(45), 0x13: roundabout(-90), 0x14: mirror(roundabout(-90)),
 0x15: ring_uturn(), 0x16: mirror(ring_uturn()), 0x17: mirror(roundabout(0, stubs=False)), 0x18: roundabout(0, stubs=False),
 0x30: finish(),
}
for n in range(1, 11): icons[0x18 + n] = roundabout(EXIT[n], number=n) if n in EXIT else ring_exit_far(n)
# clockwise rings (left-hand traffic): the mirror image, the number on the left so it stays readable
for n in range(1, 11): icons[0x22 + n] = with_number(mirror(roundabout(EXIT[n]) if n in EXIT else ring_exit_far(None)), n, left=True)
for code, s in icons.items(): open(f"{OUT}/src/0x{code:x}.svg", "w").write(s)
print(len(icons), "icons")
