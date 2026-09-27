"""Einstellungen – damit jedes Produktbild exakt gleich aufgebaut ist."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, fields
from pathlib import Path

from PIL import ImageFont

from .themes import DEFAULT_THEME, get_theme, hex_to_rgb

REMOVE_BG_MODES = ("auto", "on", "off", "ai")
OUTPUT_FORMATS = ("png", "jpg", "webp")


@dataclass
class Settings:
    # Leinwand
    width: int = 2000
    height: int = 2000
    theme: str = DEFAULT_THEME
    background: str | None = None   # eigenes Hintergrundbild statt generiertem
    seed: int = 1337                # gleicher Seed = exakt gleiches Tarnmuster
    camo_strength: float = 0.55     # 0 = kein Tarnmuster, 1 = volle Deckkraft
    grid: bool = True
    frame: bool = True

    # Produkt
    scale: float = 0.8              # Grundgröße des Motivs, Anteil der Produktfläche (0..1)
    auto_scale: bool = True         # alle Motive gleich groß wirken lassen (lange größer, kompakte kleiner)
    remove_bg: str = "auto"         # auto | on | off | ai
    tolerance: int = 40             # Farbabstand für die Hintergrund-Erkennung
    tint: str | None = None         # Icons einfärben, z.B. "#c9b877"
    shadow: bool = True
    shadow_opacity: float = 0.6
    glow: float = 0.0               # Lichtkante um das Produkt (0 = aus)

    # Texte – Platzhalter: {name}, {index}, {date}
    title: str | None = None
    subtitle: str | None = None
    brand: str | None = None        # klein oben links
    tag: str | None = None          # klein oben rechts
    title_font: str | None = None   # eigene .ttf/.otf für den Titel

    # Ausgabe
    format: str = "jpg"
    quality: int = 92

    def validate(self) -> "Settings":
        if self.width < 200 or self.height < 200:
            raise ValueError("Breite/Höhe müssen mindestens 200 px sein")
        if self.width > 10000 or self.height > 10000:
            raise ValueError("Breite/Höhe dürfen höchstens 10000 px sein")
        get_theme(self.theme)
        if self.remove_bg not in REMOVE_BG_MODES:
            raise ValueError(f"remove_bg muss eins von {REMOVE_BG_MODES} sein")
        self.format = self.format.lower().replace("jpeg", "jpg")
        if self.format not in OUTPUT_FORMATS:
            raise ValueError(f"format muss eins von {OUTPUT_FORMATS} sein")
        if not 0.05 <= self.scale <= 1.0:
            raise ValueError("scale muss zwischen 0.05 und 1.0 liegen")
        if not 0.0 <= self.camo_strength <= 1.0:
            raise ValueError("camo_strength muss zwischen 0 und 1 liegen")
        if not 0.0 <= self.shadow_opacity <= 1.0 or not 0.0 <= self.glow <= 1.0:
            raise ValueError("shadow_opacity und glow müssen zwischen 0 und 1 liegen")
        if not 0 <= self.tolerance <= 255:
            raise ValueError("tolerance muss zwischen 0 und 255 liegen")
        if not 1 <= self.quality <= 100:
            raise ValueError("quality muss zwischen 1 und 100 liegen")
        if self.tint:
            hex_to_rgb(self.tint)
        if self.background and not Path(self.background).is_file():
            raise ValueError(f"Hintergrundbild nicht gefunden: {self.background}")
        if self.title_font:
            try:
                ImageFont.truetype(self.title_font, 12)
            except OSError:
                raise ValueError(f"Schrift kann nicht geladen werden: {self.title_font}") from None
        return self

    def to_dict(self) -> dict:
        return asdict(self)

    def save(self, path: str | Path) -> None:
        Path(path).write_text(
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

    @classmethod
    def load(cls, path: str | Path) -> "Settings":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        return cls().update(data)

    def update(self, data: dict) -> "Settings":
        known = {f.name for f in fields(self)}
        unknown = sorted(set(data) - known)
        if unknown:
            raise ValueError(f"Unbekannte Einstellung(en): {', '.join(unknown)}")
        for key, value in data.items():
            setattr(self, key, value)
        return self
