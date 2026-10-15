#!/usr/bin/env python3
"""Generates all resources of the Crude & Compute mod.

    python3 tools/generate_assets.py            # write assets + data into src/main/resources
    python3 tools/generate_assets.py --preview  # additionally render PNG previews into tools/preview

Outputs (all committed, so building the mod does not need Python):
  assets/crudecompute/models/{block,item}/*.obj|.mtl|.json   free-form OBJ models
  assets/crudecompute/textures/{block,item}/*.png            procedural textures
  assets/crudecompute/blockstates/*.json, lang/*.json
  data/crudecompute/{recipe,loot_table,worldgen,neoforge,structure}/...
"""

import gzip
import io
import json
import math
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import models as M  # noqa: E402
import textures as T  # noqa: E402
from meshlib import MODID, Mesh, beam, write_mtl  # noqa: E402

ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MODID)
DATA = os.path.join(ROOT, "data", MODID)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ns(path):
    return f"{MODID}:{path}"


# =============================================================================== models
def obj_model_json(kind, name, particle, item=False):
    j = {}
    if item:
        j["parent"] = "minecraft:block/block"
    j.update({
        "loader": "neoforge:obj",
        "model": ns(f"models/{kind}/{name}.obj"),
        "flip_v": False,
        "automatic_culling": False,
        "shade_quads": True,
        "emissive_ambient": True,
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {"particle": ns(f"block/{particle}")},
    })
    return j


def write_obj_model(kind, name, mesh, particle, item=False):
    folder = os.path.join(ASSETS, "models", kind)
    os.makedirs(folder, exist_ok=True)
    mesh.write_obj(os.path.join(folder, f"{name}.obj"), f"{name}.mtl")
    write_mtl(os.path.join(folder, f"{name}.mtl"), mesh.materials())
    write_json(os.path.join(folder, f"{name}.json"), obj_model_json(kind, name, particle, item))


def pumpjack_full():
    m = M.pumpjack_static()
    m.add(M.pumpjack_beam()).add(M.pumpjack_crank()).add(M.pumpjack_rod())
    px_, py, pz = M.PUMPJACK_PIVOT
    _, cy, cz = M.PUMPJACK_CRANK
    eq = pz + M.PUMPJACK_BEAM_BACK
    for x in (0.09, 0.91):
        m.add(beam((x, cy, cz + M.PUMPJACK_CRANK_R), (x, py - 0.16, eq), 0.06, 0.08, "steel"))
    for x in (0.46, 0.54):
        m.add(beam((x, py, -1.5), (x, M.PUMPJACK_CARRIER_Y, -1.5), 0.025, 0.025, "steel_dark"))
    return m


def shaft_stub(direction):
    """Half shaft from the centre to the given face, for composite item models."""
    s = M.shaft_half()
    rot = {"up": lambda m: m, "down": lambda m: m.rotate_x(180, (0.5, 0.5, 0.5)),
           "south": lambda m: m.rotate_x(90, (0.5, 0.5, 0.5)), "north": lambda m: m.rotate_x(-90, (0.5, 0.5, 0.5)),
           "east": lambda m: m.rotate_z(-90, (0.5, 0.5, 0.5)), "west": lambda m: m.rotate_z(90, (0.5, 0.5, 0.5))}
    return rot[direction](s)


def server_rack_full():
    m = M.server_rack()
    u = 0
    for size in (4, 2, 2, 1, 1, 1, 2):
        m.add(M.server_chassis(size).translate(0, M.U0_ + u * M.U_H_, 0))
        u += size
    return m


# Static block models used by blockstates: name -> (mesh factory, particle texture)
BLOCK_MODELS = {
    "shaft": (M.shaft, "steel"),
    "shaft_half": (M.shaft_half, "steel"),
    "gearbox": (M.gearbox, "brass"),
    "hand_crank": (M.hand_crank_base, "steel"),
    "hand_crank_handle": (M.hand_crank_anim, "wood"),
    "windmill": (M.windmill, "plastic_white"),
    "windmill_rotor": (M.windmill_anim, "plastic_white"),
    "diesel_engine": (M.diesel_engine, "paint_red"),
    "diesel_engine_flywheel": (M.diesel_engine_anim, "steel"),
    "generator": (lambda: M.generator(False), "paint_blue"),
    "generator_active": (lambda: M.generator(True), "paint_blue"),
    "pumpjack": (M.pumpjack_static, "paint_yellow"),
    "pumpjack_beam": (M.pumpjack_beam, "paint_yellow"),
    "pumpjack_crank": (M.pumpjack_crank, "paint_red"),
    "pumpjack_pitman": (M.pumpjack_pitman, "steel"),
    "pumpjack_cable": (M.pumpjack_cable, "steel_dark"),
    "pumpjack_rod": (M.pumpjack_rod, "steel"),
    "refinery": (M.refinery, "column_white"),
    "fluid_pipe_center": (M.pipe_center, "copper"),
    "fluid_pipe_arm": (M.pipe_arm, "copper"),
    "oil_tank": (M.oil_tank, "column_white"),
    "power_cable_center": (M.cable_center, "cable_black"),
    "power_cable_arm": (M.cable_arm, "rubber"),
    "power_cable_plug": (M.cable_plug, "rubber"),
    "wind_tower": (M.wind_tower, "column_white"),
    "tower_base": (M.tower_base, "concrete"),
    "server_rack": (M.server_rack, "steel_dark"),
    "rack_fan_frame": (M.rack_fan_frame, "steel_dark"),
    "rack_fan_frame_hp": (lambda: M.rack_fan_frame(True), "paint_blue"),
    "rack_fan_rotor": (M.rack_fan_rotor, "steel"),
    "rack_fan_rotor_hp": (lambda: M.rack_fan_rotor(True), "paint_red"),
    "gpu_blade": (M.gpu_blade, "gpu_side"),
    "solar_panel": (lambda: M.solar_panel("solar_cell_poly"), "solar_cell_poly"),
    "solar_panel_mono": (lambda: M.solar_panel("solar_cell_mono"), "solar_cell_mono"),
    "inverter": (M.inverter, "plastic_white"),
    "server_1u": (lambda: M.server_chassis(1), "steel_dark"),
    "server_2u": (lambda: M.server_chassis(2), "steel_dark"),
    "server_4u": (lambda: M.server_chassis(4), "steel_dark"),
    "cooling_unit": (lambda: M.cooling_unit(False), "plastic_white"),
    "cooling_unit_active": (lambda: M.cooling_unit(True), "plastic_white"),
    "cooling_unit_fan": (M.cooling_unit_anim, "fan_blade"),
    "ai_terminal": (M.ai_terminal, "plastic_dark"),
    "conveyor_belt": (M.conveyor_belt, "steel_dark"),
    "conveyor_rib": (M.conveyor_rib, "steel"),
    "oil_derrick": (M.oil_derrick, "paint_red"),
    "derrick_block": (M.derrick_block, "paint_yellow"),
    "derrick_kelly": (M.derrick_kelly, "steel"),
    "mechanical_pump": (M.mechanical_pump, "paint_green"),
    "mechanical_pump_gear": (M.mechanical_pump_gear, "brass"),
    "battery": (M.battery, "battery_cell"),
    "chip_fab": (lambda: M.chip_fab(), "plastic_white"),
    "chip_fab_bridge": (M.chip_fab_bridge, "paint_yellow"),
    "chip_fab_head": (M.chip_fab_head, "steel"),
    "chip_fab_wafer": (M.chip_fab_wafer, "wafer"),
}

