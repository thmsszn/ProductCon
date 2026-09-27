import json

import numpy as np
import pytest
from PIL import Image, ImageDraw

from productcon import Settings, compose, prepare_product, render_background
from productcon.cli import fill_placeholders, main
from productcon.cutout import flood_from_border, remove_background, trim


def _white_bg_ring(size=200):
    """Dunkler Ring auf Weiß – das weiße Innere ist NICHT mit dem Rand verbunden."""
    img = Image.new("RGB", (size, size), "white")
    d = ImageDraw.Draw(img)
    d.ellipse((size * 0.2, size * 0.2, size * 0.8, size * 0.8), fill=(30, 40, 20))
    d.ellipse((size * 0.4, size * 0.4, size * 0.6, size * 0.6), fill=(255, 255, 255))
    return img.convert("RGBA")


def test_flood_fill_only_reaches_border_connected_pixels():
    cand = np.ones((7, 7), dtype=bool)
    cand[1:6, 1:6] = False
    cand[3, 3] = True  # eingeschlossen
    region = flood_from_border(cand)
    assert region[0, 0] and region[6, 6]
    assert not region[3, 3]


def test_flood_fill_follows_winding_paths():
    # Schlangenförmiger Gang, der nur über viele Richtungswechsel erreichbar ist
    cand = np.zeros((9, 9), dtype=bool)
    cand[0, 0] = True
    cand[0:8, 1] = True
    cand[7, 1:8] = True
    cand[1:8, 7] = True
    cand[1, 3:8] = True
    cand[1:6, 3] = True
    region = flood_from_border(cand)
    assert region[cand].all()


def test_remove_background_keeps_enclosed_white():
    out = remove_background(_white_bg_ring())
    alpha = np.asarray(out.getchannel("A"))
    assert alpha[5, 5] == 0            # äußerer Hintergrund transparent
    assert alpha[100, 100] == 255      # weißes Inneres bleibt
    assert alpha[100, 55] == 255       # Ring bleibt


def test_remove_background_has_no_light_fringe():
    img = Image.new("RGB", (300, 300), "white")
    ImageDraw.Draw(img).rectangle((100, 100, 200, 200), fill=(20, 20, 20))
    img = img.resize((150, 150), Image.Resampling.BILINEAR)  # erzeugt Mischpixel an der Kante
    out = np.asarray(remove_background(img.convert("RGBA"))).astype(int)
    visible = out[..., 3] > 30
    # Kein sichtbarer Pixel darf deutlich heller als das Produkt sein
    assert out[visible][:, :3].max() < 80


def test_transparent_input_is_used_as_is():
    img = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle((20, 30, 60, 90), fill=(255, 255, 255, 255))
    product, info = prepare_product(img)
    assert product.size == (41, 61)
    assert "Transparenz" in info


def test_busy_photo_is_not_cut_out_in_auto_mode():
    rng = np.random.default_rng(0)
    photo = Image.fromarray(rng.integers(0, 255, (80, 120, 3), dtype=np.uint8)).convert("RGBA")
    product, info = prepare_product(photo, mode="auto")
    assert product.size == (120, 80)
    assert "nicht einfarbig" in info


def test_trim_rejects_empty_image():
    with pytest.raises(Exception):
        trim(Image.new("RGBA", (10, 10), (0, 0, 0, 0)))


def test_tint_recolors_visible_pixels():
    img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle((5, 5, 14, 14), fill=(0, 0, 0, 255))
    product, _ = prepare_product(img, tint_color="#ff8800")
    assert product.getpixel((3, 3)) == (255, 136, 0, 255)


@pytest.mark.parametrize("theme", ["woodland", "desert", "night", "urban"])
def test_background_is_deterministic(theme):
    s = Settings(width=300, height=200, theme=theme)
    a = np.asarray(render_background(s))
    b = np.asarray(render_background(s))
    assert a.shape == (200, 300, 3)
    assert np.array_equal(a, b)


def test_different_seed_gives_different_pattern():
    a = np.asarray(render_background(Settings(width=300, height=300, seed=1)))
    b = np.asarray(render_background(Settings(width=300, height=300, seed=2)))
    assert not np.array_equal(a, b)


