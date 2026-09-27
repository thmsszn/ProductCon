"""Setzt das Motiv auf den Hintergrund – immer an die gleiche Stelle, in gleicher Größe."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from .config import Settings
from .fonts import draw_tracked, load_font, text_width
from .themes import get_theme, hex_to_rgb


@dataclass
class Box:
    left: float
    top: float
    right: float
    bottom: float

    @property
    def width(self) -> float:
        return self.right - self.left

    @property
    def height(self) -> float:
        return self.bottom - self.top


def product_area(settings: Settings, has_text: bool) -> Box:
    """Fläche, in die das Motiv eingepasst wird (innerhalb des Rahmens, über dem Titel)."""
    w, h = settings.width, settings.height
    unit = min(w, h)
    inset = unit * 0.11
    bottom = h - (unit * 0.24 if has_text else inset)
    return Box(inset, inset, w - inset, bottom)


# Automatische Skalierung: Bezugsgröße ist ein kompaktes, quadratisches Produkt
# (z.B. ein runder Patch) mit dieser Deckung ...
REFERENCE_COVERAGE = 0.8
# ... das etwas kleiner als "scale" dargestellt wird, damit lange Produkte
# (Gewehr, Messer, Zielfernrohr) im Vergleich größer werden können.
REFERENCE_SIZE = 0.875


def coverage(product: Image.Image) -> float:
    """Anteil der sichtbaren Pixel im Rahmen des Motivs (0..1)."""
    return float(np.asarray(product.getchannel("A"), dtype=np.float32).mean() / 255.0)


def scale_factor(product: Image.Image, box: Box, scale: float, auto: bool = True) -> float:
    """Vergrößerungsfaktor für das (zugeschnittene) Motiv.

    auto=False: Motiv wird in scale × Produktfläche eingepasst – lange Produkte
    wirken dadurch deutlich kleiner als kompakte.
    auto=True: Alle Motive bekommen die gleiche optische Fläche (Rahmenfläche,
    gewichtet mit der Deckung); lange Produkte dürfen dafür bis an den Rand der
    Produktfläche wachsen, kompakte werden etwas kleiner.
    """
    w, h = product.size
    fits = min(box.width / w, box.height / h)  # größter Faktor, der noch in die Fläche passt
    if not auto:
        return fits * scale
    ref = min(box.width, box.height) * scale * REFERENCE_SIZE
    density = min(1.0, max(0.15, coverage(product)))
    factor = ref * np.sqrt(np.sqrt(REFERENCE_COVERAGE) / (w * h * np.sqrt(density)))
    return float(min(factor, fits))


def fit_product(product: Image.Image, box: Box, scale: float, auto: bool = True) -> Image.Image:
    factor = scale_factor(product, box, scale, auto)
    size = (max(1, round(product.width * factor)), max(1, round(product.height * factor)))
    return product.resize(size, Image.Resampling.LANCZOS)


def _shadow(alpha: Image.Image, unit: int, opacity: float) -> tuple[Image.Image, int, int]:
    """Weicher Schlagschatten; liefert Ebene + Versatz relativ zum Motiv."""
    blur = max(2, round(unit * 0.014))
    pad = blur * 3
    offset = round(unit * 0.012)
    mask = Image.new("L", (alpha.width + 2 * pad, alpha.height + 2 * pad), 0)
    mask.paste(alpha.point(lambda v: round(v * opacity)), (pad, pad))
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    layer = Image.new("RGBA", mask.size, (0, 0, 0, 255))
    layer.putalpha(mask)
    return layer, -pad, -pad + offset


def _glow(alpha: Image.Image, unit: int, color: str, strength: float) -> tuple[Image.Image, int, int]:
    radius = max(2, round(unit * 0.01))
    pad = radius * 4
    mask = Image.new("L", (alpha.width + 2 * pad, alpha.height + 2 * pad), 0)
    mask.paste(alpha, (pad, pad))
    mask = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(radius))
    mask = mask.point(lambda v: min(255, round(v * strength)))
    layer = Image.new("RGBA", mask.size, hex_to_rgb(color) + (255,))
    layer.putalpha(mask)
    return layer, -pad, -pad


def _fit_font(kind: str, text: str, max_width: float, start: int, tracking_ratio: float, custom: str | None):
    size = start
    while size > 10:
        font = load_font(kind, size, custom)
        if text_width(font, text, size * tracking_ratio) <= max_width:
            return font, size * tracking_ratio
        size = int(size * 0.92)
    font = load_font(kind, size, custom)
    return font, size * tracking_ratio


def _draw_title(canvas: Image.Image, settings: Settings, title: str | None, subtitle: str | None) -> None:
    theme = get_theme(settings.theme)
    w, h = canvas.size
    unit = min(w, h)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    max_width = w - unit * 0.3
    accent = hex_to_rgb(theme.accent)
    text_col = hex_to_rgb(theme.text)

    title_base = h - unit * 0.115
    if title:
        title = title.upper()
        font, tracking = _fit_font("title", title, max_width, round(unit * 0.068), 0.06, settings.title_font)
        # dezenter Schatten für Lesbarkeit
        off = max(1, round(unit * 0.003))
        draw_tracked(draw, (w / 2 + off, title_base + off), title, font, (0, 0, 0, 150), tracking, anchor="ms")
        draw_tracked(draw, (w / 2, title_base), title, font, text_col + (255,), tracking, anchor="ms")
        # Akzentlinien links und rechts vom Titel – nur so lang, wie Platz bis zum Eckrahmen ist
        tw = text_width(font, title, tracking)
        gap = unit * 0.025
        length = min(unit * 0.06, w / 2 - unit * 0.15 - (tw / 2 + gap))
        if length >= unit * 0.015:
            line_y = title_base - font.size * 0.36
            thick = max(2, round(unit * 0.003))
            for side in (-1, 1):
                x0 = w / 2 + side * (tw / 2 + gap)
                x1 = x0 + side * length
                draw.rectangle(
                    [min(x0, x1), line_y - thick / 2, max(x0, x1), line_y + thick / 2], fill=accent + (220,)
                )

    if subtitle:
        subtitle = subtitle.upper()
        font, tracking = _fit_font("mono", subtitle, max_width, round(unit * 0.03), 0.25, None)
        y = title_base + unit * 0.05 if title else title_base
        draw_tracked(draw, (w / 2, y), subtitle, font, accent + (235,), tracking, anchor="ms")

    canvas.alpha_composite(layer)


def compose(
    background: Image.Image,
    product: Image.Image,
    settings: Settings,
    title: str | None = None,
    subtitle: str | None = None,
) -> Image.Image:
    """Motiv (RGBA, bereits zugeschnitten) mittig in die Produktfläche setzen."""
    canvas = background.convert("RGBA")
    if canvas.size != (settings.width, settings.height):
        canvas = canvas.resize((settings.width, settings.height), Image.Resampling.LANCZOS)
    unit = min(canvas.size)
    theme = get_theme(settings.theme)

    box = product_area(settings, bool(title or subtitle))
    fitted = fit_product(product, box, settings.scale, settings.auto_scale)
    x = round(box.left + (box.width - fitted.width) / 2)
    y = round(box.top + (box.height - fitted.height) / 2)
    alpha = fitted.getchannel("A")

    if settings.glow > 0:
        layer, dx, dy = _glow(alpha, unit, theme.accent, settings.glow)
        _paste(canvas, layer, x + dx, y + dy)
    if settings.shadow and settings.shadow_opacity > 0:
        layer, dx, dy = _shadow(alpha, unit, settings.shadow_opacity)
        _paste(canvas, layer, x + dx, y + dy)
    canvas.alpha_composite(fitted, (x, y))

    if title or subtitle:
        _draw_title(canvas, settings, title, subtitle)
    return canvas.convert("RGB")


def _paste(canvas: Image.Image, layer: Image.Image, x: int, y: int) -> None:
    """alpha_composite, das auch mit Ebenen klarkommt, die über den Rand ragen."""
    full = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    full.paste(layer, (x, y))
    canvas.alpha_composite(full)
