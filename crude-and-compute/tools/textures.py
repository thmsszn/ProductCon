"""Procedural pixel-art textures for Crude & Compute (deterministic, seeded)."""

import math
import random

from PIL import Image, ImageDraw


def _rng(name):
    return random.Random(sum(ord(c) * (i + 7) for i, c in enumerate(name)))


def clamp(v):
    return max(0, min(255, int(v)))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def noise_fill(img, base, amount, rng, alpha=255):
    w, h = img.size
    for y in range(h):
        for x in range(w):
            f = 1 + rng.uniform(-amount, amount)
            img.putpixel((x, y), shade(base, f) + (alpha,))


def new(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


# ---------------------------------------------------------------------------- metals & paint
def metal(name, base, amount=0.08, streaks=True, rivets=False, size=16):
    rng = _rng(name)
    img = new(size)
    noise_fill(img, base, amount, rng)
    if streaks:  # brushed metal: horizontal streaks
        for _ in range(size // 2):
            y = rng.randrange(size)
            x0 = rng.randrange(size)
            ln = rng.randrange(3, size // 2)
            f = rng.choice((0.9, 1.1))
            for x in range(x0, min(size, x0 + ln)):
                img.putpixel((x, y), shade(img.getpixel((x, y)), f))
    # bevel highlight / shadow at the edges
    for i in range(size):
        img.putpixel((i, 0), shade(img.getpixel((i, 0)), 1.18))
        img.putpixel((0, i), shade(img.getpixel((0, i)), 1.12))
        img.putpixel((i, size - 1), shade(img.getpixel((i, size - 1)), 0.78))
        img.putpixel((size - 1, i), shade(img.getpixel((size - 1, i)), 0.85))
    if rivets:
        for (x, y) in ((2, 2), (size - 3, 2), (2, size - 3), (size - 3, size - 3)):
            img.putpixel((x, y), shade(base, 1.35) + (255,))
            img.putpixel((x + 1, y + 1), shade(base, 0.6) + (255,))
    return img


def paint(name, base, wear=0.06):
    rng = _rng(name)
    img = metal(name, base, amount=0.05, streaks=False)
    steel = (150, 150, 155)
    # chipped paint showing steel + darker grime at the bottom
    for _ in range(int(16 * 16 * wear)):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), shade(steel, rng.uniform(0.8, 1.1)) + (255,))
    for y in range(12, 16):
        for x in range(16):
            if rng.random() < (y - 11) * 0.12:
                img.putpixel((x, y), shade(img.getpixel((x, y)), 0.82))
    return img


def rust_steel():
    rng = _rng("rust_steel")
    img = metal("rust_steel", (92, 80, 74), amount=0.1, streaks=False)
    for _ in range(40):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), shade((150, 82, 40), rng.uniform(0.7, 1.1)) + (255,))
    return img


def hazard():
    img = new()
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 4) % 2 == 0
            c = (232, 182, 28) if stripe else (34, 32, 30)
            img.putpixel((x, y), shade(c, 1 + _rng("h" + str(x * 31 + y)).uniform(-0.06, 0.06)) + (255,))
    return img


def grating():
    img = new()
    bar = (120, 124, 130)
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                f = 1.15 if (x % 4 == 0 and y % 4 == 0) else (1.0 if y % 4 == 0 else 0.85)
                img.putpixel((x, y), shade(bar, f) + (255,))
    return img


def concrete():
    rng = _rng("concrete")
    img = new()
    noise_fill(img, (158, 156, 150), 0.07, rng)
    for _ in range(18):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), shade((158, 156, 150), rng.choice((0.75, 1.15))) + (255,))
    return img


def column_white():
    rng = _rng("column")
    img = new()
    noise_fill(img, (214, 216, 214), 0.04, rng)
    for x in range(16):
        img.putpixel((x, 7), (168, 170, 170, 255))
        img.putpixel((x, 8), (232, 234, 232, 255))
        img.putpixel((x, 15), (176, 178, 178, 255))
    for y in range(16):
        if y % 8 in (2, 5):
            img.putpixel((3, y), (150, 152, 152, 255))
            img.putpixel((11, y), (150, 152, 152, 255))
    return img