# Inventory models: full machine incl. animated parts, normalised into one block
ITEM_MODELS = {
    "shaft": (lambda: M.shaft().rotate_z(-90, (0.5, 0.5, 0.5)), "steel"),
    "gearbox": (lambda: M.gearbox().add(shaft_stub("east")).add(shaft_stub("west")), "brass"),
    "hand_crank": (lambda: M.hand_crank_base().add(M.hand_crank_anim()), "steel"),
    "windmill": (lambda: M.windmill().add(M.windmill_anim()), "plastic_white"),
    "diesel_engine": (lambda: M.diesel_engine().add(M.diesel_engine_anim()).add(shaft_stub("north")), "paint_red"),
    "generator": (lambda: M.generator(True).add(M.shaft().rotate_x(90, (0.5, 0.5, 0.5))), "paint_blue"),
    "pumpjack": (pumpjack_full, "paint_yellow"),
    "refinery": (M.refinery, "column_white"),
    "fluid_pipe": (lambda: M.pipe_center().add(M.pipe_arm()).add(M.pipe_arm().rotate_y(180, (0.5, 0.5, 0.5))), "copper"),
    "oil_tank": (M.oil_tank, "column_white"),
    "power_cable": (lambda: M.cable_center().add(M.cable_arm()).add(M.cable_plug().rotate_y(180, (0.5, 0.5, 0.5))), "rubber"),
    "wind_tower": (M.wind_tower, "column_white"),
    "tower_base": (M.tower_base, "concrete"),
    "server_rack": (server_rack_full, "steel_dark"),
    "cooling_unit": (lambda: M.cooling_unit(True).add(M.cooling_unit_anim()), "plastic_white"),
    "ai_terminal": (M.ai_terminal, "plastic_dark"),
    "conveyor_belt": (lambda: M.conveyor_belt().add(M.conveyor_rib()).add(M.conveyor_rib().translate(0, 0, 0.25)).add(M.conveyor_rib().translate(0, 0, -0.25)), "steel_dark"),
    "oil_derrick": (lambda: M.oil_derrick().add(M.derrick_block().translate(0, 4.2, 0)), "paint_red"),
    "solar_panel": (lambda: M.solar_panel("solar_cell_poly"), "solar_cell_poly"),
    "solar_panel_mono": (lambda: M.solar_panel("solar_cell_mono"), "solar_cell_mono"),
    "inverter": (M.inverter, "plastic_white"),
    "mechanical_pump": (lambda: M.mechanical_pump().add(M.mechanical_pump_gear()), "paint_green"),
    "battery": (M.battery, "battery_cell"),
    "chip_fab": (lambda: M.chip_fab().add(M.chip_fab_bridge()).add(M.chip_fab_head()).add(M.chip_fab_wafer()), "plastic_white"),
}

FLAT_ITEMS = ["server_1u", "server_2u", "server_4u", "wrench", "crude_oil_bucket", "diesel_bucket", "plastic", "silicon", "silicon_wafer", "circuit_board",
              "gpu", "empty_model_drive", "trained_model_drive", "ai_overclock_chip"]


# =============================================================================== blockstates
HORIZONTAL_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
SIX_FROM_UP = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "east": (90, 90), "south": (90, 180), "west": (90, 270)}


def variant(model, x=0, y=0):
    v = {"model": ns(f"block/{model}")}
    if x:
        v["x"] = x
    if y:
        v["y"] = y
    return v


def horizontal_states(model_fn, extra=None):
    """model_fn(props) -> model name. extra = list of (prop, values)."""
    variants = {}
    combos = [{}]
    for prop, values in (extra or []):
        combos = [dict(c, **{prop: v}) for c in combos for v in values]
    for facing, rot in HORIZONTAL_Y.items():
        for c in combos:
            props = dict(c, facing=facing)
            key = ",".join(f"{k}={v}" for k, v in sorted(props.items()))
            variants[key] = variant(model_fn(props), y=rot)
    return {"variants": variants}


def connection_multipart(center, arm):
    parts = [{"apply": {"model": ns(f"block/{center}")}}]
    for d, (x, y) in {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}.items():
        apply = {"model": ns(f"block/{arm}")}
        if x:
            apply["x"] = x
        if y:
            apply["y"] = y
        parts.append({"when": {d: "true"}, "apply": apply})
    return {"multipart": parts}


def hardware_assets(item_models_dir):
    """One icon texture + item model per catalog part (rendered from small 3D models in brand colours)."""
    import hwicons
    tex_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(tex_dir, exist_ok=True)
    for part in hwicons.parse_catalog():
        name = f"hw_{part['id']}"
        hwicons.make_icon(part, size=32).save(os.path.join(tex_dir, f"{name}.png"))
        write_json(os.path.join(item_models_dir, f"{name}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns(f"item/{name}")}})


def cable_multipart():
    """Cable arms; a side with plug_<dir> draws the plug variant instead of the cable-to-cable gland."""
    parts = [{"apply": {"model": ns("block/power_cable_center")}}]
    for d, (x, y) in {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}.items():
        for plug in (False, True):
            apply = {"model": ns("block/power_cable_plug" if plug else "block/power_cable_arm")}
            if x:
                apply["x"] = x
            if y:
                apply["y"] = y
            parts.append({"when": {"AND": [{d: "true"}, {f"plug_{d}": "true" if plug else "false"}]}, "apply": apply})
    return {"multipart": parts}


def blockstates():
    bs = {}
    simple = lambda m: {"variants": {"": {"model": ns(f"block/{m}")}}}
    bs["oil_shale"] = simple("oil_shale")
    bs["crude_oil"] = simple("crude_oil")
    bs["diesel"] = simple("diesel")
    bs["multiblock_part"] = {"multipart": [{"apply": {"model": ns("block/multiblock_part")}}]}
    bs["shaft"] = {"variants": {f"axis={a}": {"model": ns("block/shaft_particle")} for a in "xyz"}}
    bs["gearbox"] = simple("gearbox")
    bs["hand_crank"] = {"variants": {f"facing={f}": variant("hand_crank", x, y) for f, (x, y) in SIX_FROM_UP.items()}}
    bs["windmill"] = horizontal_states(lambda p: "windmill")
    bs["diesel_engine"] = horizontal_states(lambda p: "diesel_engine", [("active", ["false", "true"])])
    bs["generator"] = horizontal_states(lambda p: "generator_active" if p["active"] == "true" else "generator",
                                        [("active", ["false", "true"])])
    bs["pumpjack"] = horizontal_states(lambda p: "pumpjack", [("active", ["false", "true"])])
    bs["refinery"] = horizontal_states(lambda p: "refinery", [("active", ["false", "true"])])
    bs["server_rack"] = horizontal_states(lambda p: "server_rack", [("active", ["false", "true"])])
    bs["ai_terminal"] = horizontal_states(lambda p: "ai_terminal")
    bs["fluid_pipe"] = connection_multipart("fluid_pipe_center", "fluid_pipe_arm")
    bs["power_cable"] = cable_multipart()
    bs["wind_tower"] = {"variants": {f"axis={a}": {"model": ns("block/wind_tower")} for a in "xyz"}}
    bs["tower_base"] = simple("tower_base")
    bs["oil_tank"] = simple("oil_tank")
    bs["mechanical_pump"] = {"variants": {f"facing={f}": variant("mechanical_pump", x, y) for f, (x, y) in {
        "north": (0, 0), "south": (0, 180), "east": (0, 90), "west": (0, 270), "up": (270, 0), "down": (90, 0)}.items()}}
    bs["conveyor_belt"] = horizontal_states(lambda p: "conveyor_belt")
    bs["oil_derrick"] = horizontal_states(lambda p: "oil_derrick", [("active", ["false", "true"])])
    bs["battery"] = horizontal_states(lambda p: "battery")
    bs["solar_panel"] = simple("solar_panel")
    bs["solar_panel_mono"] = simple("solar_panel_mono")
    bs["inverter"] = horizontal_states(lambda p: "inverter")
    bs["chip_fab"] = horizontal_states(lambda p: "chip_fab", [("active", ["false", "true"])])
    bs["cooling_unit"] = {"variants": {"active=false": {"model": ns("block/cooling_unit")},
                                       "active=true": {"model": ns("block/cooling_unit_active")}}}
    for name, j in bs.items():
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), j)
    return bs


