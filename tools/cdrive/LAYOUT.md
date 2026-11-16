# C-Drive backplate: live areas

Everything here is in pixels of `cd_base` (2560 × 1208, the same as the mockup; `px()` = half a dp).
Rects are `x, y, w, h` with the top-left pixel included. Angles are degrees in the maths convention
(counter-clockwise from +x, y up), as `PrintedScale` in `ui/home/hybrid/PrintedScale.kt` takes them.
"Ink" is where the mockup drew the value: put the app's text there. "Cleared" is the area the tool repainted.
The figures come from the mockup's pixels, so where the artwork is uneven they are uneven too.

Task 2 moves the deck and the data screen to a common height. When it does, it must update the rows marked ⇕.

## Header (top right, black panel)

| Element | Rect / value | Notes |
|---|---|---|
| Panel inside | 2142, 27, 379, 71 | black glass, lum ≈ 20 |
| Printed dividers (kept) | x 2290–2291 and 2392–2393, y 51–77 | |
| Mode zone (leaf + word) | 2150, 34, 132, 60 | cleared 2156–2270 × 40–88 |
| Leaf `cd_leaf` | 2158, 44, 45, 41 | ink 2164, 50, 32, 29. Draw for ECO only |
| Mode word ink (ECO) | 2212, 53, 49, 21 | white #FFFFFF, cap height ≈ 21 |
| Gear zone | 2300, 34, 84, 60 | cleared 2320–2368 × 40–88 |
| Gear ink (P) | 2333, 47, 22, 33 | white-cyan #EBFFFF, centre x 2344 |
| READY zone | 2400, 34, 114, 60 | cleared 2404–2512 × 40–88 |
| READY ink | 2417, 53, 84, 21 | green #56FFA4, centre x 2459 |

## Spectrum matrix (printed, not erased)

| Element | Value |
|---|---|
| Window inside | 562, 16, 1543, 94 |
| Columns | 107, centre x = 569.0 + 14.41·i (i = 0…106) |
| Rows | 7, centre y = 23.5 + 13.08·j (j = 0…6) |
| LED | ≈ 12 px across. A column is 14.41 px wide: 562 + 14.41·i … +14.41 |

## Deck display ⇕

| Element | Rect / value | Notes |
|---|---|---|
| Display inside | 302, 213, 762, 194 | lum ≈ 16 |
| Note icon (kept) | 362, 247, 54, 63 | |
| Title line ink | 485, 234, 429, 37 | cyan #8CFFFF, cap ≈ 27. Cleared 470–930 × 225–280. Keep it right of x 470 and left of the display edge (1063) |
| Big time ink | 505, 289, 157, 43 | cyan #8BFFFF, seven-segment italic "03:24". Cleared 495–672 × 281–342 |
| Status ink (▶ PLAYING) | 740, 302, 139, 21 | ▶ 740–757, word 772–878, #B6FFFF. Cleared 730–890 × 293–332 |
| Thin rule (kept) | 343, 351, 682, 3 | |
| Progress bar | 344, 366, 681, 18 | cleared 338–1030 × 358–391 |
| Segments | 20, pitch 34.05: segment i at x 344 + 34.05·i, width 32, height 18 | lit #55FFFF, unlit is an outline #30817A |

Transport keys (printed, kept). The ▶ key's triangle is printed green, as in the mockup.

| Key | Rect | Glyph centre |
|---|---|---|
| ⏪ | 301, 435, 127, 59 | 362, 465 |
| ⏹ | 433, 435, 126, 59 | 495, 465 |
| ⏸ | 564, 435, 127, 59 | 627, 465 |
| ▶ | 696, 435, 125, 59 | 759, 465 |
| ⏩ | 826, 435, 126, 59 | 888, 465 |
| ⏏ | 983, 435, 86, 59 | 1025, 465 |

## Car-data screen ⇕

Screen inside: 1122, 177, 950, 326. Labels, icons, the (i) and the dividers stay printed.

| Value | Ink (mockup) | Cleared | Colour |
|---|---|---|---|
| Заряд % | 1232, 217, 73, 29 | 1226–1312 × 214–250 | #79FFFF |
| Заряд bar | 1158, 259, 192, 16 | 1152–1356 × 255–279 | lit #11FF5F |
| Бензин % | 1488, 217, 73, 29 | 1482–1568 × 214–250 | #FFFD8F |
| Бензин bar | 1416, 259, 190, 16 | 1410–1612 × 255–279 | lit #FFD951, unlit outline #5E928E |
| Запас хода "824 км" | 1679, 233, 124, 35 | 1672–1810 × 228–272 | #ACFFFF |
| EV range "62 км" | 1963, 207, 62, 22 | 1957–2026 × 203–218, 1957–2040 × 219–233 | #B7FFFF. Keep it left of the (i) at 2031–2056 × 190–214 |
| Fuel range "762 км" | 1963, 249, 77, 22 | 1957–2045 × 244–276 | #B7FFFF |
| В салоне "22.0°C" | 1243, 337, 106, 30 | 1237–1440 × 332–374 (with AUTO) | #A2FFFF |
| AUTO badge | 1364, 339, 70, 31 | (same) | pill, outline #7FD2CF |
| На улице "−3.5°C" | 1610, 337, 119, 32 | 1603–1735 × 333–373 | #A2FFFF |
| Батарея "28°C" | 1924, 337, 83, 32 | 1918–2012 × 332–373 | #A2FFFF |
| Пробег всего | 1268, 442, 184, 35 | 1262–1458 × 437–482 | #ADFFFF, unit "км" smaller |
| Пробег сегодня | 1751, 442, 160, 36 | 1745–1916 × 437–482 | #ADFFFF |

