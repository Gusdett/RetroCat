#!/usr/bin/env python3
"""Generates Sprites.kt, Font.kt, preview PNGs and launcher icons for RetroCat."""
import os, sys
from PIL import Image

PROJ = "."
SP = "/tmp"
PKG_DIR = f"{PROJ}/app/src/main/java/com/retrocat/tamagotchi"
RES = f"{PROJ}/app/src/main/res"

# ------------------------------------------------------------------ helpers
def mirror(rows):
    return [r + r[::-1] for r in rows]

def pad(rows, w):
    return [r.ljust(w, '.') for r in rows]

def blank(w, h):
    return ['.' * w for _ in range(h)]

def overlay(w, h, cells):
    g = [['.'] * w for _ in range(h)]
    for (y, x), ch in cells.items():
        g[y][x] = ch
    return [''.join(r) for r in g]

def face_rows(h, w, half_rows):
    """half_rows: {row: half_string}; mirrored; padded to w."""
    rows = []
    half_w = len(next(iter(half_rows.values())))
    for y in range(h):
        hr = half_rows.get(y, '.' * half_w)
        rows.append(hr + hr[::-1])
    return pad(rows, w)

def merge(*layers):
    h = len(layers[0]); w = len(layers[0][0])
    g = [['.'] * w for _ in range(h)]
    for L in layers:
        for y in range(h):
            for x in range(w):
                if L[y][x] != '.':
                    g[y][x] = L[y][x]
    return [''.join(r) for r in g]

def shape(mask, w, h, fill='+', outline='#'):
    rows = []
    for y in range(h):
        r = ''
        for x in range(w):
            if (x, y) in mask:
                edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                r += outline if edge else fill
            else:
                r += '.'
        rows.append(r)
    return rows

def ellipse(cx, cy, rx, ry, w, h):
    return {(x, y) for y in range(h) for x in range(w)
            if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1.0}

def setc(rows, x, y, ch):
    r = list(rows[y]); r[x] = ch; rows[y] = ''.join(r)

def embed(rows, w, h, ox, oy):
    g = [['.'] * w for _ in range(h)]
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            g[oy + y][ox + x] = ch
    return [''.join(r) for r in g]

SPRITES = {}   # name -> rows

# ------------------------------------------------------------------ ADULT CAT (24 x 20)
CAT_HALF = [
    "..##......",
    "..#+#.....",
    "..#++#####",
    ".#+++++o+o",
    "#+++++++++",
    "#+++++++++",
    "#+++++++++",
    "#+++++++++",
    "#+++++++++",
    ".#++++++++",
    "..########",
    "...#++++++",
    "..#+++++++",
    "..#++++***",
    "..#+++****",
    "..#+++***#",
    "..#+++***#",
    "..#++****#",
    "..#+++**#*",
    "..########",
]
CAT_W, CAT_H = 24, 20
SPRITES["CAT_BASE"] = pad(mirror(CAT_HALF), CAT_W)

def tail(w, h, rows_at):
    g = [['.'] * w for _ in range(h)]
    for y, s in rows_at.items():
        for i, ch in enumerate(s):
            g[y][w - len(s) + i] = ch
    return [''.join(r) for r in g]

SPRITES["CAT_TAIL_A"] = tail(CAT_W, CAT_H, {
    9: ".##.", 10: "#++#", 11: "#++#", 12: "#++#", 13: "#++#", 14: "#++#", 15: "#++#", 16: "#+#.", 17: "###.",
})
SPRITES["CAT_TAIL_B"] = tail(CAT_W, CAT_H, {
    12: "..##", 13: ".#++#"[:4], 14: "#++#", 15: "#++#", 16: "#+#.", 17: "###.",
})
# shift tail B a bit so it differs visibly (leaning outward)
SPRITES["CAT_TAIL_B"] = tail(CAT_W, CAT_H, {
    11: "..##", 12: ".#+#", 13: "#++#", 14: "#++#", 15: "#+#.", 16: "#+#.", 17: "###.",
})