# =============================================================================== language
BLOCK_NAMES = {
    # id: (english, german)
    "oil_shale": ("Oil Shale", "Ölschiefer"),
    "crude_oil": ("Crude Oil", "Rohöl"),
    "diesel": ("Diesel", "Diesel"),
    "shaft": ("Shaft", "Welle"),
    "gearbox": ("Gearbox", "Getriebe"),
    "wind_tower": ("Wind Tower", "Windradturm"),
    "tower_base": ("Tower Base", "Turmfuß"),
    "hand_crank": ("Hand Crank", "Handkurbel"),
    "windmill": ("Wind Turbine", "Windrad"),
    "diesel_engine": ("Diesel Engine", "Dieselmotor"),
    "generator": ("Dynamo Generator", "Dynamo-Generator"),
    "pumpjack": ("Pumpjack", "Pferdekopfpumpe"),
    "refinery": ("Distillation Column", "Raffinerie-Destillationskolonne"),
    "fluid_pipe": ("Fluid Pipe", "Rohrleitung"),
    "oil_tank": ("Oil Tank", "Öltank"),
    "power_cable": ("Power Cable", "Stromkabel"),
    "server_rack": ("AI Server Rack", "KI-Server-Rack"),
    "cooling_unit": ("Cooling Unit", "Kühlaggregat"),
    "ai_terminal": ("AI Training Terminal", "KI-Trainingsterminal"),
    "multiblock_part": ("Machine Part", "Maschinenteil"),
    "solar_panel": ("Solar Panel (poly)", "Solarmodul (poly)"),
    "solar_panel_mono": ("Solar Panel (mono)", "Solarmodul (mono)"),
    "inverter": ("Solar Inverter", "Wechselrichter"),
    "mechanical_pump": ("Mechanical Pump", "Mechanische Pumpe"),
    "battery": ("Battery Bank", "Akku-Block"),
    "conveyor_belt": ("Conveyor Belt", "Förderband"),
    "oil_derrick": ("Oil Derrick", "Ölbohrturm"),
    "chip_fab": ("Lithography Machine", "Lithografie-Maschine"),
}

ITEM_NAMES = {
    "wrench": ("Wrench", "Schraubenschlüssel"),
    "server_1u": ("Rack Server 1U", "Rack-Server 1U"),
    "server_2u": ("Rack Server 2U", "Rack-Server 2U"),
    "server_4u": ("Rack Server 4U", "Rack-Server 4U"),
    "crude_oil_bucket": ("Crude Oil Bucket", "Rohöleimer"),
    "diesel_bucket": ("Diesel Bucket", "Dieseleimer"),
    "plastic": ("Plastic Pellets", "Kunststoffgranulat"),
    "silicon": ("Silicon", "Silizium"),
    "silicon_wafer": ("Silicon Wafer", "Silizium-Wafer"),
    "circuit_board": ("Circuit Board", "Platine"),
    "gpu": ("AI Accelerator (GPU)", "KI-Beschleuniger (GPU)"),
    "empty_model_drive": ("Empty Model Drive", "Leeres KI-Laufwerk"),
    "trained_model_drive": ("Trained AI Model", "Trainiertes KI-Modell"),
    "ai_overclock_chip": ("AI Optimization Chip", "KI-Optimierungschip"),
}

