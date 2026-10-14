"""Icons for the hardware parts: small 3D models (CPU, RAM, mainboard, GPU, ...) in brand colours, rendered to item textures."""

import math
import re
import os

from PIL import Image

from meshlib import Mesh, box, cylinder_y, tube
from preview import render

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG_JAVA = os.path.join(HERE, "..", "src", "main", "java", "com", "crudecompute", "hardware", "Catalog.java")

KINDS = {"mobo": "motherboard", "cpu": "cpu", "ram": "ram", "gpu": "gpu", "storage": "storage", "nic": "network", "psu": "psu", "cooler": "cooler"}


def parse_catalog():
    """Reads (kind, id, name, args) for every part from Catalog.java so the icons always match the catalog."""
    parts = []
    pattern = re.compile(r'^\s+(mobo|cpu|ram|gpu|storage|nic|psu|cooler)\("([^"]+)",\s*"([^"]+)",\s*(.*)\);\s*$')
    with open(CATALOG_JAVA, encoding="utf-8") as f:
        for line in f:
            m = pattern.match(line)
            if m:
                args = [a.strip().strip('"') for a in re.findall(r'"[^"]*"|[^,]+', m.group(4))]
                parts.append({"fn": m.group(1), "kind": KINDS[m.group(1)], "id": m.group(2), "name": m.group(3), "args": args})
    return parts


# ----------------------------------------------------------------------------- materials / brands
_TEX = {}


def mat(rgb):
    rgb = tuple(min(255, v + 12) if max(rgb) < 70 else v for v in rgb)
    key = "c%02x%02x%02x" % tuple(rgb)
    if key not in _TEX:
        _TEX[key] = Image.new("RGBA", (4, 4), tuple(rgb) + (255,))
    return key


SILVER = (196, 200, 206)
DARK = (28, 29, 33)
GOLD = (222, 178, 70)
PCB_GREEN = (22, 96, 56)
PCB_BLACK = (24, 24, 28)


def brand(name):
    """(primary, accent, pcb) colours from the product name."""
    n = name.lower()
    if "rog" in n:
        return (26, 26, 30), (232, 32, 52), PCB_BLACK
    if "msi" in n:
        return (30, 30, 34), (214, 30, 44), PCB_BLACK
    if "corsair" in n:
        if "dominator" in n:
            return (206, 208, 214), (255, 206, 0), PCB_BLACK
        return (30, 30, 34), (255, 206, 0), PCB_BLACK
    if any(k in n for k in ("ryzen", "threadripper", "epyc")):
        return (200, 203, 208), (240, 124, 40), PCB_GREEN
    if "radeon" in n or "instinct" in n:
        return (36, 36, 40), (232, 40, 40), PCB_BLACK
    if any(k in n for k in ("intel", "xeon", "core i")):
        return (200, 203, 208), (0, 120, 205), PCB_GREEN
    if any(k in n for k in ("nvidia", "geforce", "rtx", "h100", "h200", "b200", "l4", "l40s", "connectx")):
        return (30, 32, 30), (118, 185, 0), PCB_BLACK
    if "samsung" in n:
        return (30, 34, 40), (30, 70, 190), PCB_GREEN
    if "micron" in n or "kioxia" in n:
        return (36, 36, 40), (30, 90, 200), PCB_GREEN
    if "wd" in n:
        return (60, 60, 64), (210, 35, 35), PCB_GREEN
    if "seagate" in n:
        return (50, 52, 54), (112, 190, 50), PCB_GREEN
    if "noctua" in n:
        return (176, 148, 108), (96, 60, 36), PCB_BLACK
    if "supermicro" in n or "delta" in n or "coolit" in n:
        return (40, 44, 52), (40, 120, 220), PCB_GREEN
    if "asrock" in n or "gigabyte" in n or "dynatron" in n:
        return (36, 38, 44), (240, 124, 30), PCB_GREEN
    if "asus" in n:
        return (28, 28, 32), (60, 140, 230), PCB_GREEN
    return (50, 52, 58), (90, 160, 230), PCB_GREEN


def darker(c, f=0.6):
    return tuple(int(v * f) for v in c)


def lighter(c, f=0.35):
    return tuple(int(v + (255 - v) * f) for v in c)