EYES_OPEN = {4: ".....##...", 5: ".....#*...", 6: ".....##..."}
NOSE = {7: ".........#"}
WHISK = {7: ".ooo......", 8: "..oo......"}
MOUTH_N = {8: "........#."}   # nose-mouth
def comb(*dicts):
    out = {}
    for d in dicts:
        for k, v in d.items():
            if k in out:
                out[k] = ''.join(a if a != '.' else b for a, b in zip(out[k], v))
            else:
                out[k] = v
    return out

CAT_FACES = {
    "NEUTRAL": comb(EYES_OPEN, NOSE, WHISK, {8: ".......#.#"[:10]}),
    "BLINK":   comb({6: "....###..."}, NOSE, WHISK, {8: ".......#.#"[:10]}),
    "HAPPY":   comb({5: ".....#....", 6: "....#.#..."}, NOSE, WHISK, {8: ".......###"}),
    "SAD":     comb({5: ".....##...", 6: ".....#*...", 7: ".....*...."}, {7: ".........#"}, {8: "........#."}, {9: ".......#.."}),
    "SLEEP":   comb({6: "....###..."}, NOSE, WHISK, {8: "........#."}),
    "EAT":     comb({5: ".....#....", 6: "....#.#..."}, NOSE, WHISK, {8: ".......###", 9: ".......#oo"}),
    "SICK":    comb({4: "....#.#...", 5: ".....#....", 6: "....#.#..."}, NOSE, {8: ".......#.#"[:10]}),
}
FACE_ORDER = ["NEUTRAL", "BLINK", "HAPPY", "SAD", "SLEEP", "EAT", "SICK"]
for n in FACE_ORDER:
    SPRITES[f"CAT_FACE_{n}"] = face_rows(CAT_H, CAT_W, CAT_FACES[n])

# ------------------------------------------------------------------ KITTEN (19 x 14)
KIT_HALF = [
    ".##.....",
    ".#+#....",
    ".#++####",
    "#+++++o+",
    "#+++++++",
    "#+++++++",
    "#+++++++",
    "#+++++++",
    ".#++++++",
    "..######",
    "..#+++**",
    "..#++***",
    "..#++**#",
    "..######",
]
KIT_W, KIT_H = 19, 14
SPRITES["KITTEN_BASE"] = pad(mirror(KIT_HALF), KIT_W)
SPRITES["KITTEN_TAIL_A"] = tail(KIT_W, KIT_H, {7: ".##", 8: "#+#", 9: "#+#", 10: "#+#", 11: "#+#", 12: "##."})
SPRITES["KITTEN_TAIL_B"] = tail(KIT_W, KIT_H, {9: "..#", 10: ".#+"[:3], 11: "#+#", 12: "##."})
SPRITES["KITTEN_TAIL_B"] = tail(KIT_W, KIT_H, {8: "..##"[:3], 9: ".#+"[:3], 10: "#+#", 11: "#+#", 12: "##."})

K_EYES = {4: "...##...", 5: "...#*...", 6: "...##..."}
K_NOSE = {7: ".......#"}
KIT_FACES = {
    "NEUTRAL": comb(K_EYES, K_NOSE),
    "BLINK":   comb({5: "..###...", 6: "........"}, K_NOSE),
    "HAPPY":   comb({4: "...#....", 5: "..#.#..."}, K_NOSE, {8: "......##"}),
    "SAD":     comb({5: "...##...", 6: "...#*...", 7: "...*...."}, {7: ".......#"}, {8: "......#."}),
    "SLEEP":   comb({5: "..###..."}, K_NOSE),
    "EAT":     comb({4: "...#....", 5: "..#.#..."}, K_NOSE, {8: "......##"}),
    "SICK":    comb({4: "..#.#...", 5: "...#....", 6: "..#.#..."}, K_NOSE),
}
for n in FACE_ORDER:
    SPRITES[f"KITTEN_FACE_{n}"] = face_rows(KIT_H, KIT_W, KIT_FACES[n])