MESSAGES = {
    # key: (english, german)
    "hw.kind.motherboard": ('Mainboard', 'Mainboard'),
    "hw.kind.cpu": ('CPU', 'CPU'),
    "hw.kind.ram": ('RAM', 'RAM'),
    "hw.kind.gpu": ('GPU / Accelerator', 'GPU / Beschleuniger'),
    "hw.kind.storage": ('Storage', 'Speicher'),
    "hw.kind.network": ('Network', 'Netzwerk'),
    "hw.kind.psu": ('Power supply', 'Netzteil'),
    "hw.kind.cooler": ('Cooling', 'Kühlung'),
    "hw.kind.motherboard.short": ('Board', 'Board'),
    "hw.kind.cpu.short": ('CPU', 'CPU'),
    "hw.kind.ram.short": ('RAM', 'RAM'),
    "hw.kind.gpu.short": ('GPU', 'GPU'),
    "hw.kind.storage.short": ('Disk', 'Disk'),
    "hw.kind.network.short": ('NIC', 'NIC'),
    "hw.kind.psu.short": ('PSU', 'PSU'),
    "hw.kind.cooler.short": ('Cool', 'Kühl'),
    "hw.status.0": ('OFF / IDLE', 'AUS / LEERLAUF'),
    "hw.status.1": ('ONLINE', 'ONLINE'),
    "hw.status.2": ('THROTTLED', 'GEDROSSELT'),
    "hw.status.3": ('OVERHEATED', 'ÜBERHITZT'),
    "hw.status.4": ('NO POWER', 'KEIN STROM'),
    "hw.status.5": ('CANNOT BOOT', 'BOOTET NICHT'),
    "hw.err.mobo_too_large": ('This mainboard needs a chassis of at least %sU.', 'Dieses Mainboard braucht ein Gehäuse mit mindestens %sU.'),
    "hw.err.needs_mobo": ('Install a mainboard first.', 'Zuerst ein Mainboard einbauen.'),
    "hw.err.cpu_socket": ('CPU socket does not match the mainboard.', 'CPU-Sockel passt nicht zum Mainboard.'),
    "hw.err.cpu_slots": ('The mainboard only has %s CPU socket(s).', 'Das Mainboard hat nur %s CPU-Sockel.'),
    "hw.err.ram_type": ('This RAM standard is not supported by the mainboard.', 'Dieser RAM-Standard wird vom Mainboard nicht unterstützt.'),
    "hw.err.ram_slots": ('All %s RAM slots are occupied.', 'Alle %s RAM-Steckplätze sind belegt.'),
    "hw.err.gpu_too_big": ('This card does not fit: it needs a chassis of at least %sU.', 'Die Karte passt nicht: Sie braucht ein Gehäuse mit mindestens %sU.'),
    "hw.err.pcie_slots": ('No free PCIe slot (the board has %s; GPUs and NICs share them).', 'Kein freier PCIe-Steckplatz (das Board hat %s; GPUs und NICs teilen sie).'),
    "hw.err.m2_slots": ('No free NVMe slot (the board has %s).', 'Kein freier NVMe-Steckplatz (das Board hat %s).'),
    "hw.err.bays": ('All %s drive bays of this chassis are full.', 'Alle %s Laufwerksschächte dieses Gehäuses sind belegt.'),
    "hw.err.psu_slots": ('At most %s power supplies fit.', 'Es passen höchstens %s Netzteile.'),
    "hw.err.psu_too_big": ('This power supply needs a chassis of at least %sU.', 'Dieses Netzteil braucht ein Gehäuse mit mindestens %sU.'),
    "hw.err.psu_weak": ('Power supply too weak: the hardware can draw up to %s W.', 'Netzteil zu schwach: Die Hardware kann bis zu %s W ziehen.'),
    "hw.err.cooler_too_big": ('This cooler needs a chassis of at least %sU.', 'Dieser Kühler braucht ein Gehäuse mit mindestens %sU.'),
    "hw.err.remove_dependents": ('Cannot remove this: other parts depend on it.', 'Entfernen nicht möglich: Andere Teile hängen davon ab.'),
    "hw.err.no_space": ('No room in the rack for a %sU server.', 'Im Rack ist kein Platz für einen %sU-Server.'),
    "hw.err.not_found": ('Nothing to do here.', 'Hier gibt es nichts zu tun.'),
    "hw.err.no_mobo": ('no mainboard', 'kein Mainboard'),
    "hw.err.no_cpu": ('no CPU', 'keine CPU'),
    "hw.err.no_ram": ('no RAM', 'kein RAM'),
    "hw.err.no_psu": ('no power supply', 'kein Netzteil'),
    "hw.err.no_cooler": ('no cooling', 'keine Kühlung'),
    "subtitles.crudecompute.rack_hum": ('Server rack whines', 'Server-Rack heult'),
    "hw.gui.fan_bay": ('Rear fan bay', 'Lüfterfach hinten'),
    "hw.gui.fan_tip": ('Click: mount fan (Shift: high-performance) / remove', 'Klick: Lüfter einbauen (Umschalt: Hochleistung) / entfernen'),
    "hw.gui.fan_empty": ('empty slot', 'leerer Schacht'),
    "hw.gui.fan_std": ('Rack fan 80 mm (12 W)', 'Rack-Lüfter 80 mm (12 W)'),
    "hw.gui.fan_hp": ('High-performance fan (36 W, loud)', 'Hochleistungs-Lüfter (36 W, laut)'),
    "tooltip.crudecompute.rack_fan": ('Slim 80 mm fan for the rear fan bay of a server rack. Blows the hot air upwards. 12 W.',
                                      'Schmaler 80-mm-Lüfter für das hintere Lüfterfach eines Server-Racks. Bläst die Warmluft nach oben. 12 W.'),
    "tooltip.crudecompute.rack_fan_hp": ('High-performance fan: 1.8x airflow, 36 W and louder.', 'Hochleistungs-Lüfter: 1,8-facher Luftstrom, 36 W und lauter.'),
    "item.crudecompute.rack_fan": ('Rack Fan', 'Rack-Lüfter'),
    "item.crudecompute.rack_fan_hp": ('High-Performance Rack Fan', 'Hochleistungs-Rack-Lüfter'),
    "hw.err.not_owned": ('You do not have this part in your inventory.', 'Dieses Teil hast du nicht im Inventar.'),
    "hw.item.install_hint": ('Install via the server rack screen.', 'Einbau über die Rack-Oberfläche.'),
    "itemGroup.crudecompute.hardware": ('Crude & Compute: Hardware', 'Crude & Compute: Hardware'),
    "hw.gui.title": ('Server Rack', 'Server-Rack'),
    "hw.gui.tab.rack": ('Rack', 'Rack'),
    "hw.gui.tab.energy": ('Power', 'Energie'),
    "hw.gui.search": ('Search…', 'Suchen…'),
    "hw.gui.click_open": ('Click to open', 'Klicken zum Öffnen'),
    "hw.gui.click_install": ('Click to install (uses one from your inventory)', 'Klicken zum Einbauen (verbraucht eins aus dem Inventar)'),
    "hw.gui.owned": ('In inventory: %s', 'Im Inventar: %s'),
    "hw.gui.only_compat": ('Fits', 'Passend'),
    "hw.gui.sort": ('Sort', 'Sort.'),
    "hw.gui.sort.price": ('price', 'Preis'),
    "hw.gui.sort.performance": ('power', 'Leistung'),
    "hw.gui.sort.name": ('name', 'Name'),
    "hw.gui.prio": ('Power priority', 'Strom-Priorität'),
    "hw.gui.prio.0": ('CRIT', 'KRIT'),
    "hw.gui.prio.1": ('NORM', 'NORM'),
    "hw.gui.prio.2": ('LOW', 'NIEDR'),
    "hw.gui.shed": ('%s server(s) shut down: not enough power', '%s Server aus: zu wenig Strom'),
    "hw.gui.draw_per_server": ('Power draw per server', 'Verbrauch pro Server'),
    "hw.gui.no_servers": ('No servers mounted.', 'Keine Server eingebaut.'),
    "hw.gui.supply": ('Power supply', 'Stromversorgung'),
    "hw.gui.runtime": ('Buffer lasts: %s min', 'Puffer reicht: %s min'),
    "hw.gui.psu_loss": ('PSU losses', 'Netzteil-Verlust'),
    "hw.gui.efficiency": ('Efficiency', 'Wirkungsgrad'),
    "hw.gui.history": ('Last 60 s   draw   heat', 'Letzte 60 s  Strom  Wärme'),
    "hw.gui.mode": ('Operating mode', 'Betriebsmodus'),
    "hw.gui.nothing": ('Nothing matches.', 'Nichts gefunden.'),
    "hw.gui.live": ('Live data', 'Live-Daten'),
    "hw.gui.done": ('Done.', 'Erledigt.'),
    "hw.gui.back": ('Rack', 'Rack'),
    "hw.gui.installed": ('Installed', 'Eingebaut'),
    "hw.gui.rack_title": ('Rack – %s of %s U occupied', 'Rack – %s von %s U belegt'),
    "hw.gui.rack_stats": ('Rack statistics', 'Rack-Statistik'),
    "hw.gui.power": ('Power draw', 'Stromaufnahme'),
    "hw.gui.heat": ('Waste heat', 'Abwärme'),
    "hw.gui.inlet": ('Inlet air', 'Zuluft'),
    "hw.gui.max_temp": ('Hottest server', 'Heißester Server'),
    "hw.gui.cooling_load": ('Cooling load', 'Kühllast'),
    "hw.gui.compute": ('Compute', 'Leistung'),
    "hw.gui.buffer": ('Energy buffer', 'Energiepuffer'),
    "hw.gui.status": ('Status', 'Status'),
    "hw.gui.insert_held": ('Mount held server', 'Gehaltenen Server einbauen'),
    "hw.gui.empty_chassis": ('empty chassis', 'leeres Gehäuse'),
    "hw.gui.server_title": ('Server %sU at U%s', 'Server %sU bei U%s'),
    "hw.gui.cpu": ('CPU', 'CPU'),
    "hw.gui.gpu": ('GPU', 'GPU'),
    "hw.gui.disk": ('Disk', 'Disk'),
    "hw.gui.fan": ('Fans: %s rpm', 'Lüfter: %s U/min'),
    "hw.gui.mode.0": ('OFF', 'AUS'),
    "hw.gui.mode.1": ('IDLE', 'IDLE'),
    "hw.gui.mode.2": ('NORM', 'NORM'),
    "hw.gui.mode.3": ('FULL', 'VOLL'),
    "hw.rack.summary": ('Rack: %s servers (%s/%s U) | %s kW heat | %s °C | %s', 'Rack: %s Server (%s/%s U) | %s kW Abwärme | %s °C | %s'),
    "hw.item.size": ('%sU rack server', '%sU-Rack-Server'),
    "hw.item.empty": ('Empty chassis – install parts in a rack', 'Leeres Gehäuse – Teile im Rack einbauen'),
    "tooltip.crudecompute.server_rack": ('14U rack. Right-click with a server to mount it, empty hand opens the management UI, sneak shows a summary.', '14U-Rack. Rechtsklick mit Server baut ihn ein, leere Hand öffnet die Verwaltung, Schleichen zeigt eine Kurzinfo.'),
    "tooltip.crudecompute.solar_panel": ('Produces power in daylight (polycrystalline, 15 %). Needs sky access and a touching inverter.', 'Erzeugt bei Tageslicht Strom (polykristallin, 15 %). Braucht freien Himmel und einen anliegenden Wechselrichter.'),
    "tooltip.crudecompute.solar_panel_mono": ('Monocrystalline, 22 % efficiency. Needs sky access and a touching inverter.', 'Monokristallin, 22 % Wirkungsgrad. Braucht freien Himmel und einen anliegenden Wechselrichter.'),
    "tooltip.crudecompute.inverter": ('Collects all touching solar panels (even in chains) and feeds the front side with 96 % efficiency.', 'Sammelt alle anliegenden Solarmodule (auch in Ketten) und speist vorne mit 96 % Wirkungsgrad ein.'),
    "message.crudecompute.inverter": ('Inverter: %s panels | sun %s %% | %s kW DC → %s kW AC | buffer %s FE', 'Wechselrichter: %s Module | Sonne %s %% | %s kW DC → %s kW AC | Puffer %s FE'),
    "dash.title": ('DATACENTER CONTROL', 'RECHENZENTRUM-STEUERUNG'),
    "dash.solar": ('Solar', 'Solar'),
    "dash.panels": ('panels', 'Module'),
    "dash.consumption": ('Consumption', 'Verbrauch'),
    "dash.cooling_part": ('cooling %s kW', 'Kühlung %s kW'),
    "dash.balance": ('Balance', 'Bilanz'),
    "dash.surplus": ('surplus', 'Überschuss'),
    "dash.deficit": ('shortfall', 'Mangel'),
    "dash.battery": ('Battery', 'Akku'),
    "dash.no_battery": ('no battery found', 'kein Akku gefunden'),
    "dash.chart_power": ('Power: solar (green) vs. consumption (red)', 'Leistung: Solar (grün) vs. Verbrauch (rot)'),
    "dash.chart_temp": ('Hottest server (last 2 min)', 'Heißester Server (2 min)'),
    "dash.datacenter": ('Datacenter', 'Rechenzentrum'),
    "dash.racks": ('Racks: %s  GPUs: %s', 'Racks: %s  GPUs: %s'),
    "dash.compute": ('Compute: %s PFLOPS', 'Rechenleistung: %s PFLOPS'),
    "dash.pue": ('PUE: %s', 'PUE: %s'),
    "dash.temps": ('Max %s °C | inlet %s °C', 'Max %s °C | Zuluft %s °C'),
    "dash.training": ('AI model training', 'KI-Modelltraining'),
    "dash.no_drive": ('insert a model drive', 'Laufwerk einlegen'),
    "dash.done": ('MODEL READY', 'MODELL FERTIG'),
    "dash.running": ('training... %s %%', 'Training... %s %%'),
    "dash.eject": ('Eject drive', 'Laufwerk auswerfen'),
    "itemGroup.crudecompute": ("Crude & Compute", "Crude & Compute"),
    "fluid_type.crudecompute.crude_oil": ("Crude Oil", "Rohöl"),
    "fluid_type.crudecompute.diesel": ("Diesel", "Diesel"),
    "message.crudecompute.kinetic": ("%s: %s RPM | Stress %s / %s SU", "%s: %s U/min | Last %s / %s SU"),
    "message.crudecompute.overstressed": ("%s: OVERSTRESSED – the network needs %s SU but only has %s SU!",
                                          "%s: ÜBERLASTET – das Netz braucht %s SU, hat aber nur %s SU!"),
    "message.crudecompute.no_space": ("Not enough space to build this machine.", "Nicht genug Platz für diese Maschine."),
    "message.crudecompute.pumpjack": ("Pumpjack: %s RPM | oil deposits below: %s | tank %s / %s mB",
                                      "Pferdekopfpumpe: %s U/min | Ölvorkommen darunter: %s | Tank %s / %s mB"),
    "message.crudecompute.pumpjack.no_oil": ("No oil below the wellhead! Oil shale must lie in the column under the front of the pumpjack.",
                                             "Kein Öl unter dem Bohrkopf! Unter der Vorderseite der Pumpe muss Ölschiefer liegen."),
    "message.crudecompute.refinery": ("Refinery: %s RPM | crude %s mB | diesel %s mB | plastic %s",
                                      "Raffinerie: %s U/min | Rohöl %s mB | Diesel %s mB | Kunststoff %s"),
    "message.crudecompute.engine.running": ("Diesel engine running | fuel %s / %s mB", "Dieselmotor läuft | Treibstoff %s / %s mB"),
    "message.crudecompute.engine.empty": ("Diesel engine out of fuel – fill it with diesel (bucket or pipe).",
                                          "Dieselmotor ohne Treibstoff – mit Diesel befüllen (Eimer oder Rohr)."),
    "message.crudecompute.windmill": ("Wind turbine (wind %s RPM, more higher up and in storms)",
                                      "Windrad (Wind %s U/min, mehr in der Höhe und bei Unwetter)"),
    "message.crudecompute.windmill.blocked": ("Wind turbine needs open sky above it.", "Das Windrad braucht freien Himmel über sich."),
    "message.crudecompute.generator": ("Generator: %s RPM → %s FE/t | buffer %s / %s FE",
                                       "Generator: %s U/min → %s FE/t | Speicher %s / %s FE"),
    "message.crudecompute.tank": ("Oil tank: %s %s / %s mB", "Öltank: %s %s / %s mB"),
    "message.crudecompute.tank.empty": ("Oil tank: empty (capacity %s mB)", "Öltank: leer (Kapazität %s mB)"),
    "message.crudecompute.pipe": ("Pipe: %s %s mB", "Rohr: %s %s mB"),
    "message.crudecompute.pipe.empty": ("Pipe: empty", "Rohr: leer"),
    "message.crudecompute.cable": ("Power network: %s cables (loss %s%%) | in %s kW, out %s kW | %s sources, %s consumers, %s batteries (%s kW)",
                                   "Stromnetz: %s Kabel (Verlust %s %%) | Erzeugung %s kW, Verbrauch %s kW | %s Quellen, %s Verbraucher, %s Akkus (%s kW)"),
    "message.crudecompute.cable.idle": ("Cable: not connected to anything yet.", "Kabel: noch mit nichts verbunden."),
    "message.crudecompute.rack": ("Server rack: %s GPUs | %s °C | %s | energy %s / %s FE | cooling %s °C/s",
                                  "Server-Rack: %s GPUs | %s °C | %s | Energie %s / %s FE | Kühlung %s °C/s"),
    "message.crudecompute.rack.online": ("ONLINE", "ONLINE"),
    "message.crudecompute.rack.throttled": ("THROTTLED (too hot)", "GEDROSSELT (zu heiß)"),
    "message.crudecompute.rack.overheated": ("OVERHEATED – shut down", "ÜBERHITZT – abgeschaltet"),
    "message.crudecompute.rack.no_power": ("NO POWER", "KEIN STROM"),
    "message.crudecompute.rack.idle": ("IDLE (no GPUs)", "LEERLAUF (keine GPUs)"),
    "message.crudecompute.rack.full": ("All 8 GPU bays are occupied.", "Alle 8 GPU-Einschübe sind belegt."),
    "message.crudecompute.cooler": ("Cooling unit: %s | cooling %s rack(s)", "Kühlaggregat: %s | kühlt %s Rack(s)"),
    "message.crudecompute.cooler.on": ("running", "läuft"),
    "message.crudecompute.cooler.off": ("no power", "kein Strom"),
    "message.crudecompute.terminal": ("AI terminal: %s racks, %s GPUs, %s PFLOPS | %s",
                                      "KI-Terminal: %s Racks, %s GPUs, %s PFLOPS | %s"),
    "message.crudecompute.terminal.no_drive": ("insert an Empty Model Drive to start training",
                                               "Leeres KI-Laufwerk einlegen, um das Training zu starten"),
    "message.crudecompute.terminal.training": ("training %s%%", "Training %s%%"),
    "message.crudecompute.terminal.done": ("model trained – take it out!", "Modell fertig trainiert – herausnehmen!"),
    "message.crudecompute.chip.installed": ("AI optimization installed – this machine now works twice as fast!",
                                            "KI-Optimierung installiert – diese Maschine arbeitet jetzt doppelt so schnell!"),
    "message.crudecompute.chip.already": ("This machine is already AI-optimized.", "Diese Maschine ist bereits KI-optimiert."),
    "message.crudecompute.optimized": (" [AI-optimized]", " [KI-optimiert]"),
    "screen.crudecompute.title": ("AI DATACENTER", "KI-RECHENZENTRUM"),
    "screen.crudecompute.racks": ("Racks: %s  GPUs: %s", "Racks: %s  GPUs: %s"),
    "screen.crudecompute.compute": ("Compute: %s PFLOPS", "Rechenleistung: %s PFLOPS"),
    "screen.crudecompute.power": ("Power: %s FE/t  Max %s °C", "Strom: %s FE/t  Max %s °C"),
    "screen.crudecompute.no_drive": ("> insert model drive", "> KI-Laufwerk einlegen"),
    "screen.crudecompute.training": ("> training model... %s%%", "> trainiere Modell... %s%%"),
    "screen.crudecompute.done": ("> MODEL READY", "> MODELL FERTIG"),
    "screen.crudecompute.offline": ("> no compute available", "> keine Rechenleistung"),
    "screen.crudecompute.chatter.0": ("> optimizing drill plan", "> optimiere Bohrplan"),
    "screen.crudecompute.chatter.1": ("> forecasting oil price", "> prognostiziere Ölpreis"),
    "screen.crudecompute.chatter.2": ("> tuning refinery PID", "> justiere Raffinerie-PID"),
    "screen.crudecompute.chatter.3": ("> scaling transformer", "> skaliere Transformer"),
    "screen.crudecompute.chatter.4": ("> backprop in progress", "> Backpropagation läuft"),
    "screen.crudecompute.chatter.5": ("> mining creeper data", "> analysiere Creeper-Daten"),
    "screen.crudecompute.temp": ("%s °C", "%s °C"),
    "message.crudecompute.pump": ("Mechanical pump: %s RPM → up to %s mB/t%s", "Mechanische Pumpe: %s U/min → bis zu %s mB/t%s"),
    "message.crudecompute.pump.hint": (" (pumps from the back to the arrow side)", " (pumpt von hinten zur Pfeilseite)"),
    "message.crudecompute.battery": ("Battery bank: %s / %s FE (%s%%) – output on the front", "Akku-Block: %s / %s FE (%s%%) – Ausgang vorne"),
    "message.crudecompute.chip_fab": ("Lithography: %s RPM | wafers %s, plastic %s, gold nuggets %s | circuit boards %s | %s%%",
                                      "Lithografie: %s U/min | Wafer %s, Kunststoff %s, Goldnuggets %s | Platinen %s | %s%%"),
    "message.crudecompute.chip_fab.missing": ("Needs a silicon wafer, plastic pellets and a gold nugget per run.",
                                              "Braucht pro Durchgang einen Silizium-Wafer, Kunststoffgranulat und ein Goldnugget."),
    "tooltip.crudecompute.conveyor_belt": ("Moves items towards its front. Shafts connect at the sides (4 SU). Chains with other belts, feeds chests and hoppers; accepts dropped items and hoppers.",
                                           "Befördert Items nach vorne. Wellen seitlich (4 SU). Reiht sich an andere Bänder, füttert Kisten und Trichter; nimmt Wurf-Items und Trichter an."),
    "tooltip.crudecompute.oil_derrick": ("3×3×7 tower. Drills crude oil under its drill pipe – 3× a pumpjack. Needs rotation (128 SU) from behind.",
                                         "3×3×7-Turm. Fördert Rohöl unter dem Bohrstrang – 3× so viel wie die Pferdekopfpumpe. Braucht Drehung (128 SU) von hinten."),
    "message.crudecompute.derrick": ("Derrick: %s RPM | oil deposits below: %s | tank %s / %s mB", "Bohrturm: %s U/min | Ölvorkommen darunter: %s | Tank %s / %s mB"),
    "message.crudecompute.belt": ("Belt: %s items | %s RPM", "Band: %s Items | %s U/min"),
    "tooltip.crudecompute.mechanical_pump": ("Driven by a shaft from the side: pumps fluid from the back to the front (arrow). 16 SU.",
                                             "Per Welle von der Seite angetrieben: pumpt Flüssigkeit von hinten nach vorne (Pfeil). 16 SU."),
    "tooltip.crudecompute.battery": ("Stores 2,000,000 FE. Charges on every side, outputs on the front.",
                                     "Speichert 2.000.000 FE. Lädt an allen Seiten, gibt vorne ab."),
    "tooltip.crudecompute.chip_fab": ("Etches circuit boards: wafer + plastic + gold nugget → 2 boards. Needs rotation (64 SU) from below or behind.",
                                      "Belichtet Platinen: Wafer + Kunststoff + Goldnugget → 2 Platinen. Braucht Drehung (64 SU) von unten oder hinten."),
    "tooltip.crudecompute.shaft": ("Transmits rotation along its axis.", "Überträgt Drehbewegung entlang der Achse."),
    "tooltip.crudecompute.gearbox": ("Connects shafts on all six sides.", "Verbindet Wellen an allen sechs Seiten."),
    "tooltip.crudecompute.hand_crank": ("Right-click to crank: 32 RPM, 64 SU.", "Rechtsklick zum Kurbeln: 32 U/min, 64 SU."),
    "tooltip.crudecompute.wind_tower": ("Stackable tower segment with an axle inside. Put a wind turbine on top, stack higher for stronger wind.",
                                        "Stapelbares Turmsegment mit Welle im Inneren. Windrad oben aufsetzen, höher bauen = mehr Wind."),
    "tooltip.crudecompute.tower_base": ("Foundation: leads the tower axle out to the four sides (to a generator).",
                                        "Fundament: führt die Turmwelle zu den vier Seiten (zum Generator)."),
    "tooltip.crudecompute.windmill": ("Place on top of a vertical shaft. 16-32 RPM, 256 SU.",
                                      "Auf eine senkrechte Welle setzen. 16-32 U/min, 256 SU."),
    "tooltip.crudecompute.diesel_engine": ("Burns diesel: 64 RPM, 1024 SU. Output shaft at the flywheel.",
                                           "Verbrennt Diesel: 64 U/min, 1024 SU. Abtrieb am Schwungrad."),
    "tooltip.crudecompute.generator": ("Turns rotation into Forge Energy (FE). Needs 128 SU.",
                                       "Wandelt Drehbewegung in Forge Energy (FE) um. Braucht 128 SU."),
    "tooltip.crudecompute.pumpjack": ("3×3 machine. Pumps crude oil from oil shale below the wellhead. Needs rotation (48 SU) at the gearbox side.",
                                      "3×3-Maschine. Pumpt Rohöl aus Ölschiefer unter dem Bohrkopf. Braucht Drehung (48 SU) seitlich am Getriebe."),
    "tooltip.crudecompute.refinery": ("3 blocks tall. Refines crude oil into diesel and plastic. Needs rotation (96 SU) at the base.",
                                      "3 Blöcke hoch. Raffiniert Rohöl zu Diesel und Kunststoff. Braucht Drehung (96 SU) am Sockel."),
    "tooltip.crudecompute.fluid_pipe": ("Moves fluids between machines and tanks.", "Transportiert Flüssigkeiten zwischen Maschinen und Tanks."),
    "tooltip.crudecompute.oil_tank": ("Stores 16 buckets. Drains downwards.", "Speichert 16 Eimer. Gibt nach unten ab."),
    "tooltip.crudecompute.power_cable": ("Transports Forge Energy. Plugs into machines, the inverter and solar panels (DC link).", "Leitet Forge Energy. Steckt per Stecker an Maschinen, Wechselrichter und Solarpanels (DC-Anbindung)."),
    "tooltip.crudecompute.server_rack": ("2 blocks tall. Right-click with GPUs (up to 8). Needs FE and cooling.",
                                         "2 Blöcke hoch. Mit GPUs rechtsklicken (bis zu 8). Braucht FE und Kühlung."),
    "tooltip.crudecompute.cooling_unit": ("Cools server racks within 2 blocks. Uses 16 FE/t.",
                                          "Kühlt Server-Racks im Umkreis von 2 Blöcken. Verbraucht 16 FE/t."),
    "tooltip.crudecompute.ai_terminal": ("Shows the datacenter within 8 blocks and trains AI models.",
                                         "Zeigt das Rechenzentrum im Umkreis von 8 Blöcken und trainiert KI-Modelle."),
    "tooltip.crudecompute.wrench": ("Right-click: rotate. Sneak + right-click: pick up machine.",
                                    "Rechtsklick: drehen. Schleichen + Rechtsklick: Maschine abbauen."),
    "tooltip.crudecompute.ai_overclock_chip": ("Right-click a pumpjack, refinery, generator or diesel engine to double its output.",
                                               "Auf Pferdekopfpumpe, Raffinerie, Generator oder Dieselmotor anwenden: doppelte Leistung."),
    "tooltip.crudecompute.empty_model_drive": ("Insert into an AI Training Terminal.", "In ein KI-Trainingsterminal einlegen."),
    "tooltip.crudecompute.trained_model_drive": ("Contains a fully trained AI. Craft AI Optimization Chips from it.",
                                                 "Enthält eine fertig trainierte KI. Daraus werden KI-Optimierungschips hergestellt."),
    "tooltip.crudecompute.gpu": ("Insert into an AI Server Rack.", "In ein KI-Server-Rack einsetzen."),
}