def plastic(name, base):
    rng = _rng(name)
    img = new()
    noise_fill(img, base, 0.025, rng)
    for i in range(16):
        img.putpixel((i, 0), shade(base, 1.08) + (255,))
        img.putpixel((i, 15), shade(base, 0.86) + (255,))
    return img


def rubber():
    rng = _rng("rubber")
    img = new()
    noise_fill(img, (36, 36, 38), 0.12, rng)
    return img


def wood():
    rng = _rng("wood")
    img = new()
    base = (150, 104, 60)
    for y in range(16):
        for x in range(16):
            grain = math.sin((x * 0.9 + math.sin(y * 0.6) * 1.5)) * 0.08
            img.putpixel((x, y), shade(base, 1 + grain + rng.uniform(-0.05, 0.05)) + (255,))
    for x in (5, 11):
        for y in range(16):
            img.putpixel((x, y), shade(base, 0.72) + (255,))
    return img


def glass_dark():
    img = new()
    for y in range(16):
        for x in range(16):
            t = y / 15
            img.putpixel((x, y), mix((52, 70, 82), (24, 30, 38), t) + (255,))
    for i in range(5):
        img.putpixel((3 + i, 2 + i), (150, 180, 196, 255))
        img.putpixel((4 + i, 2 + i), (110, 136, 150, 255))
    return img


def gauge():
    img = new()
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, 15, 15), fill=(70, 70, 74, 255))
    d.ellipse((1, 1, 14, 14), fill=(236, 232, 220, 255))
    for a in range(-135, 136, 45):
        r = math.radians(a - 90)
        x, y = 7.5 + 5.5 * math.cos(r), 7.5 + 5.5 * math.sin(r)
        img.putpixel((int(round(x)), int(round(y))), (40, 40, 40, 255))
    d.arc((3, 3, 12, 12), 20, 70, fill=(200, 40, 30, 255))
    d.line((7, 8, 11, 4), fill=(180, 20, 20, 255))
    img.putpixel((7, 8), (30, 30, 30, 255))
    return img


def solid(rgb, glow=False):
    img = new()
    for y in range(16):
        for x in range(16):
            f = 1.0
            if glow:
                dist = math.hypot(x - 7.5, y - 7.5) / 10.6
                f = 1.25 - 0.35 * dist
            img.putpixel((x, y), shade(rgb, f) + (255,))
    return img


def vent():
    img = metal("vent", (88, 92, 98), streaks=False)
    for y in range(2, 15, 3):
        for x in range(2, 14):
            img.putpixel((x, y), (28, 30, 34, 255))
            img.putpixel((x, y + 1), (126, 130, 136, 255))
    return img


def fan_grille():
    img = new()
    c = (60, 62, 66)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            on_ring = abs(r - 7.0) < 0.6 or abs(r - 4.6) < 0.5 or abs(r - 2.2) < 0.5
            on_spoke = (abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6) and r < 7.4
            if on_ring or on_spoke:
                img.putpixel((x, y), shade(c, 1.2 if y < 8 else 0.9) + (255,))
    return img


def fan_blade():
    return plastic("fan_blade", (54, 58, 64))