# ------------------------------------------------------------------ EGG (16 x 16)
EGG_HALF = [
    ".....###",
    "...##+++",
    "..#+++++",
    "..#++o++",
    ".#++++++",
    ".#+o++oo",
    ".#++++++",
    "#+++++o+",
    "#++o++++",
    "#+++++++",
    "#+++oo++",
    "#+++++++",
    ".#++++++",
    ".#++++++",
    "..#+++++",
    "...#####",
]
SPRITES["EGG"] = mirror(EGG_HALF)
crack1 = {(8, 3): '#', (7, 4): '#', (8, 5): '#', (7, 6): '#', (8, 7): '#', (7, 8): '#', (8, 9): '#', (7, 10): '#', (8, 11): '#'}
crack2 = dict(crack1)
crack2.update({(6, 5): '#', (9, 6): '#', (9, 8): '#', (10, 9): '#', (10, 11): '#', (9, 12): '#', (6, 7): '#'})
# (row, col) keys; rows are y
def crack_overlay(c):
    cells = {}
    for (y, x), ch in c.items():
        cells[(y, x)] = ch
    return overlay(16, 16, cells)
SPRITES["EGG_CRACK1"] = crack_overlay(crack1)
SPRITES["EGG_CRACK2"] = crack_overlay(crack2)

# ------------------------------------------------------------------ GHOST (16 x 16)
GHOST_HALF = [
    ".....###",
    "...##***",
    "..#*****",
    ".#******",
    ".#******",
    ".#**##**",
    ".#**##**",
    ".#******",
    ".#*****#",
    ".#*****#",
    ".#******",
    ".#******",
    ".#******",
    ".#******",
    ".#*#**#*",
    "..#.##.#",
]
SPRITES["GHOST"] = mirror(GHOST_HALF)

# ------------------------------------------------------------------ small objects
SPRITES["POOP"] = [
    "....#....",
    "...#o#...",
    "..#ooo#..",
    "..#o+o#..",
    ".#ooooo#.",
    ".#o+ooo#.",
    "#ooooooo#",
    ".#######.",
]
SPRITES["HEART_FULL"] = [
    ".##.##.",
    "#######",
    "#######",
    ".#####.",
    "..###..",
    "...#...",
]
SPRITES["HEART_EMPTY"] = [
    ".##.##.",
    "#..#..#",
    "#.....#",
    ".#...#.",
    "..#.#..",
    "...#...",
]
SPRITES["SPK_ON"] = [
    "...#....#.",
    "..##.#...#",
    "####..#..#",
    "####..#..#",
    "..##.#...#",
    "...#....#.",
]
SPRITES["SPK_OFF"] = [
    "...#......",
    "..##..#..#",
    "####...##.",
    "####...##.",
    "..##..#..#",
    "...#......",
]
SPRITES["BED"] = [
    "..####################..",
    ".#++++++++++++++++++++#.",
    "#+oooooooooooooooooooo+#",
    "#+oooooooooooooooooooo+#",
    ".#++++++++++++++++++++#.",
    "..####################..",
]

# yarn ball 8x8
yarn_mask = ellipse(4, 4, 3.9, 3.9, 8, 8)
yarn = shape(yarn_mask, 8, 8, fill='+')
for y in range(8):
    for x in range(8):
        if yarn[y][x] == '+' and (x + y) % 3 == 0:
            setc(yarn, x, y, 'o')
SPRITES["YARN"] = yarn

# fish 13x7
fish_mask = ellipse(4.5, 3.5, 4.6, 3.3, 13, 7)
for x in range(8, 13):
    half = (x - 7) * 0.8
    for y in range(7):
        if abs(y + .5 - 3.5) <= half:
            fish_mask.add((x, y))
fish = shape(fish_mask, 13, 7, fill='+')
setc(fish, 2, 2, '#')
for y in range(2, 5):
    if fish[y][5] == '+':
        setc(fish, 5, y, 'o')
SPRITES["FISH"] = fish

# 11x11 icons
SPRITES["ICON_FISH"] = embed(SPRITES["FISH"], 11, 11, 0, 2)[:11] if False else None
fish_small_mask = ellipse(3.5, 3.5, 3.6, 3.0, 11, 7)
for x in range(7, 11):
    half = (x - 6) * 0.9
    for y in range(7):
        if abs(y + .5 - 3.5) <= half:
            fish_small_mask.add((x, y))
fs = shape(fish_small_mask, 11, 7, fill='*')
setc(fs, 2, 2, '#')
SPRITES["ICON_FISH"] = embed(fs, 11, 11, 0, 2)