def lang():
    en, de = {}, {}
    for k, (e, d) in BLOCK_NAMES.items():
        en[f"block.{MODID}.{k}"] = e
        de[f"block.{MODID}.{k}"] = d
    for k, (e, d) in ITEM_NAMES.items():
        en[f"item.{MODID}.{k}"] = e
        de[f"item.{MODID}.{k}"] = d
    for k, (e, d) in MESSAGES.items():
        en[k] = e
        de[k] = d
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang", "de_de.json"), de)


# =============================================================================== data
DROPS_SELF = ["solar_panel", "solar_panel_mono", "inverter", "conveyor_belt", "oil_derrick", "mechanical_pump", "battery", "chip_fab", "oil_shale", "shaft", "gearbox", "hand_crank", "windmill", "wind_tower", "tower_base", "diesel_engine", "generator", "pumpjack",
              "refinery", "fluid_pipe", "oil_tank", "power_cable", "server_rack", "cooling_unit", "ai_terminal"]


def loot_tables():
    for b in DROPS_SELF:
        write_json(os.path.join(DATA, "loot_table", "blocks", f"{b}.json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": ns(b)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": ns(f"blocks/{b}"),
        })


def shaped(name, pattern, key, result=None, count=1):
    keys = {}
    for k, v in key.items():
        keys[k] = {"tag": v[1:]} if v.startswith("#") else {"item": v}
    write_json(os.path.join(DATA, "recipe", f"{name}.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": pattern,
        "key": keys,
        "result": {"id": ns(result or name), "count": count},
    })