# ----------------------------------------------------------------------------- builders
def cpu_model(p):
    pri, acc, pcb = brand(p["name"])
    cores = int(p["args"][1])
    sock = p["args"][0]
    big = sock in ("SP5", "sTR5", "LGA4677")
    w, d = (0.94, 0.74) if sock == "sTR5" else (0.84, 0.84) if big else (0.62, 0.62)
    if sock == "sTR5":
        w, d = 0.96, 0.78
    cx = cz = 0.5
    m = Mesh()
    m.add(box(cx - w / 2, 0.0, cz - d / 2, cx + w / 2, 0.05, cz + d / 2, mat(pcb)))
    inset = 0.05
    m.add(box(cx - w / 2 + inset, 0.05, cz - d / 2 + inset, cx + w / 2 - inset, 0.115, cz + d / 2 - inset, mat(SILVER)))
    # IHS top plate with brand stripe
    m.add(box(cx - w / 2 + inset + 0.03, 0.115, cz - d / 2 + inset + 0.03, cx + w / 2 - inset - 0.03, 0.125, cz + d / 2 - inset - 0.03, mat(lighter(SILVER, 0.25))))
    m.add(box(cx - w * 0.28, 0.125, cz - 0.04, cx + w * 0.28, 0.135, cz + 0.04, mat(acc)))
    if cores >= 24:
        m.add(box(cx - w * 0.28, 0.125, cz + 0.08, cx + w * 0.1, 0.133, cz + 0.12, mat(darker(SILVER, 0.5))))
    # AM5-style cutouts / SMD parts on the substrate edge
    for i in range(5):
        x = cx - w / 2 + 0.08 + i * (w - 0.16) / 4
        m.add(box(x - 0.012, 0.05, cz - d / 2 + 0.012, x + 0.012, 0.062, cz - d / 2 + 0.04, mat(DARK)))
    # gold orientation marker
    m.add(box(cx - w / 2 + 0.008, 0.05, cz - d / 2 + 0.008, cx - w / 2 + 0.05, 0.056, cz - d / 2 + 0.05, mat(GOLD)))
    return m


def ram_model(p):
    pri, acc, pcb = brand(p["name"])
    server = "rdimm" in p["name"].lower()
    rgb = "rgb" in p["name"].lower() or "titanium" in p["name"].lower() or "dominator" in p["name"].lower()
    m = Mesh()
    L, H = 1.0, 0.3
    m.add(box(0.0, 0.0, 0.46, L, H, 0.5, mat(PCB_GREEN if server else PCB_BLACK)))
    m.add(box(0.1, -0.025, 0.465, 0.9, 0.0, 0.495, mat(GOLD)))          # contacts
    if server:
        for i in range(8):
            x = 0.06 + i * 0.115
            m.add(box(x, 0.06, 0.5, x + 0.09, 0.22, 0.52, mat((22, 22, 26))))
            m.add(box(x, 0.06, 0.44, x + 0.09, 0.22, 0.46, mat((22, 22, 26))))
        m.add(box(0.0, 0.0, 0.5, L, 0.03, 0.51, mat(SILVER)))
    else:
        for z0, z1 in ((0.5, 0.54), (0.42, 0.46)):
            m.add(box(0.02, 0.04, z0, 0.98, H - 0.02, z1, mat(pri)))
            m.add(box(0.02, 0.15, z0 if z0 > 0.46 else z0 - 0.006, 0.98, 0.18, z1 if z0 > 0.46 else z0, mat(darker(pri, 0.7) if sum(pri) > 300 else lighter(pri, 0.25))))
        m.add(box(0.12, 0.1, 0.54, 0.5, 0.2, 0.545, mat(acc)))             # label
        top = acc if rgb else pri
        m.add(box(0.02, H - 0.02, 0.43, 0.98, H + 0.035, 0.53, mat(top if rgb else lighter(pri, 0.2))))
        if rgb:
            m.add(box(0.06, H + 0.035, 0.455, 0.94, H + 0.05, 0.505, mat(lighter(acc, 0.5))))
    return m