yarn_i = shape(ellipse(5, 5.5, 4.4, 4.4, 11, 11), 11, 11, fill='*')
for y in range(11):
    for x in range(11):
        if yarn_i[y][x] == '*' and (x * 2 + y) % 4 == 0:
            setc(yarn_i, x, y, '#')
SPRITES["ICON_YARN"] = yarn_i

moon_mask = ellipse(5.5, 5.5, 5, 5, 11, 11) - ellipse(8.2, 4.2, 4.2, 4.2, 11, 11)
SPRITES["ICON_MOON"] = shape(moon_mask, 11, 11, fill='*')

b1 = ellipse(4.5, 6.5, 3.8, 3.8, 11, 11)
b2 = ellipse(8.6, 2.6, 2.4, 2.4, 11, 11) - b1
soap = shape(b1 | b2, 11, 11, fill='*')
setc(soap, 3, 5, '#'); setc(soap, 8, 2, '#')
SPRITES["ICON_SOAP"] = soap

plus = {(x, y) for x in range(11) for y in range(11) if (3 <= x <= 7) or (3 <= y <= 7)} - \
       {(x, y) for x in range(11) for y in range(11) if not ((3 <= x <= 7 and True) or (3 <= y <= 7 and True))}
plus = {(x, y) for x in range(11) for y in range(11)
        if (4 <= x <= 6) or (4 <= y <= 6)}
SPRITES["ICON_CROSS"] = shape(plus, 11, 11, fill='*')

# ------------------------------------------------------------------ validation
for name, rows in SPRITES.items():
    assert rows, name
    w = len(rows[0])
    for i, r in enumerate(rows):
        assert len(r) == w, f"{name} row {i} width {len(r)} != {w}"
        assert set(r) <= set(".#+o*"), f"{name} row {i} bad chars {set(r)}"
assert len({len(r) for r in SPRITES["CAT_BASE"]}) == 1
for n in FACE_ORDER:
    assert len(SPRITES[f"CAT_FACE_{n}"]) == CAT_H and len(SPRITES[f"CAT_FACE_{n}"][0]) == CAT_W
    assert len(SPRITES[f"KITTEN_FACE_{n}"]) == KIT_H and len(SPRITES[f"KITTEN_FACE_{n}"][0]) == KIT_W
for t in ("CAT_TAIL_A", "CAT_TAIL_B"):
    assert len(SPRITES[t]) == CAT_H and len(SPRITES[t][0]) == CAT_W
for t in ("KITTEN_TAIL_A", "KITTEN_TAIL_B"):
    assert len(SPRITES[t]) == KIT_H and len(SPRITES[t][0]) == KIT_W
print("sprites OK:", len(SPRITES))

# ------------------------------------------------------------------ Kotlin: Sprites.kt
def kt_sprite(name, rows):
    body = ",\n".join(f'        "{r}"' for r in rows)
    return f"    val {name} = Sprite.of(\n{body}\n    )\n"

out = ["package com.retrocat.tamagotchi", "",
       "// GENERATED pixel art. '.' = transparent, '*' = palette 0 (lightest), '+' = 1, 'o' = 2, '#' = 3 (darkest).",
       "object Art {"]
order = [k for k in SPRITES.keys()]
for k in order:
    out.append(kt_sprite(k, SPRITES[k]))
faces_k = ", ".join(f"KITTEN_FACE_{n}" for n in FACE_ORDER)
faces_c = ", ".join(f"CAT_FACE_{n}" for n in FACE_ORDER)
out.append(f"    val kitten = CatSet(KITTEN_BASE, KITTEN_TAIL_A, KITTEN_TAIL_B, arrayOf({faces_k}))")
out.append(f"    val cat = CatSet(CAT_BASE, CAT_TAIL_A, CAT_TAIL_B, arrayOf({faces_c}))")
out.append("}")
os.makedirs(PKG_DIR, exist_ok=True)
open(f"{PKG_DIR}/Sprites.kt", "w").write("\n".join(out) + "\n")

