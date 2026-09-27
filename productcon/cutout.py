"""Bild laden, Hintergrund entfernen, zuschneiden, einfärben."""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from .themes import hex_to_rgb

SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".tif", ".tiff", ".ico"}


class ImageError(Exception):
    pass


def load_image(path: str | Path) -> Image.Image:
    path = Path(path)
    if path.suffix.lower() == ".svg":
        raise ImageError("SVG wird nicht direkt unterstützt – bitte vorher als PNG exportieren")
    try:
        with Image.open(path) as im:  # bei .ico lädt Pillow automatisch die größte Variante
            im = ImageOps.exif_transpose(im)
            return im.convert("RGBA")
    except (OSError, ValueError) as exc:
        raise ImageError(f"Bild kann nicht gelesen werden: {exc}") from exc


def has_transparency(img: Image.Image) -> bool:
    alpha = np.asarray(img.getchannel("A"))
    return bool((alpha < 250).any())


def _border_pixels(arr: np.ndarray) -> np.ndarray:
    return np.concatenate([arr[0], arr[-1], arr[:, 0], arr[:, -1]])


def border_uniformity(img: Image.Image, tolerance: int) -> tuple[np.ndarray, float]:
    """Hintergrundfarbe am Rand schätzen + Anteil der Randpixel, die dazu passen."""
    arr = np.asarray(img.convert("RGB"), dtype=np.float32)
    border = _border_pixels(arr)
    bg = np.median(border, axis=0)
    dist = np.sqrt(((border - bg) ** 2).sum(axis=1))
    return bg, float((dist <= tolerance).mean())


def _fill_runs(cand: np.ndarray, seed: np.ndarray) -> np.ndarray:
    """Erweitert seed entlang jeder Zeile über zusammenhängende cand-Abschnitte."""
    prev = np.zeros_like(cand)
    prev[:, 1:] = cand[:, :-1]
    starts = cand & ~prev
    run_id = np.cumsum(starts.ravel()) * cand.ravel()
    hit = np.zeros(int(run_id.max()) + 1, dtype=bool)
    hit[run_id[seed.ravel() & cand.ravel()]] = True
    hit[0] = False
    return hit[run_id].reshape(cand.shape)


def flood_from_border(cand: np.ndarray) -> np.ndarray:
    """Alle cand-Pixel, die (4er-Nachbarschaft) mit dem Bildrand verbunden sind."""
    region = np.zeros_like(cand)
    region[0], region[-1], region[:, 0], region[:, -1] = cand[0], cand[-1], cand[:, 0], cand[:, -1]
    while True:
        grown = _fill_runs(cand, region)
        grown = _fill_runs(cand.T, grown.T).T
        if np.array_equal(grown, region):
            return region
        region = grown


def _dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    """Maske um radius px (quadratisch) vergrößern."""
    rows = mask.copy()
    for i in range(1, radius + 1):
        rows[:, i:] |= mask[:, :-i]
        rows[:, :-i] |= mask[:, i:]
    out = rows.copy()
    for i in range(1, radius + 1):
        out[i:] |= rows[:-i]
        out[:-i] |= rows[i:]
    return out


def _nearest_core_color(
    rgb: np.ndarray, core: np.ndarray, ys: np.ndarray, xs: np.ndarray, radius: int
) -> tuple[np.ndarray, np.ndarray]:
    """Für jeden Punkt (ys, xs): Farbe des nächstgelegenen Kernpixels im Umkreis."""
    h, w = core.shape
    color = np.zeros((len(ys), 3), dtype=np.float32)
    found = np.zeros(len(ys), dtype=bool)
    offsets = sorted(
        ((dy, dx) for dy in range(-radius, radius + 1) for dx in range(-radius, radius + 1)),
        key=lambda o: o[0] ** 2 + o[1] ** 2,
    )
    for dy, dx in offsets:
        todo = np.nonzero(~found)[0]
        if len(todo) == 0:
            break
        yy = np.clip(ys[todo] + dy, 0, h - 1)
        xx = np.clip(xs[todo] + dx, 0, w - 1)
        hit = core[yy, xx]
        color[todo[hit]] = rgb[yy[hit], xx[hit]]
        found[todo[hit]] = True
    return color, found


def _strongest_neighbor(
    rgb: np.ndarray, dist: np.ndarray, fg: np.ndarray, ys: np.ndarray, xs: np.ndarray, radius: int
) -> tuple[np.ndarray, np.ndarray]:
    """Für jeden Punkt (ys, xs): Farbe des Produktpixels mit dem größten Abstand zum Hintergrund."""
    h, w = dist.shape
    best_d = np.full(len(ys), -1.0, dtype=np.float32)
    best_c = np.zeros((len(ys), 3), dtype=np.float32)
    for dy in range(-radius, radius + 1):
        yy = np.clip(ys + dy, 0, h - 1)
        for dx in range(-radius, radius + 1):
            xx = np.clip(xs + dx, 0, w - 1)
            d = np.where(fg[yy, xx], dist[yy, xx], -1.0)
            better = d > best_d
            best_d[better] = d[better]
            best_c[better] = rgb[yy[better], xx[better]]
    return best_c, best_d >= 0