def mobo_model(p):
    pri, acc, pcb = brand(p["name"])
    sock = p["args"][0]
    cpus = int(p["args"][2])
    minU = int(p["args"][6])
    consumer = any(k in p["name"] for k in ("ROG", "MSI"))
    s = 0.72 if minU <= 2 and consumer else 1.0
    m = Mesh()
    m.add(box(0.0, 0.0, 0.0, s, 0.04, s, mat(pcb)))
    ihs_w = 0.2 if sock in ("AM5", "LGA1700") else 0.3
    # sockets
    for i in range(cpus):
        cz = (0.5 if cpus == 1 else 0.3 + 0.4 * i) * s
        cx = 0.4 * s
        m.add(box(cx - ihs_w / 2, 0.04, cz - ihs_w / 2, cx + ihs_w / 2, 0.075, cz + ihs_w / 2, mat(SILVER)))
        m.add(box(cx - ihs_w / 2 + 0.03, 0.075, cz - ihs_w / 2 + 0.03, cx + ihs_w / 2 - 0.03, 0.082, cz + ihs_w / 2 - 0.03, mat(darker(SILVER, 0.55))))
    # DIMM slots right of the socket(s)
    slots = min(8, int(p["args"][3]) if p["args"][3].isdigit() else 4)
    slots = min(slots, 8)
    for i in range(slots):
        x = (0.6 + i * 0.045) * s
        m.add(box(x, 0.04, 0.08 * s, x + 0.03 * s, 0.085, 0.92 * s, mat(darker(pri, 0.8) if sum(pri) < 200 else (60, 60, 64))))
        m.add(box(x + 0.005, 0.085, 0.1 * s, x + 0.025 * s, 0.09, 0.9 * s, mat(acc if consumer else (30, 30, 34))))
    # PCIe slots along the bottom
    for i in range(3):
        z = (0.62 + i * 0.12) * s
        m.add(box(0.12 * s, 0.04, z, 0.52 * s, 0.075, z + 0.045 * s, mat((22, 22, 26))))
    # VRM heatsinks, I/O shroud and chipset
    if consumer:
        m.add(box(0.0, 0.04, 0.1 * s, 0.13 * s, 0.17, 0.9 * s, mat(pri)))
        m.add(box(0.0, 0.17, 0.12 * s, 0.13 * s, 0.185, 0.88 * s, mat(acc)))
        m.add(box(0.14 * s, 0.04, 0.12 * s, 0.34 * s, 0.11, 0.2 * s, mat(lighter(pri, 0.2))))
        m.add(box(0.14 * s, 0.04, 0.8 * s, 0.34 * s, 0.11, 0.88 * s, mat(lighter(pri, 0.2))))
        m.add(box(0.26 * s, 0.04, 0.4 * s, 0.4 * s, 0.1, 0.55 * s, mat(pri)))
        m.add(box(0.28 * s, 0.1, 0.43 * s, 0.38 * s, 0.108, 0.52 * s, mat(acc)))
    else:
        m.add(box(0.0, 0.04, 0.1 * s, 0.1 * s, 0.12, 0.9 * s, mat(SILVER)))
        m.add(box(0.1 * s, 0.04, 0.05 * s, 0.4 * s, 0.1, 0.12 * s, mat(darker(SILVER, 0.55))))
        m.add(box(0.5 * s, 0.04, 0.05 * s, 0.56 * s, 0.1, 0.12 * s, mat(acc)))
    return m