# ------------------------------------------------------------------ Font (3x5)
FONT = {
 'A': ".X.|X.X|XXX|X.X|X.X", 'B': "XX.|X.X|XX.|X.X|XX.", 'C': ".XX|X..|X..|X..|.XX",
 'D': "XX.|X.X|X.X|X.X|XX.", 'E': "XXX|X..|XX.|X..|XXX", 'F': "XXX|X..|XX.|X..|X..",
 'G': ".XX|X..|X.X|X.X|.XX", 'H': "X.X|X.X|XXX|X.X|X.X", 'I': "XXX|.X.|.X.|.X.|XXX",
 'J': "..X|..X|..X|X.X|.X.", 'K': "X.X|X.X|XX.|X.X|X.X", 'L': "X..|X..|X..|X..|XXX",
 'M': "X.X|XXX|XXX|X.X|X.X", 'N': "XX.|X.X|X.X|X.X|X.X", 'O': ".X.|X.X|X.X|X.X|.X.",
 'P': "XX.|X.X|XX.|X..|X..", 'Q': ".X.|X.X|X.X|XX.|.XX", 'R': "XX.|X.X|XX.|X.X|X.X",
 'S': ".XX|X..|.X.|..X|XX.", 'T': "XXX|.X.|.X.|.X.|.X.", 'U': "X.X|X.X|X.X|X.X|XXX",
 'V': "X.X|X.X|X.X|X.X|.X.", 'W': "X.X|X.X|XXX|XXX|X.X", 'X': "X.X|X.X|.X.|X.X|X.X",
 'Y': "X.X|X.X|.X.|.X.|.X.", 'Z': "XXX|..X|.X.|X..|XXX",
 '0': "XXX|X.X|X.X|X.X|XXX", '1': ".X.|XX.|.X.|.X.|XXX", '2': "XX.|..X|.X.|X..|XXX",
 '3': "XX.|..X|.X.|..X|XX.", '4': "X.X|X.X|XXX|..X|..X", '5': "XXX|X..|XX.|..X|XX.",
 '6': ".XX|X..|XXX|X.X|XXX", '7': "XXX|..X|.X.|.X.|.X.", '8': "XXX|X.X|XXX|X.X|XXX",
 '9': "XXX|X.X|XXX|..X|XX.",
 '.': "...|...|...|...|.X.", '!': ".X.|.X.|.X.|...|.X.", '?': "XX.|..X|.X.|...|.X.",
 ':': "...|.X.|...|.X.|...", '-': "...|...|XXX|...|...", '+': "...|.X.|XXX|.X.|...",
 '/': "..X|..X|.X.|X..|X..", '%': "X.X|..X|.X.|X..|X.X", ',': "...|...|...|.X.|X..",
 "'": ".X.|.X.|...|...|...", ' ': "...|...|...|...|...", '<': "..X|.X.|X..|.X.|..X",
 '>': "X..|.X.|..X|.X.|X..",
}
lines = ["package com.retrocat.tamagotchi", "", "// GENERATED 3x5 pixel font: each glyph is 5 rows of 3-bit masks (MSB = left pixel).",
         "object Font {", "    val glyphs: Map<Char, IntArray> = mapOf("]
for ch, spec in FONT.items():
    rows = spec.split('|')
    assert len(rows) == 5 and all(len(r) == 3 for r in rows), ch
    ints = [sum((1 << (2 - i)) for i, c in enumerate(r) if c == 'X') for r in rows]
    lit = "'\\''" if ch == "'" else f"'{ch}'"
    lines.append(f"        {lit} to intArrayOf({', '.join(map(str, ints))}),")
lines.append("    )")
lines.append("}")
open(f"{PKG_DIR}/Font.kt", "w").write("\n".join(lines) + "\n")
print("font OK:", len(FONT))

# ------------------------------------------------------------------ preview + icons
PAL = [(0xFF, 0xFF, 0xFF), (0xF2, 0xA6, 0x5A), (0xC9, 0x6F, 0x2B), (0x2B, 0x1B, 0x17)]
BG = (0xF7, 0xF5, 0xF0)
CH = {'*': 0, '+': 1, 'o': 2, '#': 3}