def shapeless(name, ingredients, result=None, count=1):
    write_json(os.path.join(DATA, "recipe", f"{name}.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ingredients],
        "result": {"id": ns(result or name), "count": count},
    })


DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"]


def hardware_recipes():
    """Every part is crafted shapeless: kind base + tier extras (by price) + one dye that selects the model."""
    import hwicons
    B, P, W = ns("circuit_board"), ns("plastic"), ns("silicon_wafer")
    base = {"cpu": [W, "minecraft:gold_nugget"], "ram": [B, P], "motherboard": [B, B, "minecraft:copper_ingot"],
            "gpu": [B, B, P], "storage": [B, P, "minecraft:redstone"], "network": [B, "minecraft:copper_ingot", "minecraft:redstone"],
            "psu": ["minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:redstone", B],
            "cooler": ["minecraft:iron_ingot", "minecraft:copper_ingot", P]}
    extras = [[], ["minecraft:quartz"], ["minecraft:gold_ingot"], ["minecraft:diamond"], ["minecraft:diamond", "minecraft:netherite_scrap"]]
    index = {}
    for part in hwicons.parse_catalog():
        price = int(part["args"][-1])
        tier = 0 if price < 200 else 1 if price < 700 else 2 if price < 2500 else 3 if price < 10000 else 4
        i = index.get(part["kind"], 0)
        index[part["kind"]] = i + 1
        assert i < len(DYES), f"too many {part['kind']} parts for the dye selector"
        shapeless(f"hw_{part['id']}", base[part["kind"]] + extras[tier] + [f"minecraft:{DYES[i]}_dye"])