# ---------------------------------------------------------------------------- datacenter
def server_side():
    img = metal("server_side", (46, 48, 54), amount=0.04, streaks=False)
    for y in range(1, 16, 2):
        for x in range(1 + (y // 2) % 2, 16, 2):
            img.putpixel((x, y), (18, 19, 22, 255))
    return img


def server_back():
    img = metal("server_back", (40, 42, 48), amount=0.04, streaks=False)
    d = ImageDraw.Draw(img)
    for cx in (4, 11):
        d.ellipse((cx - 3, 2, cx + 3, 8), fill=(24, 24, 28, 255), outline=(80, 84, 92, 255))
        d.line((cx - 2, 5, cx + 2, 5), fill=(70, 72, 80, 255))
        d.line((cx, 3, cx, 7), fill=(70, 72, 80, 255))
    colors = [(60, 120, 220), (230, 200, 40), (220, 60, 60), (60, 190, 90)]
    for i, col in enumerate(colors):
        d.line((2 + i * 3, 10, 2 + i * 3, 15), fill=col + (255,))
    return img


def server_bay():
    img = metal("server_bay", (26, 27, 31), amount=0.05, streaks=False)
    for x in range(16):
        img.putpixel((x, 0), (90, 94, 100, 255))
        img.putpixel((x, 15), (14, 14, 16, 255))
    for x in (1, 14):
        for y in range(2, 14):
            img.putpixel((x, y), (60, 62, 68, 255))
    return img


def gpu_front():
    """32x32 front face of an AI accelerator blade: heatsink fins, brand stripe, ports."""
    rng = _rng("gpu_front")
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 31, 31), fill=(30, 31, 36, 255))
    for x in range(2, 30, 2):  # fins
        d.line((x, 3, x, 22), fill=(122, 126, 134, 255))
        d.line((x + 1, 3, x + 1, 22), fill=(64, 66, 72, 255))
    d.rectangle((0, 24, 31, 27), fill=(118, 185, 0, 255))  # accent stripe
    d.rectangle((0, 24, 31, 24), fill=(160, 220, 40, 255))
    for i in range(4):
        d.rectangle((3 + i * 7, 28, 7 + i * 7, 30), fill=(12, 12, 14, 255), outline=(90, 90, 96, 255))
    for x in range(32):
        img.putpixel((x, 0), (150, 154, 160, 255))
        img.putpixel((x, 31), (10, 10, 12, 255))
    for _ in range(10):
        x, y = rng.randrange(2, 30), rng.randrange(3, 22)
        img.putpixel((x, y), shade(img.getpixel((x, y)), 1.15))
    return img


def gpu_side():
    rng = _rng("gpu_side")
    img = new()
    noise_fill(img, (28, 92, 52), 0.06, rng)
    d = ImageDraw.Draw(img)
    for _ in range(6):
        x, y = rng.randrange(1, 13), rng.randrange(1, 13)
        d.rectangle((x, y, x + 2, y + 1), fill=(20, 20, 22, 255))
    for y in (4, 9, 13):
        d.line((0, y, 15, y), fill=(196, 160, 60, 255))
    return img


def pcb():
    return gpu_side()


def screen(size=32):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    for y in range(size):
        for x in range(size):
            t = y / (size - 1)
            c = mix((14, 34, 58), (6, 12, 24), t)
            if y % 2 == 0:
                c = shade(c, 1.12)
            img.putpixel((x, y), c + (255,))
    return img


def keyboard():
    img = plastic("keyboard", (40, 42, 46))
    for y in range(2, 15, 3):
        for x in range(1, 15, 2):
            img.putpixel((x, y), (196, 200, 206, 255))
            img.putpixel((x, y + 1), (120, 124, 130, 255))
    return img


# ---------------------------------------------------------------------------- terrain
def oil_shale():
    rng = _rng("oil_shale")
    img = new()
    base = (92, 88, 84)
    for y in range(16):
        for x in range(16):
            layer = 0.06 * math.sin(y * 1.3 + x * 0.25)
            img.putpixel((x, y), shade(base, 1 + layer + rng.uniform(-0.09, 0.09)) + (255,))
    blobs = [(4, 4, 2.6), (11, 6, 2.1), (6, 11, 2.3), (12, 12, 1.6), (1, 9, 1.2)]
    for cx, cy, r in blobs:
        for y in range(16):
            for x in range(16):
                dd = math.hypot(x - cx, y - cy)
                if dd < r:
                    c = (16, 13, 12) if dd < r - 0.6 else (40, 34, 30)
                    img.putpixel((x, y), c + (255,))
        hx, hy = int(cx - r * 0.4), int(cy - r * 0.4)
        if 0 <= hx < 16 and 0 <= hy < 16:
            img.putpixel((hx, hy), (120, 116, 130, 255))  # glossy highlight
    return img


# ---------------------------------------------------------------------------- items
def _outline(img, color=(20, 20, 24, 255)):
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 0 and src.getpixel((nx, ny))[:3] != color[:3]:
                        img.putpixel((x, y), color)
                        break
    return img