def render(layers, scale, bg=BG, pal=PAL):
    w = len(layers[0][0]); h = len(layers[0])
    img = Image.new("RGB", (w * scale, h * scale), bg)
    px = img.load()
    for L in layers:
        for y, r in enumerate(L):
            for x, ch in enumerate(r):
                if ch in CH:
                    for dy in range(scale):
                        for dx in range(scale):
                            px[x * scale + dx, y * scale + dy] = pal[CH[ch]]
    return img

def cat_layers(prefix, face, tail="A"):
    return [SPRITES[f"{prefix}_BASE"], SPRITES[f"{prefix}_TAIL_{tail}"], SPRITES[f"{prefix}_FACE_{face}"]]

S = 8
sheet = Image.new("RGB", (1500, 720), BG)
x = 10
y = 10
for n in FACE_ORDER:
    im = render(cat_layers("CAT", n), S)
    sheet.paste(im, (x, y)); x += im.width + 10
x = 10; y = 10 + CAT_H * S + 14
for n in FACE_ORDER:
    im = render(cat_layers("KITTEN", n, "B"), S)
    sheet.paste(im, (x, y)); x += im.width + 10
x = 10; y += KIT_H * S + 14
for key in ["EGG", "GHOST", "POOP", "YARN", "FISH", "HEART_FULL", "HEART_EMPTY", "SPK_ON", "SPK_OFF", "BED"]:
    im = render([SPRITES[key]], S); sheet.paste(im, (x, y)); x += im.width + 10
im = render([SPRITES["EGG"], SPRITES["EGG_CRACK2"]], S); sheet.paste(im, (x, y)); x += im.width + 10
x = 10; y += 18 * S + 10
for key in ["ICON_FISH", "ICON_YARN", "ICON_MOON", "ICON_SOAP", "ICON_CROSS"]:
    im = render([SPRITES[key]], S); sheet.paste(im, (x, y)); x += im.width + 10
# font preview
fy = y
fx = x + 20
text = "FEED PLAY SLEEP CLEAN 0123456789 !?"
for ch in text:
    spec = FONT.get(ch)
    if spec:
        for ry, r in enumerate(spec.split('|')):
            for rx, c in enumerate(r):
                if c == 'X':
                    for dy in range(4):
                        for dx in range(4):
                            sheet.putpixel((fx + rx * 4 + dx, fy + ry * 4 + dy), PAL[3])
    fx += 16
sheet.save(f"{SP}/preview.png")

# launcher icons
def icon_img(size, safe):
    layers = cat_layers("CAT", "HAPPY")
    cw, ch_ = len(layers[0][0]), len(layers[0])
    bg = BG
    img = Image.new("RGBA", (size, size), bg + (255,))
    s = max(1, int(size * safe) // max(cw, ch_))
    cat_img = render(layers, s, bg=bg).convert("RGBA")
    # make bg transparent where layer empty
    cat_img_t = Image.new("RGBA", cat_img.size, (0, 0, 0, 0))
    px = cat_img.load(); pt = cat_img_t.load()
    for yy in range(cat_img.height):
        for xx in range(cat_img.width):
            if px[xx, yy][:3] != bg:
                pt[xx, yy] = px[xx, yy]
    img.alpha_composite(cat_img_t, ((size - cat_img.width) // 2, (size - cat_img.height) // 2))
    return img

for dens, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = f"{RES}/mipmap-{dens}"
    os.makedirs(d, exist_ok=True)
    icon_img(px, 0.78).convert("RGB").save(f"{d}/ic_launcher.png")

# adaptive foreground (transparent), 432x432, cat inside the 66% safe zone
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
layers = cat_layers("CAT", "HAPPY")
cw, ch_ = len(layers[0][0]), len(layers[0])
s = (432 * 60 // 100) // max(cw, ch_)
ci = render(layers, s, bg=(1, 2, 3)).convert("RGBA")
pci = ci.load()
for yy in range(ci.height):
    for xx in range(ci.width):
        if pci[xx, yy][:3] == (1, 2, 3):
            pci[xx, yy] = (0, 0, 0, 0)
fg.alpha_composite(ci, ((432 - ci.width) // 2, (432 - ci.height) // 2))
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)
fg.save(f"{RES}/drawable-nodpi/ic_launcher_foreground.png")
print("icons OK")