def gpu_model(p):
    pri, acc, pcb = brand(p["name"])
    n = p["name"]
    server = any(k in n for k in ("H100", "H200", "B200", "L40S", "L4 ", "MI300")) or n.endswith("L4 24GB")
    vram = int(p["args"][0])
    thick = 0.3 if "4090" in n or "7900" in n else 0.22 if "4080" in n else 0.17
    if server:
        thick = 0.16
    m = Mesh()
    L, W = 1.0, 0.46
    m.add(box(0.06, 0.0, 0.0, L - 0.04, 0.02, W - 0.04, mat(pcb)))                          # PCB / backplate
    m.add(box(0.0, 0.0, 0.0, 0.05, 0.02 + thick + 0.02, W, mat(SILVER)))                    # bracket
    m.add(box(0.2, -0.02, 0.0, 0.7, 0.02, 0.05, mat(GOLD)))                                  # PCIe fingers
    if server:
        body = (22, 22, 24) if "AMD" not in n else (40, 40, 44)
        m.add(box(0.06, 0.02, 0.0, L - 0.04, 0.02 + thick, W - 0.04, mat(body)))
        m.add(box(0.06, 0.02 + thick, 0.0, L - 0.04, 0.02 + thick + 0.012, W - 0.04, mat(lighter(body, 0.12))))
        for i in range(9):
            x = 0.16 + i * 0.09
            m.add(box(x, 0.02 + thick + 0.012, 0.03, x + 0.035, 0.02 + thick + 0.04, W - 0.07, mat(GOLD if "NVIDIA" not in n else (170, 172, 176))))
        m.add(box(0.72, 0.02 + thick + 0.012, 0.03, 0.96, 0.02 + thick + 0.02, 0.12, mat(acc)))
        return m
    # consumer cards: shroud + 2 or 3 fans
    m.add(box(0.06, 0.02, 0.0, L - 0.04, 0.02 + thick, W - 0.04, mat(pri)))
    fans = 3 if ("4090" in n or "7900" in n) else 2
    for i in range(fans):
        cx = 0.17 + (i + 0.5) * (0.78 / fans)
        m.add(cylinder_y(cx, 0.21, 0.13 if fans == 2 else 0.095, 0.02 + thick, 0.02 + thick + 0.012, 18, mat(DARK)))
        for k in range(7):
            a = k * math.pi * 2 / 7
            m.add(box(cx + math.cos(a) * 0.05 - 0.012, 0.02 + thick + 0.012, 0.21 + math.sin(a) * 0.05 - 0.012,
                      cx + math.cos(a) * 0.09 + 0.012, 0.02 + thick + 0.02, 0.21 + math.sin(a) * 0.09 + 0.012, mat(lighter(pri, 0.2))))
        m.add(cylinder_y(cx, 0.21, 0.03, 0.02 + thick + 0.012, 0.02 + thick + 0.028, 12, mat(acc)))
    m.add(box(0.1, 0.02 + thick, 0.395, L - 0.08, 0.02 + thick + 0.012, 0.42, mat(acc)))   # accent strip
    m.add(box(0.06, 0.02 + thick * 0.55, W - 0.04, L - 0.04, 0.02 + thick * 0.8, W - 0.025, mat(acc)))  # side lighting
    m.add(box(0.7, 0.02 + thick, 0.0, 0.9, 0.02 + thick + 0.012, 0.03, mat(lighter(pri, 0.3))))  # pcie power cutout
    return m


def psu_model(p):
    pri, acc, pcb = brand(p["name"])
    watts = int(p["args"][0])
    rackish = any(k in p["name"] for k in ("Supermicro", "Delta"))
    m = Mesh()
    if rackish:
        m.add(box(0.0, 0.0, 0.0, 1.0, 0.16, 0.5, mat(pri)))
        m.add(box(0.0, 0.0, 0.0, 0.04, 0.16, 0.5, mat(acc)))
        m.add(box(0.9, 0.04, 0.4, 1.0, 0.12, 0.5, mat((12, 12, 14))))
        for i in range(8):
            m.add(box(0.1 + i * 0.1, 0.16, 0.05, 0.14 + i * 0.1, 0.165, 0.45, mat(darker(pri, 0.6))))
        m.add(box(0.3, 0.16, 0.2, 0.7, 0.166, 0.3, mat(lighter(pri, 0.5))))
        return m
    h = 0.42 if watts >= 1000 else 0.36 if watts >= 750 else 0.3
    m.add(box(0.1, 0.0, 0.1, 0.9, h, 0.6, mat(pri)))
    m.add(cylinder_y(0.5, 0.35, 0.19, h, h + 0.008, 22, mat(DARK)))
    for k in range(7):
        a = k * math.pi * 2 / 7
        m.add(box(0.5 + math.cos(a) * 0.04 - 0.012, h + 0.008, 0.35 + math.sin(a) * 0.04 - 0.012,
                  0.5 + math.cos(a) * 0.16 + 0.012, h + 0.016, 0.35 + math.sin(a) * 0.16 + 0.012, mat(lighter(pri, 0.2))))
    m.add(cylinder_y(0.5, 0.35, 0.035, h + 0.008, h + 0.02, 12, mat(acc)))
    m.add(box(0.1, 0.0, 0.1, 0.9, h * 0.3, 0.14, mat(acc)))
    m.add(box(0.14, 0.04, 0.6, 0.86, h - 0.04, 0.64, mat((18, 18, 20))))
    for i in range(6):
        m.add(box(0.18 + i * 0.11, 0.08, 0.64, 0.25 + i * 0.11, h * 0.5, 0.67, mat((42, 42, 46))))
    return m