def remove_background(img: Image.Image, tolerance: int = 40) -> Image.Image:
    """Einfarbigen Hintergrund (z.B. weiß) entfernen, der den Rand berührt.

    Innenflächen in Hintergrundfarbe, die nicht mit dem Rand verbunden sind,
    bleiben erhalten (z.B. weiße Details im Produkt).
    """
    rgb = np.asarray(img.convert("RGB"), dtype=np.float32)
    bg, _ = border_uniformity(img, tolerance)
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=2))
    region = flood_from_border(dist <= tolerance)
    fg = ~region

    alpha = fg.astype(np.float32)
    out_rgb = rgb.copy()

    # Kantenzone (±2 px): Mischpixel aus Produkt- und Hintergrundfarbe.
    # Die Produktfarbe wird aus dem Inneren geschätzt, daraus ergibt sich die
    # Deckkraft – so entsteht kein heller Saum auf dem dunklen Hintergrund.
    core = ~_dilate(region, 2)
    band = _dilate(fg, 2) & ~core
    ys, xs = np.nonzero(band)
    if len(ys):
        fg_color, known = _nearest_core_color(rgb, core, ys, xs, radius=5)
        if not known.all():
            # sehr dünne Stellen ohne Kern (Riemen, Antennen …): produktähnlichste
            # Farbe aus der direkten Nachbarschaft verwenden
            miss = np.nonzero(~known)[0]
            color, ok = _strongest_neighbor(rgb, dist, fg, ys[miss], xs[miss], radius=2)
            fg_color[miss[ok]] = color[ok]
            known[miss[ok]] = True

        px = rgb[ys, xs]
        diff = fg_color - bg
        denom = (diff**2).sum(axis=1)
        projected = np.clip(((px - bg) * diff).sum(axis=1) / np.maximum(denom, 1.0), 0.0, 1.0)
        reliable = known & (np.sqrt(denom) > tolerance * 1.5)
        # Produktfarbe kaum vom Hintergrund unterscheidbar: Deckkraft über den Farbabstand
        d = dist[ys, xs]
        fallback = np.where(fg[ys, xs], 1.0, np.clip((d - tolerance * 0.35) / (tolerance * 0.65 + 1e-6), 0.0, 1.0))
        a = np.where(reliable, projected, fallback).astype(np.float32)
        unmixed = np.clip((px - (1 - a[:, None]) * bg) / np.maximum(a[:, None], 1e-3), 0, 255)
        alpha[ys, xs] = a
        out_rgb[ys, xs] = np.where(reliable[:, None], fg_color, unmixed)

    src_alpha = np.asarray(img.getchannel("A"), dtype=np.float32) / 255.0
    out_alpha = np.clip(alpha * src_alpha * 255 + 0.5, 0, 255).astype(np.uint8)
    out = np.dstack([np.clip(out_rgb + 0.5, 0, 255).astype(np.uint8), out_alpha])
    return Image.fromarray(out, "RGBA")


AI_MODEL = "isnet-general-use"  # ~170 MB, wird beim ersten Aufruf heruntergeladen
_ai_session = None


def remove_background_ai(img: Image.Image) -> Image.Image:
    """Hintergrund per KI (rembg) entfernen – auch bei unruhigen Fotos."""
    global _ai_session
    if getattr(sys, "frozen", False):
        raise ImageError(
            "KI-Freistellung ist in der EXE nicht enthalten – dafür die Python-Version mit rembg nutzen"
        )
    try:
        from rembg import new_session, remove  # type: ignore
    except ImportError as exc:
        raise ImageError(
            "Für --remove-bg ai wird 'rembg' benötigt: pip install \"rembg[cpu]\""
        ) from exc
    if _ai_session is None:
        _ai_session = new_session(AI_MODEL)
    return remove(img, session=_ai_session).convert("RGBA")


def trim(img: Image.Image, threshold: int = 8) -> Image.Image:
    """Transparente Ränder abschneiden."""
    alpha = img.getchannel("A").point(lambda v: 255 if v > threshold else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ImageError("Kein Motiv gefunden – Bild ist leer oder besteht nur aus Hintergrund")
    return img.crop(bbox)


def tint(img: Image.Image, color: str) -> Image.Image:
    """Alle sichtbaren Pixel einfarbig einfärben (für Icons/Silhouetten)."""
    r, g, b = hex_to_rgb(color)
    solid = Image.new("RGBA", img.size, (r, g, b, 255))
    solid.putalpha(img.getchannel("A"))
    return solid


def prepare_product(
    img: Image.Image,
    mode: str = "auto",
    tolerance: int = 40,
    tint_color: str | None = None,
    max_side: int = 4000,
) -> tuple[Image.Image, str]:
    """Liefert das freigestellte, zugeschnittene Motiv + eine kurze Info, was passiert ist."""
    if max(img.size) > max_side:
        img = img.copy()
        img.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)

    info = "Transparenz übernommen"
    if mode == "ai":
        img = remove_background_ai(img)
        info = "Hintergrund per KI entfernt"
    elif mode == "on" or (mode == "auto" and not has_transparency(img)):
        _, uniform = border_uniformity(img, tolerance)
        if mode == "on" or uniform >= 0.6:
            img = remove_background(img, tolerance)
            info = "einfarbigen Hintergrund entfernt"
        else:
            info = "Hintergrund nicht einfarbig – Bild bleibt rechteckig (Tipp: --remove-bg ai)"
    elif mode == "off":
        info = "Hintergrund unverändert"

    img = trim(img)
    if tint_color:
        img = tint(img, tint_color)
    return img, info