def item_bucket(liquid, highlight):
    img = new()
    iron, iron_d, iron_l = (176, 176, 182), (110, 110, 118), (226, 226, 232)
    d = ImageDraw.Draw(img)
    # bucket body (trapezoid)
    for y in range(6, 15):
        inset = (y - 6) // 3
        for x in range(3 + inset, 13 - inset):
            c = iron if x < 9 - inset else iron_d
            if x == 3 + inset:
                c = iron_l
            img.putpixel((x, y), c + (255,))
    d.line((2, 5, 13, 5), fill=iron_l + (255,))
    d.line((2, 6, 13, 6), fill=iron + (255,))
    # liquid surface
    d.line((3, 5, 12, 5), fill=liquid + (255,))
    d.line((4, 6, 11, 6), fill=shade(liquid, 0.8) + (255,))
    img.putpixel((5, 5), highlight + (255,))
    img.putpixel((6, 5), highlight + (255,))
    # handle
    d.arc((3, 0, 12, 10), 200, 340, fill=iron_d + (255,))
    return _outline(img)


def item_wrench():
    img = new()
    steel, steel_d, steel_l = (170, 172, 180), (100, 102, 110), (220, 222, 230)
    brass = (214, 168, 64)
    for i in range(10):  # handle diagonal from bottom-left
        x, y = 2 + i, 13 - i
        img.putpixel((x, y), brass + (255,))
        img.putpixel((x + 1, y), shade(brass, 0.75) + (255,))
    for (x, y) in ((11, 1), (12, 1), (13, 2), (14, 3), (14, 4), (13, 5), (10, 2), (11, 4), (12, 5), (10, 3), (9, 4)):
        img.putpixel((x, y), steel + (255,))
    for (x, y) in ((12, 2), (13, 3), (13, 4)):
        img.putpixel((x, y), (0, 0, 0, 0))
    img.putpixel((11, 1), steel_l + (255,))
    img.putpixel((9, 5), steel_d + (255,))
    img.putpixel((10, 5), steel_d + (255,))
    return _outline(img)


def item_plastic():
    img = new()
    d = ImageDraw.Draw(img)
    rng = _rng("plastic_item")
    for (cx, cy) in ((5, 6), (10, 5), (7, 10), (11, 10), (4, 11)):
        col = rng.choice(((236, 236, 240), (220, 226, 236), (246, 240, 228)))
        d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=col + (255,))
        img.putpixel((cx - 1, cy - 1), (255, 255, 255, 255))
        img.putpixel((cx + 1, cy + 1), shade(col, 0.78) + (255,))
    return _outline(img, (90, 92, 100, 255))


def item_silicon():
    img = new()
    pts = [(3, 9), (6, 3), (11, 4), (13, 9), (9, 13), (4, 12)]
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=(92, 98, 116, 255))
    d.polygon([(6, 3), (11, 4), (9, 8), (5, 7)], fill=(150, 158, 182, 255))
    d.polygon([(9, 8), (13, 9), (9, 13)], fill=(70, 74, 92, 255))
    img.putpixel((7, 4), (220, 226, 240, 255))
    return _outline(img)


def item_wafer():
    img = new()
    d = ImageDraw.Draw(img)
    d.ellipse((1, 1, 14, 14), fill=(120, 126, 150, 255))
    for y in range(3, 13, 2):
        for x in range(3, 13, 2):
            if math.hypot(x + 0.5 - 7.5, y + 0.5 - 7.5) < 5.5:
                img.putpixel((x, y), (170, 120, 200, 255) if (x + y) % 4 == 0 else (110, 170, 210, 255))
    d.line((5, 14, 10, 14), fill=(0, 0, 0, 0))
    img.putpixel((4, 4), (230, 230, 250, 255))
    return _outline(img)


def item_circuit():
    img = new()
    d = ImageDraw.Draw(img)
    d.rectangle((1, 3, 14, 12), fill=(30, 110, 60, 255))
    d.rectangle((6, 6, 9, 9), fill=(24, 24, 28, 255))
    for i in range(6, 10):
        img.putpixel((i, 5), (210, 180, 70, 255))
        img.putpixel((i, 10), (210, 180, 70, 255))
    d.line((2, 4, 5, 4), fill=(210, 180, 70, 255))
    d.line((10, 11, 13, 11), fill=(210, 180, 70, 255))
    d.line((2, 8, 5, 8), fill=(210, 180, 70, 255))
    img.putpixel((12, 5), (220, 40, 40, 255))
    img.putpixel((3, 11), (40, 120, 230, 255))
    return _outline(img)


