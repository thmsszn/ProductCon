"""Farbpaletten für die Military-Hintergründe."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Theme:
    name: str
    description: str
    center: str          # Farbe in der Bildmitte (Spotlight hinter dem Produkt)
    edge: str            # Farbe an den Rändern
    camo: tuple[str, ...]  # 4 Tarnfarben, von der Grundfläche zu den Flecken
    accent: str          # Linien, Rahmen, kleine Texte
    text: str            # Produkttitel


THEMES: dict[str, Theme] = {
    "woodland": Theme(
        name="woodland",
        description="Olivgrün / Braun – klassisches Woodland-Flecktarn",
        center="#5d6640",
        edge="#1c2114",
        camo=("#46512f", "#5f5d3a", "#4a3c28", "#23281a"),
        accent="#c9b877",
        text="#ece5c8",
    ),
    "desert": Theme(
        name="desert",
        description="Sand / Khaki – Wüstentarn",
        center="#e3d4ae",
        edge="#7d6a48",
        camo=("#cbb68a", "#b09567", "#8f7552", "#ddcba0"),
        accent="#3b2e1b",
        text="#2a2114",
    ),
    "night": Theme(
        name="night",
        description="Fast schwarz mit Nachtsicht-Grün – Black Ops",
        center="#2f352c",
        edge="#070807",
        camo=("#1b1f1a", "#252a23", "#121411", "#30362d"),
        accent="#8fd16a",
        text="#dcebd2",
    ),
    "urban": Theme(
        name="urban",
        description="Grautöne mit Warngelb – Urban Camo",
        center="#777b7f",
        edge="#1f2123",
        camo=("#5a5e61", "#44484b", "#7d8184", "#2e3133"),
        accent="#f2b21b",
        text="#f1f1f1",
    ),
}

DEFAULT_THEME = "woodland"


def get_theme(name: str) -> Theme:
    try:
        return THEMES[name.lower()]
    except KeyError:
        known = ", ".join(THEMES)
        raise ValueError(f"Unbekanntes Theme '{name}'. Verfügbar: {known}") from None


def hex_to_rgb(color: str) -> tuple[int, int, int]:
    c = color.strip().lstrip("#")
    if len(c) == 3:
        c = "".join(ch * 2 for ch in c)
    if len(c) != 6:
        raise ValueError(f"Ungültige Farbe '{color}' (erwartet z.B. #c9b877)")
    return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