def storage_model(p):
    pri, acc, pcb = brand(p["name"])
    typ = p["args"][0]
    m = Mesh()
    if typ == "NVME":
        u2 = "U.2" in p["name"]
        if u2:
            m.add(box(0.05, 0.0, 0.1, 0.95, 0.12, 0.6, mat(pri)))
            m.add(box(0.05, 0.0, 0.1, 0.12, 0.12, 0.6, mat(acc)))
            m.add(box(0.12, 0.12, 0.2, 0.7, 0.126, 0.5, mat(lighter(pri, 0.4))))
            return m
        m.add(box(0.0, 0.0, 0.3, 1.0, 0.025, 0.55, mat(pcb)))
        m.add(box(0.0, -0.01, 0.31, 0.12, 0.0, 0.54, mat(GOLD)))
        gen5 = "Gen5" in p["name"]
        m.add(box(0.2, 0.025, 0.3, 0.98, 0.075 if not gen5 else 0.12, 0.55, mat(pri if not gen5 else (36, 36, 40))))
        if gen5:
            for i in range(6):
                m.add(box(0.26 + i * 0.1, 0.12, 0.32, 0.3 + i * 0.1, 0.17, 0.53, mat(lighter(pri, 0.25))))
        m.add(box(0.3, 0.075, 0.34, 0.9, 0.082, 0.5, mat(acc)))
        return m
    if typ == "SSD":
        m.add(box(0.05, 0.0, 0.1, 0.95, 0.12, 0.7, mat(pri)))
        m.add(box(0.05, 0.12, 0.1, 0.95, 0.126, 0.7, mat(lighter(pri, 0.15))))
        m.add(box(0.15, 0.126, 0.25, 0.75, 0.132, 0.55, mat(acc)))
        m.add(box(0.8, 0.04, 0.7, 0.95, 0.09, 0.74, mat(GOLD)))
        return m
    m.add(box(0.0, 0.0, 0.0, 1.0, 0.18, 0.66, mat(SILVER)))
    m.add(box(0.0, 0.18, 0.0, 1.0, 0.195, 0.66, mat(lighter(SILVER, 0.2))))
    m.add(box(0.1, 0.195, 0.12, 0.8, 0.203, 0.54, mat(acc)))
    m.add(box(0.14, 0.203, 0.2, 0.5, 0.21, 0.3, mat((236, 236, 240))))
    m.add(box(0.9, 0.04, 0.66, 1.0, 0.12, 0.7, mat(GOLD)))
    return m


def nic_model(p):
    pri, acc, pcb = brand(p["name"])
    gbps = float(p["args"][0])
    m = Mesh()
    m.add(box(0.06, 0.0, 0.0, 0.95, 0.03, 0.4, mat(pcb)))
    m.add(box(0.0, 0.0, 0.0, 0.05, 0.3, 0.42, mat(SILVER)))
    m.add(box(0.2, -0.02, 0.0, 0.7, 0.03, 0.05, mat(GOLD)))
    sfp = 2
    for i in range(sfp):
        z = 0.06 + i * 0.16
        m.add(box(-0.1, 0.08, z, 0.05, 0.2, z + 0.13, mat(darker(SILVER, 0.7))))
        m.add(box(-0.1, 0.12, z + 0.03, -0.095, 0.17, z + 0.1, mat(acc if gbps >= 25 else (20, 20, 24))))
    m.add(box(0.3, 0.03, 0.08, 0.7, 0.12 if gbps < 100 else 0.22, 0.3, mat(pri)))
    if gbps >= 100:
        for i in range(5):
            m.add(box(0.32 + i * 0.08, 0.22, 0.1, 0.36 + i * 0.08, 0.27, 0.28, mat(lighter(pri, 0.3))))
    m.add(box(0.5, 0.12, 0.12, 0.66, 0.125, 0.26, mat(acc)))
    return m