def item_gpu():
    img = new()
    d = ImageDraw.Draw(img)
    d.rectangle((1, 4, 14, 11), fill=(36, 38, 44, 255))
    for x in range(3, 13, 2):
        d.line((x, 5, x, 9), fill=(130, 134, 142, 255))
    d.rectangle((1, 10, 14, 11), fill=(118, 185, 0, 255))
    d.line((2, 12, 9, 12), fill=(210, 170, 60, 255))  # gold PCIe fingers
    d.line((11, 12, 13, 12), fill=(210, 170, 60, 255))
    d.line((15, 4, 15, 11), fill=(150, 150, 158, 255))  # bracket
    return _outline(img)


def item_drive(trained):
    img = new()
    d = ImageDraw.Draw(img)
    body = (54, 60, 74) if not trained else (40, 44, 60)
    d.rectangle((2, 2, 13, 13), fill=body + (255,))
    d.rectangle((4, 3, 11, 7), fill=(170, 176, 190, 255))
    d.rectangle((5, 9, 10, 12), fill=(22, 24, 30, 255))
    if trained:
        glow = (90, 220, 255)
        for (x, y) in ((6, 10), (7, 11), (8, 10), (9, 11), (7, 10), (8, 11)):
            img.putpixel((x, y), glow + (255,))
        img.putpixel((12, 12), (80, 255, 120, 255))
        d.line((4, 5, 11, 5), fill=(90, 220, 255, 255))
    else:
        img.putpixel((12, 12), (90, 90, 96, 255))
    return _outline(img)


def item_chip():
    img = new()
    d = ImageDraw.Draw(img)
    for i in range(4, 12, 2):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            img.putpixel((x, y), (200, 200, 208, 255))
    d.rectangle((3, 3, 12, 12), fill=(28, 28, 34, 255))
    d.rectangle((5, 5, 10, 10), fill=(214, 168, 64, 255))
    d.rectangle((6, 6, 9, 9), fill=(80, 220, 255, 255))
    img.putpixel((7, 7), (230, 255, 255, 255))
    return _outline(img)


def block_textures():
    return {
        "steel": metal("steel", (148, 152, 158), rivets=True),
        "steel_dark": metal("steel_dark", (72, 75, 82)),
        "paint_yellow": paint("paint_yellow", (226, 170, 34)),
        "paint_red": paint("paint_red", (170, 44, 36)),
        "paint_blue": paint("paint_blue", (44, 88, 160)),
        "paint_green": paint("paint_green", (62, 110, 62)),
        "brass": metal("brass", (214, 168, 64), amount=0.07),
        "rust_steel": rust_steel(),
        "copper": metal("copper", (196, 110, 70), amount=0.07),
        "rubber": rubber(),
        "concrete": concrete(),
        "grating": grating(),
        "hazard": hazard(),
        "column_white": column_white(),
        "plastic_white": plastic("plastic_white", (226, 228, 230)),
        "plastic_dark": plastic("plastic_dark", (48, 50, 56)),
        "server_side": server_side(),
        "server_back": server_back(),
        "server_bay": server_bay(),
        "gpu_front": gpu_front(),
        "gpu_side": gpu_side(),
        "screen": screen(),
        "keyboard": keyboard(),
        "fan_grille": fan_grille(),
        "fan_blade": fan_blade(),
        "vent": vent(),
        "glass_dark": glass_dark(),
        "gauge": gauge(),
        "led_green": solid((60, 255, 110), glow=True),
        "led_red": solid((255, 50, 40), glow=True),
        "led_blue": solid((70, 190, 255), glow=True),
        "led_amber": solid((255, 176, 40), glow=True),
        "led_white": solid((255, 255, 255)),
        "wood": wood(),
        "oil_shale": oil_shale(),
    }


def item_textures():
    return {
        "wrench": item_wrench(),
        "crude_oil_bucket": item_bucket((22, 18, 16), (90, 84, 96)),
        "diesel_bucket": item_bucket((214, 168, 40), (255, 236, 150)),
        "plastic": item_plastic(),
        "silicon": item_silicon(),
        "silicon_wafer": item_wafer(),
        "circuit_board": item_circuit(),
        "gpu": item_gpu(),
        "empty_model_drive": item_drive(False),
        "trained_model_drive": item_drive(True),
        "ai_overclock_chip": item_chip(),
    }