def recipes():
    hardware_recipes()
    I, C, G = "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot"
    P = ns("plastic")
    B = ns("circuit_board")
    S = ns("shaft")
    shaped("shaft", ["I", "I"], {"I": I}, count=4)
    shaped("gearbox", [" S ", "SIS", " S "], {"S": S, "I": "minecraft:iron_block"})
    shaped("hand_crank", ["WWW", "  S"], {"W": "#minecraft:planks", "S": S})
    shaped("wind_tower", ["I I", "ISI", "I I"], {"I": I, "S": S}, count=2)
    shaped("tower_base", [" S ", "IGI", "BBB"], {"I": I, "S": ns("wind_tower"), "G": ns("gearbox"), "B": "minecraft:stone_bricks"})
    shaped("rack_fan", ["IPI", "PRP", "IPI"], {"I": I, "P": P, "R": "minecraft:redstone"})
    shaped("rack_fan_hp", ["GPG", "PFP", "GPG"], {"G": G, "P": P, "F": ns("rack_fan")})
    shaped("windmill", [" W ", "WSW", " I "], {"W": "minecraft:white_wool", "S": S, "I": I})
    shaped("diesel_engine", ["IPI", "CFC", "ISI"], {"I": I, "P": "minecraft:piston", "C": C, "F": "minecraft:blast_furnace", "S": S})
    shaped("generator", ["CRC", "CSC", "III"], {"C": C, "R": "minecraft:redstone_block", "S": S, "I": I})
    shaped("pumpjack", ["III", "PSP", "IBI"], {"I": I, "P": "minecraft:piston", "S": S, "B": "minecraft:bucket"})
    shaped("refinery", ["ICI", "IBI", "IFI"], {"I": I, "C": C, "B": "minecraft:bucket", "F": "minecraft:blast_furnace"})
    shaped("fluid_pipe", ["CCC"], {"C": C}, count=8)
    shaped("oil_tank", ["ICI", "I I", "ICI"], {"I": I, "C": C})
    shaped("power_cable", ["PCP"], {"P": P, "C": C}, count=6)
    shaped("server_rack", ["IBI", "ICI", "IBI"], {"I": I, "B": "minecraft:iron_bars", "C": B})
    shaped("cooling_unit", ["IPI", "PKP", "ICI"], {"I": I, "P": P, "K": "minecraft:packed_ice", "C": B})
    shaped("ai_terminal", ["GGG", "PCP", "PPP"], {"G": "minecraft:glass_pane", "P": P, "C": B})
    shaped("server_1u", ["III", "PBP"], {"I": I, "P": P, "B": B})
    shaped("server_2u", ["III", "PBP", "III"], {"I": I, "P": P, "B": B})
    shaped("server_4u", ["IGI", "PBP", "IGI"], {"I": I, "P": P, "B": B, "G": ns("gpu")})
    shaped("solar_panel", ["GGG", "SSS", "III"], {"G": "minecraft:glass_pane", "S": ns("silicon"), "I": I}, count=2)
    shaped("solar_panel_mono", ["GGG", "WWW", "CCC"], {"G": "minecraft:glass_pane", "W": ns("silicon_wafer"), "C": C}, count=2)
    shaped("inverter", ["ICI", "BRB", "III"], {"I": I, "C": C, "B": B, "R": "minecraft:redstone_block"})
    shaped("conveyor_belt", ["RRR", "ISI"], {"R": "minecraft:black_wool", "I": I, "S": S}, count=4)
    shaped("oil_derrick", ["ISI", "IWI", "PDP"], {"I": I, "S": S, "W": ns("gearbox"), "D": "minecraft:diamond", "P": ns("fluid_pipe")})
    shaped("mechanical_pump", ["CPC", " S "], {"C": C, "P": ns("fluid_pipe"), "S": S})
    shaped("battery", ["PRP", "CBC", "PRP"], {"P": P, "R": "minecraft:redstone_block", "C": C, "B": "minecraft:iron_block"})
    shaped("chip_fab", ["GRG", "IDI", "ISI"], {"G": "minecraft:glass_pane", "R": "minecraft:redstone_lamp", "D": "minecraft:diamond",
                                             "I": I, "S": S})
    shaped("wrench", [" I ", " GI", "I  "], {"I": I, "G": G})
    shapeless("silicon_wafer", [ns("silicon")], count=2)
    shaped("circuit_board", ["GRG", "WWW", "PPP"], {"G": "minecraft:gold_nugget", "R": "minecraft:redstone", "W": ns("silicon_wafer"), "P": P}, count=2)
    shaped("gpu", ["CBC", "BDB", "PPP"], {"C": C, "B": B, "D": "minecraft:diamond", "P": P})
    shaped("empty_model_drive", ["PRP", "PBP", "PPP"], {"P": P, "R": "minecraft:redstone", "B": B})
    shaped("ai_overclock_chip", ["GRG", "RTR", "GRG"], {"G": G, "R": "minecraft:redstone", "T": ns("trained_model_drive")}, count=4)
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write_json(os.path.join(DATA, "recipe", f"silicon_from_{kind}.json"), {
            "type": f"minecraft:{kind}",
            "category": "misc",
            "ingredient": {"item": "minecraft:quartz"},
            "result": {"id": ns("silicon")},
            "experience": 0.3,
            "cookingtime": time,
        })


def tags():
    pickaxe = [ns(b) for b in DROPS_SELF if b not in ("hand_crank",)] + [ns("multiblock_part")]
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": pickaxe})
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
               {"replace": False, "values": [ns("hand_crank")]})
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "needs_stone_tool.json"),
               {"replace": False, "values": [ns("oil_shale")]})


