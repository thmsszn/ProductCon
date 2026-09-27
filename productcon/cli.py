"""Kommandozeile: productcon [Bilder/Ordner] [Optionen]"""

from __future__ import annotations

import argparse
import os
import sys
from datetime import date
from pathlib import Path

from . import __version__
from .background import BackgroundCache, render_background
from .compose import compose
from .config import OUTPUT_FORMATS, REMOVE_BG_MODES, Settings
from .cutout import SUPPORTED_EXTENSIONS, ImageError, load_image, prepare_product
from .themes import THEMES

DEFAULT_CONFIG = "productcon.json"
DEFAULT_INPUT = "input"
DEFAULT_OUTPUT = "output"


def is_frozen() -> bool:
    """Läuft als EXE (PyInstaller)?"""
    return bool(getattr(sys, "frozen", False))


def app_dir() -> Path:
    """Basis für input/, output/ und productcon.json.

    Als EXE: der Ordner der EXE (Doppelklick/Drag & Drop starten in beliebigen
    Arbeitsordnern). Sonst: der aktuelle Arbeitsordner.
    """
    if is_frozen():
        return Path(sys.executable).resolve().parent
    return Path(".")


def _parse_size(value: str) -> tuple[int, int]:
    try:
        if "x" in value.lower():
            w, h = value.lower().split("x", 1)
            return int(w), int(h)
        return int(value), int(value)
    except ValueError:
        raise argparse.ArgumentTypeError("Größe als 2000 oder 1600x1200 angeben") from None


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="productcon",
        description="Setzt Bilder/Icons einheitlich auf einen Military-Hintergrund.",
        epilog="Beispiel: productcon bilder/ --theme desert --title \"{name}\" --brand \"Mein Shop\"",
        argument_default=argparse.SUPPRESS,
    )
    p.add_argument("inputs", nargs="*", default=[], help="Bilddateien oder Ordner (Standard: ./input)")
    p.add_argument("-o", "--output", default=None, help="Ausgabeordner (Standard: ./output)")
    p.add_argument("-c", "--config", default=None, help=f"JSON-Einstellungen (Standard: ./{DEFAULT_CONFIG}, falls vorhanden)")
    p.add_argument("--version", action="version", version=f"%(prog)s {__version__}")

    g = p.add_argument_group("Hintergrund")
    g.add_argument("-t", "--theme", choices=sorted(THEMES), help="Farbthema (Standard: woodland)")
    g.add_argument("-s", "--size", type=_parse_size, help="Ausgabegröße, z.B. 2000 oder 1600x1200")
    g.add_argument("--background", help="Eigenes Hintergrundbild statt des generierten")
    g.add_argument("--seed", type=int, help="Variante des Tarnmusters (gleicher Seed = gleiches Muster)")
    g.add_argument("--camo-strength", type=float, help="Stärke des Tarnmusters 0..1")
    g.add_argument("--no-grid", dest="grid", action="store_false", help="Raster ausblenden")
    g.add_argument("--no-frame", dest="frame", action="store_false", help="Eckrahmen/Skalen ausblenden")

    g = p.add_argument_group("Motiv")
    g.add_argument("--scale", type=float, help="Grundgröße des Motivs, Anteil der Produktfläche 0..1 (Standard: 0.8)")
    g.add_argument("--no-auto-scale", dest="auto_scale", action="store_false",
                   help="Motiv nur einpassen, statt alle Produkte gleich groß wirken zu lassen")
    g.add_argument("--remove-bg", choices=REMOVE_BG_MODES, help="Hintergrund entfernen: auto (Standard), on, off, ai")
    g.add_argument("--tolerance", type=int, help="Toleranz für einfarbige Hintergründe (Standard: 40)")
    g.add_argument("--tint", help="Motiv einfarbig einfärben, z.B. \"#c9b877\" (für Icons)")
    g.add_argument("--no-shadow", dest="shadow", action="store_false", help="Kein Schlagschatten")
    g.add_argument("--glow", type=float, help="Lichtkante um das Motiv, 0..1 (hilft bei dunklen Motiven)")

    g = p.add_argument_group("Texte (Platzhalter: {name}, {index}, {date})")
    g.add_argument("--title", help="Titel unter dem Motiv, z.B. \"{name}\"")
    g.add_argument("--subtitle", help="Untertitel, z.B. \"Art.-Nr. {index:04d}\"")
    g.add_argument("--brand", help="Kleiner Text oben links (z.B. Shopname)")
    g.add_argument("--tag", help="Kleiner Text oben rechts")
    g.add_argument("--title-font", help="Eigene Schriftdatei (.ttf/.otf) für den Titel")

    g = p.add_argument_group("Ausgabe")
    g.add_argument("-f", "--format", choices=OUTPUT_FORMATS, help="Dateiformat (Standard: jpg)")
    g.add_argument("-q", "--quality", type=int, help="Qualität für jpg/webp (Standard: 92)")

    g = p.add_argument_group("Extras")
    g.add_argument("--list-themes", action="store_true", default=False, help="Themes anzeigen und beenden")
    g.add_argument("--export-background", metavar="DATEI", default=None,
                   help="Nur den leeren Hintergrund speichern (z.B. zum Nachbearbeiten) und beenden")
    g.add_argument("--save-config", metavar="DATEI", default=None,
                   help="Aktuelle Einstellungen als JSON speichern (für immer gleiche Ergebnisse)")
    g.add_argument("--no-pause", action="store_true", default=False,
                   help="EXE: am Ende nicht auf Enter warten und Ausgabeordner nicht öffnen")
    return p


