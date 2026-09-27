"""Schriften laden (mitgelieferte Stencil-Schrift, sonst Systemschrift)."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from PIL import ImageDraw, ImageFont

FONT_DIR = Path(__file__).resolve().parent / "fonts"

TITLE_FONTS = (
    str(FONT_DIR / "BlackOpsOne-Regular.ttf"),
    "impact.ttf",
    "DejaVuSans-Bold.ttf",
    "arialbd.ttf",
    "LiberationSans-Bold.ttf",
)
MONO_FONTS = (
    str(FONT_DIR / "ShareTechMono-Regular.ttf"),
    "consola.ttf",
    "DejaVuSansMono.ttf",
    "LiberationMono-Regular.ttf",
    "cour.ttf",
)


@lru_cache(maxsize=64)
def load_font(kind: str, size: int, custom: str | None = None) -> ImageFont.FreeTypeFont:
    candidates = ((custom,) if custom else ()) + (TITLE_FONTS if kind == "title" else MONO_FONTS)
    for name in candidates:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def text_width(font: ImageFont.FreeTypeFont, text: str, tracking: float = 0.0) -> float:
    """Breite eines Textes inkl. Buchstabenabstand (tracking in px)."""
    if not text:
        return 0.0
    if tracking == 0:
        return font.getlength(text)
    # gleiche Rechnung wie beim zeichenweisen Zeichnen in draw_tracked (ohne Kerning)
    return sum(font.getlength(ch) for ch in text) + tracking * (len(text) - 1)


def draw_tracked(
    draw: ImageDraw.ImageDraw,
    xy: tuple[float, float],
    text: str,
    font: ImageFont.FreeTypeFont,
    fill,
    tracking: float = 0.0,
    anchor: str = "ls",
) -> None:
    """Text mit Buchstabenabstand zeichnen.

    anchor: erster Buchstabe = l/m/r (links/mitte/rechts vom Gesamttext),
    zweiter = vertikaler Pillow-Anker (z.B. "s" Grundlinie, "t" oben).
    """
    x, y = xy
    total = text_width(font, text, tracking)
    if anchor[0] == "m":
        x -= total / 2
    elif anchor[0] == "r":
        x -= total
    v_anchor = "l" + anchor[1]
    if tracking == 0:
        draw.text((x, y), text, font=font, fill=fill, anchor=v_anchor)
        return
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill, anchor=v_anchor)
        x += font.getlength(ch) + tracking
