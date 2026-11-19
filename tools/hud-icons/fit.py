#!/usr/bin/env python3
"""fork: fit each icon's drawing to the 38x41 frame. Reads the alpha box of a 384x384 render
(viewBox 0 0 96 96) and rewrites the SVG's viewBox to that box plus a small margin, 38:41."""
import re, struct, sys, zlib
def alpha_box(png):
    d = open(png, "rb").read(); pos, idat, w = 8, b"", 0
    while pos < len(d):
        n, t = struct.unpack(">I4s", d[pos:pos+8]); body = d[pos+8:pos+8+n]; pos += 12 + n
        if t == b"IHDR": w, h, bit, ct = struct.unpack(">IIBB", body[:10]); assert bit == 8 and ct == 6, "need RGBA8"
        elif t == b"IDAT": idat += body
    raw = zlib.decompress(idat); stride = w*4; prev = bytearray(stride); x0, y0, x1, y1 = w, h, -1, -1; i = 0
    for y in range(h):
        f = raw[i]; line = bytearray(raw[i+1:i+1+stride]); i += 1 + stride
        for k in range(stride):
            a = line[k-4] if k >= 4 else 0; b = prev[k]; c = prev[k-4] if k >= 4 else 0
            if f == 1: line[k] = (line[k] + a) & 255
            elif f == 2: line[k] = (line[k] + b) & 255
            elif f == 3: line[k] = (line[k] + (a + b)//2) & 255
            elif f == 4:
                pp = a + b - c; pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                line[k] = (line[k] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(w):
            if line[x*4+3] > 8: x0, x1 = min(x0, x), max(x1, x); y0, y1 = min(y0, y), max(y1, y)
        prev = line
    return x0/w*96, y0/h*96, (x1+1)/w*96, (y1+1)/h*96
def fit(svg_path, png_path, pad=0.6, ratio=38/41):
    x0, y0, x1, y1 = alpha_box(png_path); x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
    w, h = x1 - x0, y1 - y0
    if w/h > ratio: nh = w/ratio; y0 -= (nh - h)/2; h = nh
    else: nw = h*ratio; x0 -= (nw - w)/2; w = nw
    s = open(svg_path).read()
    s = re.sub(r'width="96" height="96" viewBox="[^"]*"', f'width="{w:.2f}" height="{h:.2f}" viewBox="{x0:.2f} {y0:.2f} {w:.2f} {h:.2f}"', s, count=1)
    open(svg_path, "w").write(s)
if __name__ == "__main__": fit(sys.argv[1], sys.argv[2])