def resolve_settings(args: argparse.Namespace) -> Settings:
    settings = Settings()
    config = args.config
    if config is None and (app_dir() / DEFAULT_CONFIG).is_file():
        config = app_dir() / DEFAULT_CONFIG
    if config:
        settings = Settings.load(config)

    overrides = {}
    for key in ("theme", "background", "seed", "camo_strength", "grid", "frame", "scale", "auto_scale", "remove_bg",
                "tolerance", "tint", "shadow", "glow", "title", "subtitle", "brand", "tag", "title_font",
                "format", "quality"):
        if hasattr(args, key):
            overrides[key] = getattr(args, key)
    if hasattr(args, "size"):
        overrides["width"], overrides["height"] = args.size
    return settings.update(overrides).validate()


def collect_inputs(paths: list[str]) -> list[Path]:
    files: list[Path] = []
    for raw in paths:
        path = Path(raw)
        if path.is_dir():
            files.extend(sorted(
                f for f in path.iterdir()
                if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS and not f.name.startswith(".")
            ))
        elif path.is_file():
            files.append(path)
        else:
            print(f"! Nicht gefunden: {raw}", file=sys.stderr)
    return files


class _SafeDict(dict):
    def __missing__(self, key):
        return "{" + key + "}"


def fill_placeholders(text: str | None, path: Path, index: int) -> str | None:
    if not text:
        return None
    values = _SafeDict(
        name=path.stem.replace("_", " ").strip(),
        index=index,
        date=date.today().isoformat(),
    )
    try:
        return text.format_map(values)
    except (ValueError, IndexError, KeyError, AttributeError):
        return text


def _unique_path(path: Path, used: set[Path]) -> Path:
    candidate, n = path, 2
    while candidate in used:
        candidate = path.with_name(f"{path.stem}_{n}{path.suffix}")
        n += 1
    used.add(candidate)
    return candidate


def save_image(img, path: Path, settings: Settings) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if settings.format == "jpg":
        img.save(path, "JPEG", quality=settings.quality, optimize=True, progressive=True, subsampling=0)
    elif settings.format == "webp":
        img.save(path, "WEBP", quality=settings.quality, method=6)
    else:
        img.save(path, "PNG", optimize=True)


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")  # Windows-Konsolen ohne UTF-8
        except (AttributeError, ValueError):
            pass
    args = build_parser().parse_args(argv)
    interactive = is_frozen() and not args.no_pause

    code, out_dir = run(args)

    if interactive:
        # Per Doppelklick/Drag & Drop gestartet: Ergebnis zeigen, Fenster offen halten
        if out_dir is not None and out_dir.is_dir() and os.name == "nt":
            os.startfile(out_dir.resolve())  # type: ignore[attr-defined]
        try:
            input("\nEnter drücken zum Schließen …")
        except (EOFError, KeyboardInterrupt):
            pass
    return code


def run(args: argparse.Namespace) -> tuple[int, Path | None]:
    """Führt die Verarbeitung aus; liefert Exit-Code und Ausgabeordner."""
    if args.list_themes:
        for name, theme in THEMES.items():
            print(f"  {name:<10} {theme.description}")
        return 0, None

    try:
        settings = resolve_settings(args)
    except (ValueError, OSError, TypeError) as exc:
        print(f"Fehler in den Einstellungen: {exc}", file=sys.stderr)
        return 2, None

    if args.save_config:
        settings.save(args.save_config)
        print(f"Einstellungen gespeichert: {args.save_config}")

    if args.export_background:
        target = Path(args.export_background)
        render_background(settings).save(target)
        print(f"Hintergrund gespeichert: {target}")
        return 0, None

    inputs = args.inputs
    if not inputs:
        if args.save_config:
            return 0, None
        default_dir = app_dir() / DEFAULT_INPUT
        if not default_dir.is_dir():
            default_dir.mkdir(parents=True)
            print(f"Ordner '{default_dir}' angelegt – Bilder hineinlegen und erneut starten.")
            return 0, None
        inputs = [str(default_dir)]

    files = collect_inputs(inputs)
    if not files:
        print("Keine Bilder gefunden. Unterstützt: " + ", ".join(sorted(SUPPORTED_EXTENSIONS)), file=sys.stderr)
        if not args.inputs:
            print(f"Bilder in den Ordner '{app_dir() / DEFAULT_INPUT}' legen oder auf das Programm ziehen.",
                  file=sys.stderr)
        return 1, None

    out_dir = Path(args.output) if args.output else app_dir() / DEFAULT_OUTPUT
    cache = BackgroundCache(settings)
    used: set[Path] = set()
    failed = 0
    print(f"{len(files)} Bild(er) → {out_dir}/  [Theme: {settings.theme}, {settings.width}x{settings.height}]")

    for index, path in enumerate(files, start=1):
        try:
            product, info = prepare_product(load_image(path), settings.remove_bg, settings.tolerance, settings.tint)
            background = cache.get(
                fill_placeholders(settings.brand, path, index),
                fill_placeholders(settings.tag, path, index),
            )
            result = compose(
                background,
                product,
                settings,
                title=fill_placeholders(settings.title, path, index),
                subtitle=fill_placeholders(settings.subtitle, path, index),
            )
            target = _unique_path(out_dir / f"{path.stem}.{settings.format}", used)
            save_image(result, target, settings)
            print(f"  ✔ {path.name} → {target.name}  ({info})")
        except (ImageError, OSError, ValueError) as exc:
            failed += 1
            print(f"  ✘ {path.name}: {exc}", file=sys.stderr)

    done = out_dir if failed < len(files) else None
    if failed:
        print(f"{failed} von {len(files)} Bild(ern) fehlgeschlagen.", file=sys.stderr)
        return 1, done
    print(f"Fertig: {out_dir.resolve()}")
    return 0, done