def cooler_model(p):
    pri, acc, pcb = brand(p["name"])
    typ = p["args"][0]
    minU = int(p["args"][4])
    m = Mesh()
    if typ == "LIQUID":
        if "CoolIT" in p["name"]:
            m.add(box(0.1, 0.0, 0.1, 0.9, 0.3, 0.7, mat(pri)))
            m.add(box(0.1, 0.0, 0.1, 0.9, 0.04, 0.7, mat(acc)))
            for i in range(2):
                m.add(tube((0.25 + i * 0.5, 0.3, 0.4), (0.25 + i * 0.5, 0.5, 0.4), 0.04, 10, mat((20, 20, 24))))
                m.add(box(0.2 + i * 0.5, 0.5, 0.35, 0.3 + i * 0.5, 0.54, 0.45, mat(SILVER)))
            return m
        big = "360" in p["name"]
        rl = 1.0 if big else 0.7
        m.add(box(0.0, 0.0, 0.0, rl, 0.12, 0.34, mat(pri)))           # radiator
        for k in range(int(rl / 0.015)):
            m.add(box(0.015 * k, 0.12, 0.02, 0.015 * k + 0.008, 0.125, 0.32, mat(darker(pri, 0.65))))
        for i in range(3 if big else 2):
            cx = 0.17 + i * 0.33 if big else 0.18 + i * 0.34
            m.add(cylinder_y(cx, 0.17, 0.14, 0.125, 0.135, 20, mat(DARK)))
            m.add(cylinder_y(cx, 0.17, 0.035, 0.135, 0.15, 12, mat(acc)))
        m.add(cylinder_y(0.55 if not big else 0.8, 0.7, 0.17, 0.0, 0.2, 18, mat(pri)))   # pump head
        m.add(cylinder_y(0.55 if not big else 0.8, 0.7, 0.135, 0.2, 0.215, 18, mat((14, 14, 18))))
        m.add(cylinder_y(0.55 if not big else 0.8, 0.7, 0.06, 0.215, 0.228, 12, mat(acc)))
        m.add(tube((0.12, 0.08, 0.34), (0.45 if not big else 0.7, 0.1, 0.62), 0.032, 8, mat((20, 20, 24))))
        m.add(tube((0.25, 0.08, 0.34), (0.58 if not big else 0.85, 0.1, 0.58), 0.032, 8, mat((20, 20, 24))))
        return m
    h = 0.22 if minU <= 1 else 0.5 if minU == 2 else 0.78
    m.add(box(0.2, 0.0, 0.2, 0.8, 0.04, 0.8, mat(SILVER)))
    for i in range(14):
        z = 0.2 + i * 0.043
        m.add(box(0.2, 0.04, z, 0.8, h, z + 0.018, mat(pri if minU > 1 else SILVER)))
    if minU > 1:
        for i in range(3):
            m.add(tube((0.26 + i * 0.24, 0.04, 0.2), (0.26 + i * 0.24, 0.04 + h * 0.9, 0.2), 0.02, 8, mat(SILVER)))
    fz = 0.18
    m.add(box(0.22, 0.04, fz - 0.05, 0.78, h - 0.02, fz, mat(acc if "Noctua" in p["name"] else (20, 20, 24))))
    m.add(cylinder_y(0.5, fz - 0.025, 0.0, 0.0, 0.0, 3, mat(DARK)))
    return m


BUILDERS = {"cpu": cpu_model, "ram": ram_model, "motherboard": mobo_model, "gpu": gpu_model, "psu": psu_model,
            "storage": storage_model, "network": nic_model, "cooler": cooler_model}


def make_icon(part, size=32):
    mesh = BUILDERS[part["kind"]](part)
    # args layout differs per kind: normalise the ones the builders need
    img = render(mesh, _TEX, size=size, yaw=148, pitch=42, supersample=6)
    return img