def test_products_of_any_size_end_up_same_size():
    s = Settings(width=400, height=400, shadow=False, grid=False, frame=False, camo_strength=0)
    bg = render_background(s)
    sizes = []
    for w, h in ((10, 10), (800, 800)):
        product = Image.new("RGBA", (w, h), (255, 0, 0, 255))
        out = np.asarray(compose(bg, product, s))
        red = (out[..., 0] > 200) & (out[..., 1] < 60)
        ys, xs = np.nonzero(red)
        sizes.append((xs.max() - xs.min(), ys.max() - ys.min()))
    assert abs(sizes[0][0] - sizes[1][0]) <= 1
    assert abs(sizes[0][1] - sizes[1][1]) <= 1


def test_placeholders():
    from pathlib import Path

    assert fill_placeholders("{name} #{index:03d}", Path("m4_magazin.png"), 7) == "m4 magazin #007"
    assert fill_placeholders("{unknown} bleibt", Path("a.png"), 1) == "{unknown} bleibt"
    assert fill_placeholders(None, Path("a.png"), 1) is None


@pytest.fixture
def workdir(tmp_path, monkeypatch):
    """CLI-Tests in einem leeren Ordner laufen lassen (ohne ./productcon.json)."""
    monkeypatch.chdir(tmp_path)
    return tmp_path


def test_cli_batch(workdir):
    tmp_path = workdir
    src = tmp_path / "in"
    src.mkdir()
    _white_bg_ring(120).convert("RGB").save(src / "patch.jpg")
    icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    ImageDraw.Draw(icon).polygon([(32, 2), (62, 62), (2, 62)], fill=(0, 0, 0, 255))
    icon.save(src / "icon.png")
    (src / "notes.txt").write_text("kein Bild")

    out = tmp_path / "out"
    code = main([str(src), "-o", str(out), "-s", "400x300", "--title", "{name}", "-t", "desert", "-f", "png"])
    assert code == 0
    files = sorted(p.name for p in out.iterdir())
    assert files == ["icon.png", "patch.png"]
    assert Image.open(out / "patch.png").size == (400, 300)


def test_cli_config_roundtrip(workdir):
    cfg = workdir / "cfg.json"
    assert main(["--theme", "night", "--scale", "0.6", "--save-config", str(cfg)]) == 0
    data = json.loads(cfg.read_text(encoding="utf-8"))
    assert data["theme"] == "night" and data["scale"] == 0.6
    loaded = Settings.load(cfg)
    assert loaded.theme == "night"


def test_invalid_settings_are_reported(workdir):
    cfg = workdir / "cfg.json"
    cfg.write_text(json.dumps({"colour": "red"}), encoding="utf-8")
    assert main(["-c", str(cfg), "--list-themes"]) == 0  # list-themes braucht keine Config
    assert main(["-c", str(cfg), str(workdir)]) == 2


def test_config_in_working_dir_is_loaded_automatically(workdir):
    Settings(width=300, height=300, format="png").save(workdir / "productcon.json")
    icon = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    ImageDraw.Draw(icon).rectangle((5, 5, 35, 35), fill=(200, 0, 0, 255))
    (workdir / "input").mkdir()
    icon.save(workdir / "input" / "a.png")
    assert main([]) == 0  # ohne Argumente: ./input → ./output
    assert Image.open(workdir / "output" / "a.png").size == (300, 300)


def test_first_run_creates_input_folder(workdir):
    assert main([]) == 0
    assert (workdir / "input").is_dir()


def test_exe_mode_uses_folder_next_to_exe(tmp_path, monkeypatch):
    """Als EXE liegen input/, output/ und productcon.json neben der EXE – egal von wo gestartet."""
    import sys

    app = tmp_path / "ProductCon"
    app.mkdir()
    elsewhere = tmp_path / "anderswo"
    elsewhere.mkdir()
    monkeypatch.chdir(elsewhere)
    monkeypatch.setattr(sys, "frozen", True, raising=False)
    monkeypatch.setattr(sys, "executable", str(app / "ProductCon.exe"))
    pauses = []
    monkeypatch.setattr("builtins.input", lambda prompt="": pauses.append(prompt))

    assert main([]) == 0  # erster Start legt input/ neben der EXE an
    assert (app / "input").is_dir() and not (elsewhere / "input").exists()
    assert len(pauses) == 1  # Fenster bleibt offen

    Settings(width=300, height=300, format="png").save(app / "productcon.json")
    icon = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    ImageDraw.Draw(icon).rectangle((5, 5, 35, 35), fill=(200, 0, 0, 255))
    icon.save(app / "input" / "a.png")
    assert main(["--no-pause"]) == 0
    assert Image.open(app / "output" / "a.png").size == (300, 300)
    assert len(pauses) == 1  # --no-pause wartet nicht