Bar segments as printed: 7 per bar, pitch 27.4, width ≈ 26, gap ≈ 1.5, height 16
(Заряд: segment i at x 1158 + 27.4·i; Бензин: at x 1416 + 27.4·i). The mockup's lit segments are uneven;
any count works inside the bar rect.

## Volume knob

| Element | Value |
|---|---|
| Knob centre | 2315, 329 |
| Knob face radius | ≈ 107 (bright rim ≈ 105–107) |
| Marker (cleared) | white bar #FFFFFF from r 52 to r 101, ≈ 8 px wide with a 2 px black outline. Cleared ring sector r 46–105 |
| Marker angle for 0…10 | 0: 226.5, 1: 202.5, 2: 176, 3: 150, 4: 121.5, 5: 89.5, 6: 58, 7: 30, 8: 4, 9: −22.5, 10: −47 (measured from the knob centre to each printed major tick; go linearly between them). The mockup's marker sat at −21.9 |
| Printed scale | major ticks at r ≈ 133 from (2316.5, 334.2), numbers just outside |

## Chart (Trip Computer & Energy Monitor)

| Element | Rect / value | Notes |
|---|---|---|
| Frame inside, chart part | 291, 612, 1085, 296 | divider to the meters at x 1376–1383 (kept) |
| Legend (kept) | 449, 636, 790, 24 | cyan swatch + text, yellow swatch + text |
| Unit titles (kept) | left 345, 680, 133, 21 (#65FFFF); right 1238, 680, 92, 21 (#FFFF0F) | |
| Cleared | 296–1373 × 704–900 | flat dark, lum ≈ 18–20 |
| Plot area (as drawn) | 367, 716, 928, 140 | y 716 = top grid line (axis max), y 855 = baseline (0) |
| Grid as drawn | horizontal y 716, 748.5, 781.5, 818.5; baseline y 855 (white #E8E6E3, 2 px); vertical every 59.2 px from x 426.5 to 1255 | grid colour ≈ #383A39–#3D423E |
| Left axis numbers | right-aligned at x 350; 30 at y 721.5, 20 at 776.5, 10 at 818, 0 at 859 (centres) | #65FFFF, digit height ≈ 18 |
| Right axis numbers | left-aligned at x 1312, same rows | #FFFF0F |
| Time labels | centres x 391, 514, 637, 760, 883, 1006, 1130, 1255 (00:00 … 21:00, pitch 123.4); text y 868–887 | white #FFFFFF, digit height ≈ 19; small ticks under the baseline at the same x |
| Lines as drawn | energy #3BFEFF, fuel #FFF70E, energy fill ≈ #182D26 | |

## Period keys

Keys are plain in the base. To light the current key, draw in this order:
1. `cd_period_lit` (alpha overlay, clear where nothing changes) at (46, keyY − 19), size 216 × 97;
2. clipped to that key's rect only: `cd_period_icons` at 80, 640 (153 × 210), tinted with the lit
   icon colour **#35ED50** (multiply/modulate: the strokes are white, their outlines black);
3. clipped the same way: `cd_period_words` at 80, 640 (153 × 210), untinted (white, ≈ #E8F2D7 in the mockup).

Clip the layers to the current key's rect. Drawn over the whole column they lay their dark outlines a second
time over the unlit keys and darken them.

| Key | Rect | Icon ink | Word ink |
|---|---|---|---|
| День | 64, 633, 180, 65 | 95, 650, 33, 30 | 158, 656, 50, 20 |
| Неделя | 64, 712, 180, 65 | 94, 729, 33, 31 | 147, 735, 72, 21 |
| Месяц | 64, 789, 180, 65 | 95, 805, 32, 31 | 153, 812, 63, 21 |

Inside the layers, icons sit in x 80–137 and words in x 138–232.

## Meters

Meter screens: RPM 1384, 612, 541, 296; compressor 1928, 612, 560, 296 (the divider at x 1925–1926 is kept).
The arrows ◀ ▶ are still printed (Task 2 removes them). Needles, hubs and the big values were cleared.

| | Обороты (0…6 ×1000) | Компрессор (0.0…3.0 кВт) |
|---|---|---|
| Pivot (hub centre) | 1683.5, 801.5 | 2183.5, 801.5 |
| Hub as drawn | dark disc r ≈ 16, bright rim on top, yellow-green centre dot r ≈ 4, shadow to r ≈ 22 | same |
| Needle as drawn | yellow #FFFFA2, ≈ 6 px wide + 2 px black outline, from r 14 to r 127, green glow on its lower side | same, to r 126 |
| Mark angles from the pivot | 0: −170.7, 1: 161.3, 2: 128.7, 3: 90.4, 4: 51.9, 5: 18.6, 6: −9.2 (use 189.3 for 0 so the list runs downwards) | 0.0: −170.9 (189.1), 0.5: 161.0, 1.0: 128.8, 1.5: 90.6, 2.0: 52.4, 2.5: 19.3, 3.0: −9.2 |
| Printed numbers (centres) | 0: 1554, 823; 1: 1564, 766; 2: 1614, 720; 3: 1684, 704; 4: 1753, 720; 5: 1806, 768; 6: 1819, 825 | 0.0: 2054, 822; 0.5: 2070, 765; 1.0: 2117, 720; 1.5: 2183, 703; 2.0: 2249, 720; 2.5: 2299, 765; 3.0: 2316, 822 |
| Scale arc | ticks centred near (1683, 829), r ≈ 150–160 | near (2183, 829), r ≈ 150–164 |
| Value ink | number 1606–1700 (#82FFFF), unit "об/мин" 1720–1797 (white), y 835–864 | number 2132–2190 (#55FFFF), unit "кВт" 2200–2245 (white), y 841–871 |
| Cleared for the value | 1600–1803 × 829–870 | 2126–2252 × 835–877 |
| Kept below | "RPM" 1665, 876, 35, 15 | — |

The needles in the mockup reach only r ≈ 127 while the printed ticks sit at r 126–164 from the pivot:
the artwork's arc is not centred on the hub. The mark angles above are what lines the needle up with the numbers.

## Bottom tab keys

All five keys are plain and all five lamps amber in the base. To light the current key, draw in this order:
1. `cd_key_lit` (alpha overlay) at (keyX − 10, 1002), size (keyW + 20) × 133;
2. clipped to that key's rect only: `cd_tab_icons` at 882, 1016 (1302 × 99), tinted with the lit icon
   colour **#D3F4A4** (pale green, multiply/modulate);
3. clipped the same way: `cd_tab_words` at 882, 1016 (1302 × 99), untinted (white, ≈ #E8F2D7 in the mockup);
4. `cd_led_green` over that key's lamp.

Clip the glyph layers to the current key's rect, as for the period keys.

| Key | Rect | Icon ink | Word ink | Lamp `cd_led_*` (101 × 27) | Lamp centre |
|---|---|---|---|---|---|
| Машина | 882, 1007, 246, 114 | 977, 1029, 56, 32 | 960, 1081, 91, 19 | 953, 978 | 1003, 991 |
| Поездки | 1140, 1007, 254, 114 | 1248, 1025, 51, 37 | 1226, 1081, 93, 23 | 1219, 978 | 1269, 991 |
| Зарядки | 1406, 1007, 255, 114 | 1521, 1026, 40, 36 | 1493, 1081, 91, 25 | 1485, 978 | 1535.5, 991 |
| Автоматизации | 1672, 1007, 260, 114 | 1781, 1024, 46, 40 | 1716, 1081, 174, 23 | 1751, 978 | 1801.5, 991 |
| Настройки | 1944, 1007, 240, 114 | 2048, 1023, 45, 42 | 2012, 1080, 117, 26 | 2018, 978 | 2068, 991 |

Inside the layers, icons sit in y 1016–1071 and words in y 1072–1114.

The lamp strip is y 978–1004 (black lines at 976–977 and 1005). The lamp itself is ≈ 47 × 11 at the centre.
`cd_led_amber` is the same lamp in amber, for an app that wants to switch one back.

## Images from the tool

| File | Size | What |
|---|---|---|
| `cd_base` | 2560 × 1208 | the backplate |
| `cd_leaf` | 45 × 41 | ECO leaf, alpha from brightness |
| `cd_key_lit` | 266 × 133 | lit bottom-key face (glow, green rim, green-tinted face), alpha overlay, icon and word removed |
| `cd_period_lit` | 216 × 97 | lit period-key face, alpha overlay, icon and word removed |
| `cd_tab_icons`, `cd_tab_words` | 1302 × 99 | the bottom keys' printed icons / words, alpha (white strokes, black outlines) |
| `cd_period_icons`, `cd_period_words` | 153 × 210 | the period keys' printed icons / words, alpha |
| `cd_led_green`, `cd_led_amber` | 101 × 27 | a lamp with its glow on the lamp strip, opaque |
