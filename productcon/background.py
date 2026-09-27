"""Erzeugt den einheitlichen Military-Hintergrund.

Alles ist deterministisch: gleiche Einstellungen (Größe, Theme, Seed) ergeben
pixelgenau den gleichen Hintergrund.
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps

from .config import Settings
from .fonts import draw_tracked, load_font
from .themes import Theme, get_theme, hex_to_rgb


def _rgb(color: str) -> np.ndarray:
    return np.array(hex_to_rgb(color), dtype=np.float32) / 255.0


def _fractal_noise(rng: np.random.Generator, w: int, h: int, cells: int, octaves: int = 4) -> np.ndarray:
    """Weiches Rauschen (0..1) aus mehreren hochskalierten Zufallsgittern."""
    total = np.zeros((h, w), dtype=np.float32)
    amp, norm = 1.0, 0.0
    for i in range(octaves):
        cx = max(2, cells * 2**i)
        cy = max(2, round(cx * h / w))
        grid = rng.random((cy, cx), dtype=np.float32)
        layer = Image.fromarray(grid, mode="F").resize((w, h), Image.Resampling.BICUBIC)
        total += np.asarray(layer, dtype=np.float32) * amp
        norm += amp
        amp *= 0.5
    total /= norm
    lo, hi = total.min(), total.max()
    return (total - lo) / max(hi - lo, 1e-6)


def _smoothstep(e0: float, e1: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def _camo(rng: np.random.Generator, w: int, h: int, colors: tuple[str, ...]) -> np.ndarray:
    """Woodland-artiges Fleckenmuster als float-RGB-Array (h, w, 3)."""
    img = np.empty((h, w, 3), dtype=np.float32)
    img[:] = _rgb(colors[0])
    # Anteil der Fläche, den die jeweilige Fleckenfarbe bedeckt
    coverage = (0.42, 0.30, 0.18)
    cells = 5 if max(w, h) >= 1000 else 4
    for color, cover in zip(colors[1:], coverage):
        n = _fractal_noise(rng, w, h, cells)
        thr = float(np.quantile(n[::8, ::8], 1.0 - cover))
        mask = _smoothstep(thr - 0.004, thr + 0.004, n)[..., None]
        img = img * (1 - mask) + _rgb(color) * mask
    return img


def _radial(w: int, h: int) -> np.ndarray:
    """0 in der Mitte, ~1 in den Ecken (elliptisch an das Format angepasst)."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    nx = (x - (w - 1) / 2) / (w / 2)
    ny = (y - (h - 1) / 2) / (h / 2)
    return np.sqrt(nx * nx + ny * ny) / np.sqrt(2)