def worldgen():
    write_json(os.path.join(DATA, "worldgen", "configured_feature", "oil_shale.json"), {
        "type": "minecraft:ore",
        "config": {
            "size": 48,
            "discard_chance_on_air_exposure": 0.0,
            "targets": [
                {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
                 "state": {"Name": ns("oil_shale")}},
                {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
                 "state": {"Name": ns("oil_shale")}},
            ],
        },
    })
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "oil_shale.json"), {
        "feature": ns("oil_shale"),
        "placement": [
            {"type": "minecraft:count", "count": 4},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": -48},
                                                          "max_inclusive": {"absolute": 40}}},
            {"type": "minecraft:biome"},
        ],
    })
    write_json(os.path.join(DATA, "worldgen", "configured_feature", "oil_lake.json"), {
        "type": "minecraft:lake",
        "config": {
            "fluid": {"type": "minecraft:simple_state_provider", "state": {"Name": ns("crude_oil"), "Properties": {"level": "0"}}},
            "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": ns("oil_shale")}},
        },
    })
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "oil_lake.json"), {
        "feature": ns("oil_lake"),
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 70},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
        ],
    })
    write_json(os.path.join(DATA, "neoforge", "biome_modifier", "add_oil_shale.json"), {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": ns("oil_shale"),
        "step": "underground_ores",
    })
    write_json(os.path.join(DATA, "neoforge", "biome_modifier", "add_oil_lakes.json"), {
        "type": "neoforge:add_features",
        "biomes": ["minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
                   "minecraft:savanna", "minecraft:plains", "minecraft:snowy_plains"],
        "features": ns("oil_lake"),
        "step": "lakes",
    })


# ---------------------------------------------------------------- tiny NBT writer (game test structure)
def _nbt_payload(tag_type, value):
    if tag_type == "int":
        return struct.pack(">i", value)
    if tag_type == "string":
        b = value.encode("utf-8")
        return struct.pack(">H", len(b)) + b
    raise ValueError(tag_type)


def _nbt_named(type_id, name, payload):
    b = name.encode("utf-8")
    return bytes([type_id]) + struct.pack(">H", len(b)) + b + payload


def _nbt_compound(entries):
    return b"".join(entries) + b"\x00"


def empty_structure(size):
    palette_entry = _nbt_compound([_nbt_named(8, "Name", _nbt_payload("string", "minecraft:air"))])
    body = _nbt_compound([
        _nbt_named(3, "DataVersion", _nbt_payload("int", 3955)),
        _nbt_named(9, "size", bytes([3]) + struct.pack(">i", 3) + b"".join(struct.pack(">i", s) for s in size)),
        _nbt_named(9, "palette", bytes([10]) + struct.pack(">i", 1) + palette_entry),
        _nbt_named(9, "blocks", bytes([10]) + struct.pack(">i", 0)),
        _nbt_named(9, "entities", bytes([10]) + struct.pack(">i", 0)),
    ])
    root = _nbt_named(10, "", body)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(root)
    path = os.path.join(DATA, "structure", "empty.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(buf.getvalue())


def showcase(names, imgs):
    """docs/modelle.png: labelled overview of all machines for the README."""
    from PIL import Image, ImageDraw, ImageFont
    cell, cols = 240, 5
    rows = math.ceil(len(imgs) / cols)
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 34) + 70), (30, 33, 40, 255))
    draw = ImageDraw.Draw(sheet)
    def load(size, bold=False):
        for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                     "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"):
            if os.path.exists(path):
                return ImageFont.truetype(path, size)
        return ImageFont.load_default(size=size)

    title_font = load(32, bold=True)
    font = load(17)
    draw.text((20, 16), "Crude & Compute – frei geformte 3D-Modelle", font=title_font, fill=(255, 196, 60, 255))
    for i, (name, img) in enumerate(zip(names, imgs)):
        x, y = (i % cols) * cell, 70 + (i // cols) * (cell + 34)
        sheet.alpha_composite(img.resize((cell, cell), Image.LANCZOS), (x, y))
        label = BLOCK_NAMES[name][1]
        w = draw.textlength(label, font=font)
        draw.text((x + (cell - w) / 2, y + cell + 4), label, font=font, fill=(220, 226, 236, 255))
    out = os.path.join(HERE, "..", "docs", "modelle.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.convert("RGB").save(out, optimize=True)


# =============================================================================== main
def main(preview=False):
    tex_block = T.block_textures()
    tex_item = T.item_textures()
    for name, img in tex_block.items():
        p = os.path.join(ASSETS, "textures", "block", f"{name}.png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
    for name, img in tex_item.items():
        p = os.path.join(ASSETS, "textures", "item", f"{name}.png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)

    # item icons of the rack fans (rendered from their 3D models)
    from preview import render as _render
    for name, hp in (("rack_fan", False), ("rack_fan_hp", True)):
        icon = _render(M.rack_fan_frame(hp).add(M.rack_fan_rotor(hp)).scale(5.0), tex_block, size=32, yaw=148, pitch=52, supersample=6)
        icon.save(os.path.join(ASSETS, "textures", "item", f"{name}.png"))
        os.makedirs(os.path.join(ASSETS, "models", "item"), exist_ok=True)
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns(f"item/{name}")}})
    write_json(os.path.join(ASSETS, "sounds.json"),
               {"rack_hum": {"subtitle": "subtitles.crudecompute.rack_hum",
                             "sounds": [{"name": "crudecompute:rack_hum", "attenuation_distance": 28}]}})

    used = set()
    for name, (factory, particle) in BLOCK_MODELS.items():
        mesh = factory()
        used.update(mesh.materials())
        write_obj_model("block", name, mesh, particle)
    for name, (factory, particle) in ITEM_MODELS.items():
        mesh = factory().fit_unit_cube()
        used.update(mesh.materials())
        write_obj_model("item", name, mesh, particle, item=True)
    missing = used - set(tex_block)
    if missing:
        raise SystemExit(f"materials without texture: {sorted(missing)}")

    # simple json models
    bm = os.path.join(ASSETS, "models", "block")
    write_json(os.path.join(bm, "oil_shale.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": ns("block/oil_shale")}})
    write_json(os.path.join(bm, "crude_oil.json"), {"textures": {"particle": "minecraft:block/black_concrete"}})
    write_json(os.path.join(bm, "diesel.json"), {"textures": {"particle": "minecraft:block/yellow_concrete"}})
    write_json(os.path.join(bm, "multiblock_part.json"), {"textures": {"particle": ns("block/steel")}})
    write_json(os.path.join(bm, "shaft_particle.json"), {"textures": {"particle": ns("block/steel")}})
    im = os.path.join(ASSETS, "models", "item")
    write_json(os.path.join(im, "oil_shale.json"), {"parent": ns("block/oil_shale")})
    for name in FLAT_ITEMS:
        write_json(os.path.join(im, f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": ns(f"item/{name}")}})
    hardware_assets(im)
    write_json(os.path.join(im, "wrench.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": ns("item/wrench")}})

    blockstates()
    lang()
    loot_tables()
    recipes()
    tags()
    worldgen()
    empty_structure((12, 12, 12))

    # mod logo for the mods list
    from preview import render
    logo = render(pumpjack_full(), tex_block, size=256, yaw=-40, pitch=22)
    logo.save(os.path.join(ROOT, "crudecompute_logo.png"))

    if preview:
        out = os.path.join(HERE, "preview")
        os.makedirs(out, exist_ok=True)
        from PIL import Image
        names = list(ITEM_MODELS)
        imgs = []
        for name in names:
            mesh = ITEM_MODELS[name][0]()
            img = render(mesh, tex_block, size=256)
            img.save(os.path.join(out, f"{name}.png"))
            imgs.append(img)
        cols = 5
        rows = math.ceil(len(imgs) / cols)
        sheet = Image.new("RGBA", (cols * 256, rows * 256), (40, 44, 52, 255))
        for i, img in enumerate(imgs):
            sheet.alpha_composite(img, ((i % cols) * 256, (i // cols) * 256))
        sheet.save(os.path.join(out, "_all_models.png"))
        showcase(names, imgs)
        icons = Image.new("RGBA", (len(tex_item) * 72, 72), (40, 44, 52, 255))
        for i, (name, img) in enumerate(tex_item.items()):
            icons.alpha_composite(img.resize((64, 64), Image.NEAREST), (i * 72 + 4, 4))
        icons.save(os.path.join(out, "_items.png"))
    print("assets generated")


if __name__ == "__main__":
    main(preview="--preview" in sys.argv)
