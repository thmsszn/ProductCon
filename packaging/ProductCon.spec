# -*- mode: python ; coding: utf-8 -*-
# Baut ProductCon.exe (eine einzelne Datei):
#     pip install pyinstaller
#     pyinstaller --noconfirm packaging/ProductCon.spec
# Ergebnis: dist/ProductCon.exe
from pathlib import Path

ROOT = Path(SPECPATH).parent

a = Analysis(
    [str(ROOT / "packaging" / "entry.py")],
    pathex=[str(ROOT)],
    datas=[(str(ROOT / "productcon" / "fonts"), "productcon/fonts")],
    # KI-Freistellung (rembg/onnxruntime) ist zu groß für die EXE
    excludes=["tkinter", "rembg", "onnxruntime", "pytest", "matplotlib", "scipy"],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="ProductCon",
    console=True,  # Fortschritt anzeigen; Fenster bleibt am Ende offen
    upx=False,
    icon=str(ROOT / "packaging" / "productcon.ico"),
)