def _base_layer(settings: Settings, theme: Theme) -> Image.Image:
    w, h = settings.width, settings.height
    rng = np.random.default_rng(settings.seed)

    r = _radial(w, h)[..., None]
    center, edge = _rgb(theme.center), _rgb(theme.edge)
    gradient = center + (edge - center) * _smoothstep(0.0, 1.0, r)

    if settings.camo_strength > 0:
        camo = _camo(rng, w, h, theme.camo)
        # Hinter dem Produkt: Muster weicher (Tiefenunschärfe) und schwächer
        blur_radius = max(1.0, min(w, h) * 0.006)
        camo_img = Image.fromarray((camo * 255).astype(np.uint8))
        camo_soft = np.asarray(camo_img.filter(ImageFilter.GaussianBlur(blur_radius)), dtype=np.float32) / 255
        sharpness = _smoothstep(0.15, 0.7, r)
        camo = camo_soft + (camo - camo_soft) * sharpness
        weight = settings.camo_strength * (0.45 + 0.55 * _smoothstep(0.0, 0.8, r))
        # Muster über die Helligkeit des Verlaufs legen, damit Spotlight/Vignette erhalten bleiben
        camo_mean = np.array([np.mean(camo[..., i]) for i in range(3)], dtype=np.float32)
        tinted = gradient * (camo / np.maximum(camo_mean, 1e-3))
        img = gradient + (tinted - gradient) * weight
    else:
        img = gradient

    # Vignette
    img *= (1.0 - 0.45 * _smoothstep(0.35, 1.0, r))
    # Feine Körnung (wie Stoff/Film)
    grain = rng.standard_normal((h, w, 1), dtype=np.float32) * 0.018
    img += grain

    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def _hud_layer(settings: Settings, theme: Theme) -> Image.Image:
    """Rahmen, Raster und Skalenstriche als transparente Ebene."""
    ss = 2  # Supersampling für saubere Kanten
    w, h = settings.width * ss, settings.height * ss
    unit = min(w, h)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    ar, ag, ab = hex_to_rgb(theme.accent)

    margin = round(unit * 0.045)
    thin = max(ss, round(unit * 0.0012))

    if settings.grid:
        step = unit / 14
        cx, cy = w / 2, h / 2
        k = 0
        while cx + k * step < w:
            for x in {round(cx + k * step), round(cx - k * step)}:
                draw.rectangle([x, 0, x + thin - 1, h], fill=(ar, ag, ab, 16))
            k += 1
        k = 0
        while cy + k * step < h:
            for y in {round(cy + k * step), round(cy - k * step)}:
                draw.rectangle([0, y, w, y + thin - 1], fill=(ar, ag, ab, 16))
            k += 1

    if settings.frame:
        length = round(unit * 0.075)
        thick = max(2 * ss, round(unit * 0.0045))
        col = (ar, ag, ab, 215)
        for sx, sy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            x0 = margin if sx == 0 else w - margin - 1
            y0 = margin if sy == 0 else h - margin - 1
            dx = 1 if sx == 0 else -1
            dy = 1 if sy == 0 else -1
            draw.rectangle(sorted_box(x0, y0, x0 + dx * length, y0 + dy * thick), fill=col)
            draw.rectangle(sorted_box(x0, y0, x0 + dx * thick, y0 + dy * length), fill=col)

        # Skalenstriche an der linken und rechten Kante
        tick_col = (ar, ag, ab, 110)
        top, bottom = margin + length * 1.6, h - margin - length * 1.6
        n = 24
        for i in range(n + 1):
            y = round(top + (bottom - top) * i / n)
            tl = unit * (0.018 if i % 4 == 0 else 0.009)
            for x0, dx in ((margin, 1), (w - margin - 1, -1)):
                draw.rectangle(sorted_box(x0, y, x0 + dx * tl, y + thin - 1), fill=tick_col)

        # Kleine Marke oben mittig (unten ist Platz für den Titel)
        mark = unit * 0.024
        draw.rectangle(sorted_box(w / 2 - thin, margin, w / 2 + thin, margin + mark), fill=col)

    return layer.resize((settings.width, settings.height), Image.Resampling.LANCZOS)


def sorted_box(x0: float, y0: float, x1: float, y1: float) -> list[float]:
    return [min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)]


def _header_layer(settings: Settings, theme: Theme, brand: str | None, tag: str | None) -> Image.Image | None:
    if not brand and not tag:
        return None
    w, h = settings.width, settings.height
    unit = min(w, h)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    font = load_font("mono", max(10, round(unit * 0.026)))
    tracking = unit * 0.004
    margin = unit * 0.045
    x_pad = margin + unit * 0.11  # Abstand zu den Eckrahmen
    y = margin + unit * 0.012
    fill = hex_to_rgb(theme.accent) + (235,)
    if brand:
        draw_tracked(draw, (x_pad, y), brand.upper(), font, fill, tracking, anchor="lt")
    if tag:
        draw_tracked(draw, (w - x_pad, y), tag.upper(), font, fill, tracking, anchor="rt")
    return layer


def _load_custom_background(path: str, w: int, h: int) -> Image.Image:
    with Image.open(path) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        return ImageOps.fit(im, (w, h), Image.Resampling.LANCZOS)


def render_background(settings: Settings, brand: str | None = None, tag: str | None = None) -> Image.Image:
    """Kompletter Hintergrund (RGB) inkl. Rahmen und Kopfzeile."""
    theme = get_theme(settings.theme)
    if settings.background:
        img = _load_custom_background(settings.background, settings.width, settings.height)
    else:
        img = _base_layer(settings, theme)
        if settings.grid or settings.frame:
            img = Image.alpha_composite(img.convert("RGBA"), _hud_layer(settings, theme)).convert("RGB")
    header = _header_layer(settings, theme, brand, tag)
    if header is not None:
        img = Image.alpha_composite(img.convert("RGBA"), header).convert("RGB")
    return img


class BackgroundCache:
    """Generiert den (teuren) Grundhintergrund nur einmal pro Batch."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._plain: Image.Image | None = None

    def get(self, brand: str | None, tag: str | None) -> Image.Image:
        if self._plain is None:
            self._plain = render_background(self.settings)
        header = _header_layer(self.settings, get_theme(self.settings.theme), brand, tag)
        if header is None:
            return self._plain.copy()
        return Image.alpha_composite(self._plain.convert("RGBA"), header).convert("RGB")
