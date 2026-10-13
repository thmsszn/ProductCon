"""Free-form 3D models for Crude & Compute.

Every function returns a Mesh in block units. Static models end up in the chunk mesh (blockstate
model); the *_anim models are rendered and animated by block entity renderers in Java. The pivot
constants below are mirrored in com.crudecompute.client.ModelPivots - keep them in sync.
"""

import math

from meshlib import (EMISSIVE, Mesh, annular_sector_x, beam, bezier_tube, blade, box, cylinder_y,
                     disc_y, frustum_y, prism_x, ring_y, sector_plate_x, sphere, torus_y, tube,
                     washer_y)

EMISSIVE.update({"led_green", "led_red", "led_blue", "led_amber", "led_white", "screen"})

# ----------------------------------------------------------------------------- shared pivots
PUMPJACK_PIVOT = (0.5, 2.4, -0.3)      # walking beam bearing
PUMPJACK_CRANK = (0.5, 0.5, 0.5)       # crank shaft centre (axis = X)
PUMPJACK_CRANK_R = 0.25                # crank pin radius
PUMPJACK_BEAM_BACK = 0.8               # pivot -> equalizer distance
PUMPJACK_HEAD_R = 1.2                  # pivot -> horsehead arc radius
PUMPJACK_CARRIER_Y = 1.56              # carrier bar height at rest
WINDMILL_HUB = (0.5, 0.72, 0.26)
FLYWHEEL_CENTER = (0.5, 0.5)

SHAFT_R = 0.125


def along_x(mesh, cy=0.5, cz=0.5):
    """Mesh built around the Y axis at (0.5, *, 0.5) -> runs along X (y value becomes x) at height cy, depth cz."""
    return mesh.rotate_z(-90, (0.5, 0.5, 0.5)).translate(0, cy - 0.5, cz - 0.5)


def along_z(mesh, cy=0.5):
    """Mesh built along Y around (0.5, cy, 0.5) -> runs along Z (front = -Z)."""
    return mesh.rotate_x(90, (0.5, cy, 0.5))


# ============================================================================= kinetics
def shaft():
    m = cylinder_y(0.5, 0.5, SHAFT_R, 0.0, 1.0, 8, "steel", angle0=math.pi / 8, smooth=False)
    m.add(washer_y(0.5, 0.5, 0.0, 0.16, 0.44, 0.56, 8, "brass", inner=False))
    return m


def shaft_half():
    """From the block centre to the top face (used as connection stubs)."""
    m = cylinder_y(0.5, 0.5, SHAFT_R, 0.5, 1.0, 8, "steel", angle0=math.pi / 8, smooth=False, cap_bottom=False)
    m.add(washer_y(0.5, 0.5, 0.0, 0.15, 0.86, 0.92, 8, "brass", inner=False))
    return m


def gearbox():
    m = Mesh()
    t = 1 / 16
    m.add(box(t, t, t, 1 - t, 1 - t, 1 - t, "steel_dark"))
    e = 0.12
    # brass frame edges
    for (x0, x1) in ((0, e), (1 - e, 1)):
        for (z0, z1) in ((0, e), (1 - e, 1)):
            m.add(box(x0, 0, z0, x1, 1, z1, "brass"))
    for (y0, y1) in ((0, e), (1 - e, 1)):
        for (z0, z1) in ((0, e), (1 - e, 1)):
            m.add(box(e, y0, z0, 1 - e, y1, z1, "brass"))
        for (x0, x1) in ((0, e), (1 - e, 1)):
            m.add(box(x0, y0, e, x1, y1, 1 - e, "brass"))
    # round bearing bosses on each face
    boss = cylinder_y(0.5, 0.5, 0.2, 1 - t - 0.001, 1 - t + 0.03, 12, "steel", cap_bottom=False)
    for rot in ((0, 0), (180, 0), (90, 0), (-90, 0)):
        m.add(boss.rotate_x(rot[0], (0.5, 0.5, 0.5)))
    m.add(boss.rotate_z(90, (0.5, 0.5, 0.5)))
    m.add(boss.rotate_z(-90, (0.5, 0.5, 0.5)))
    return m


def hand_crank_base():
    m = cylinder_y(0.5, 0.5, 0.3, 0.0, 0.06, 16, "steel")
    m.add(cylinder_y(0.5, 0.5, 0.13, 0.06, 0.2, 12, "brass"))
    for i in range(4):
        a = math.pi / 4 + i * math.pi / 2
        m.add(cylinder_y(0.5 + 0.22 * math.cos(a), 0.5 + 0.22 * math.sin(a), 0.03, 0.06, 0.08, 6, "steel_dark"))
    return m


def hand_crank_anim():
    m = Mesh()
    m.add(beam((0.5, 0.24, 0.5), (0.83, 0.24, 0.5), 0.09, 0.07, "steel"))
    m.add(cylinder_y(0.5, 0.5, 0.07, 0.2, 0.28, 10, "steel_dark"))
    m.add(cylinder_y(0.8, 0.5, 0.045, 0.27, 0.62, 10, "wood"))
    m.add(sphere(0.8, 0.64, 0.5, 0.065, 0.065, 0.065, 10, 6, "paint_red"))
    return m


# ----------------------------------------------------------------------------- windmill
def windmill():
    m = frustum_y(0.5, 0.5, 0.17, 0.12, 0.0, 0.62, 16, "column_white")
    m.add(washer_y(0.5, 0.5, 0.0, 0.2, 0.0, 0.05, 16, "steel_dark", inner=False))
    m.add(washer_y(0.5, 0.5, 0.0, 0.15, 0.6, 0.64, 16, "steel_dark", inner=False))
    hub_y = WINDMILL_HUB[1]
    nacelle = cylinder_y(0.5, 0.5, 0.15, 0.32, 0.84, 16, "plastic_white", cap_bottom=True, cap_top=False)
    nacelle = nacelle.rotate_x(90, (0.5, 0.5, 0.5)).translate(0, hub_y - 0.5, 0)
    m.add(nacelle)
    m.add(sphere(0.5, hub_y, 0.84, 0.15, 0.14, 0.15, 16, 6, "plastic_white", lat_from=0, lat_to=90)
          .rotate_x(90, (0.5, hub_y, 0.84)))
    m.add(box(0.44, hub_y + 0.13, 0.55, 0.56, hub_y + 0.17, 0.78, "steel_dark"))  # cooler on top
    m.add(cylinder_y(0.5, 0.5, 0.012, hub_y + 0.17, hub_y + 0.27, 6, "steel").translate(0, 0, 0.25))  # anemometer
    m.add(box(0.47, hub_y + 0.26, 0.72, 0.53, hub_y + 0.28, 0.78, "led_red"))
    return m


def windmill_anim():
    """Rotor around the Z axis through WINDMILL_HUB."""
    hx, hy, hz = WINDMILL_HUB
    m = Mesh()
    spinner = sphere(0, 0, 0, 0.12, 0.18, 0.12, 14, 7, "plastic_white", lat_from=0, lat_to=90)
    spinner = spinner.rotate_x(-90).translate(hx, hy, hz - 0.08)  # nose points to -Z
    m.add(spinner)
    hub = cylinder_y(0, 0, 0.12, -0.08, 0.06, 14, "plastic_white").rotate_x(90).translate(hx, hy, hz)
    m.add(hub)
    b = blade(0.08, 1.38, 0.26, 0.07, 32, 6, 0.035, 10, "plastic_white")
    tip = blade(1.18, 1.38, 0.1, 0.07, 6, 6, 0.016, 2, "paint_red")
    for i in range(3):
        ang = i * 120
        m.add(b.rotate_z(ang).translate(hx, hy, hz))
        m.add(tip.scale(1.02).rotate_z(ang).translate(hx, hy, hz))
    return m


# ----------------------------------------------------------------------------- diesel engine
def diesel_engine():
    m = Mesh()
    for x0 in (0.12, 0.78):
        m.add(box(x0, 0, 0.04, x0 + 0.1, 0.08, 0.98, "steel_dark"))
    m.add(box(0.22, 0.08, 0.2, 0.78, 0.62, 0.9, "paint_red"))
    m.add(box(0.26, 0.04, 0.24, 0.74, 0.1, 0.86, "steel_dark"))  # oil pan
    m.add(box(0.2, 0.2, 0.3, 0.22, 0.5, 0.8, "steel"))  # side cover
    # flywheel housing (bell) around the output
    bell = frustum_y(0.5, 0.5, 0.37, 0.3, 0.12, 0.22, 20, "paint_red", cap_top=False, cap_bottom=True)
    m.add(along_z(bell))
    # three finned cylinders + valve cover
    for z in (0.36, 0.56, 0.76):
        m.add(cylinder_y(0.5, z, 0.1, 0.62, 0.78, 12, "steel"))
        for k in range(4):
            y = 0.64 + k * 0.035
            m.add(washer_y(0.5, z, 0.0, 0.135, y, y + 0.014, 12, "steel_dark", inner=False))
    m.add(box(0.37, 0.78, 0.24, 0.63, 0.84, 0.88, "paint_red"))
    cover = cylinder_y(0.5, 0.5, 0.13, 0.24, 0.88, 12, "paint_red").rotate_x(90, (0.5, 0.5, 0.5))
    cover = cover.map(lambda p: (p[0], max(p[1], 0.5), p[2])).translate(0, 0.34, 0.06)
    m.add(cover)
    m.add(box(0.44, 0.96, 0.4, 0.56, 0.98, 0.72, "brass"))  # oil filler cap strip
    # exhaust manifold + stack
    for z in (0.36, 0.56, 0.76):
        m.add(tube((0.58, 0.7, z), (0.74, 0.7, z), 0.035, 8, "rust_steel"))
    m.add(tube((0.76, 0.7, 0.3), (0.76, 0.7, 0.82), 0.05, 10, "rust_steel"))
    m.add(bezier_tube((0.76, 0.7, 0.82), (0.76, 0.7, 0.9), (0.76, 0.72, 0.9), (0.76, 0.8, 0.9), 0.05, 6, 10, "rust_steel"))
    m.add(tube((0.76, 0.8, 0.9), (0.76, 0.99, 0.9), 0.05, 10, "steel_dark"))
    m.add(beam((0.7, 1.0, 0.87), (0.82, 0.985, 0.93), 0.12, 0.012, "steel"))  # rain cap
    # intake air filter
    m.add(cylinder_y(0.16, 0.42, 0.075, 0.55, 0.84, 12, "rubber", cap_mat="steel"))
    m.add(tube((0.16, 0.6, 0.42), (0.24, 0.5, 0.42), 0.035, 8, "rubber"))
    # fuel tank
    tank = cylinder_y(0.5, 0.5, 0.075, 0.26, 0.86, 12, "paint_yellow").rotate_x(90, (0.5, 0.5, 0.5))
    m.add(tank.translate(-0.37, -0.2, 0.06))
    # radiator at the back
    m.add(box(0.2, 0.08, 0.9, 0.8, 0.74, 0.98, "steel_dark", {"s": "vent"}))
    m.add(box(0.3, 0.74, 0.92, 0.36, 0.8, 0.96, "brass"))
    return m


def diesel_engine_anim():
    """Flywheel spinning around the Z axis at FLYWHEEL_CENTER (front face)."""
    m = Mesh()
    rim = washer_y(0.5, 0.5, 0.27, 0.34, 0.9, 0.96, 24, "steel")
    hub = cylinder_y(0.5, 0.5, 0.075, 0.88, 0.97, 10, "steel_dark")
    m.add(rim).add(hub)
    for i in range(5):
        a = 2 * math.pi * i / 5
        m.add(beam((0.5 + 0.06 * math.cos(a), 0.93, 0.5 + 0.06 * math.sin(a)),
                   (0.5 + 0.28 * math.cos(a), 0.93, 0.5 + 0.28 * math.sin(a)), 0.05, 0.035, "steel"))
    m.add(box(0.47, 0.955, 0.8, 0.53, 0.965, 0.84, "paint_yellow"))  # timing mark
    # built along +Y with the front at y=1 -> rotate so +Y points to -Z (north)
    return m.rotate_x(-90, (0.5, 0.5, 0.5))


# ----------------------------------------------------------------------------- generator
def generator(active):
    m = Mesh()
    for z0 in (0.16, 0.68):
        m.add(box(0.14, 0, z0, 0.86, 0.12, z0 + 0.16, "steel_dark"))
    stator = cylinder_y(0.5, 0.5, 0.34, 0.14, 0.86, 24, "paint_blue", cap_bottom=False, cap_top=False)
    m.add(along_z(stator))
    for i in range(16):
        a = 2 * math.pi * (i + 0.5) / 16
        if math.sin(a) < -0.55:
            continue  # leave room for the feet
        c, s = math.cos(a), math.sin(a)
        m.add(beam((0.5 + 0.36 * c, 0.5 + 0.36 * s, 0.17), (0.5 + 0.36 * c, 0.5 + 0.36 * s, 0.83), 0.04, 0.05, "paint_blue",
                   up=(c, s, 0)))
    for (z0, z1, r0, r1) in ((0.06, 0.14, 0.2, 0.34), (0.86, 0.94, 0.34, 0.2)):
        bell = frustum_y(0.5, 0.5, r0, r1, z0, z1, 20, "steel")
        m.add(along_z(bell))
    m.add(box(0.38, 0.84, 0.36, 0.62, 0.96, 0.64, "steel_dark"))
    m.add(tube((0.62, 0.9, 0.5), (0.76, 0.9, 0.5), 0.03, 8, "copper"))
    m.add(box(0.44, 0.96, 0.44, 0.56, 0.975, 0.56, "led_green" if active else "rubber"))
    m.add(box(0.12, 0.38, 0.4, 0.15, 0.58, 0.6, "brass"))  # name plate
    return m


# ----------------------------------------------------------------------------- pumpjack
def pumpjack_static():
    px_, py, pz = PUMPJACK_PIVOT
    m = Mesh()
    # skid with cross members
    for x0 in (0.28, 0.62):
        m.add(box(x0, 0, -1.96, x0 + 0.1, 0.12, 0.96, "steel_dark"))
    for z in (0.62, -0.05, -0.95, -1.62):
        m.add(box(0.06, 0.0, z - 0.06, 0.94, 0.1, z + 0.06, "steel_dark"))
    # gearbox (reducer) with rounded top around the crank shaft
    m.add(box(0.3, 0.12, 0.26, 0.7, 0.5, 0.8, "paint_yellow"))
    m.add(along_x(cylinder_y(0.5, 0.5, 0.27, 0.3, 0.7, 20, "paint_yellow"), cy=0.5, cz=0.53))
    m.add(box(0.28, 0.12, 0.24, 0.72, 0.16, 0.82, "steel_dark"))
    m.add(along_x(cylinder_y(0.5, 0.5, 0.1, 0.22, 0.78, 12, "steel"), cy=0.5, cz=0.5))  # bearing housings
    # samson post (A-frame)
    top_y = py - 0.24
    legs = [((0.1, 0.1, -0.05), (0.4, top_y, pz + 0.08)), ((0.9, 0.1, -0.05), (0.6, top_y, pz + 0.08)),
            ((0.1, 0.1, -0.95), (0.4, top_y, pz - 0.08)), ((0.9, 0.1, -0.95), (0.6, top_y, pz - 0.08))]
    for a, b in legs:
        m.add(beam(a, b, 0.08, 0.08, "steel"))
    for t in (0.35, 0.68):
        def lerp(a, b):
            return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
        p = [lerp(*legs[i]) for i in range(4)]
        m.add(beam(p[0], p[1], 0.05, 0.05, "steel_dark"))
        m.add(beam(p[2], p[3], 0.05, 0.05, "steel_dark"))
        m.add(beam(p[0], p[2], 0.05, 0.05, "steel_dark"))
        m.add(beam(p[1], p[3], 0.05, 0.05, "steel_dark"))
    # ladder on the back legs
    for x in (0.18, 0.3):
        m.add(beam((x, 0.1, -0.02), (x + 0.2, top_y - 0.1, pz + 0.12), 0.025, 0.025, "steel_dark"))
    # saddle bearing
    m.add(box(0.36, top_y, pz - 0.14, 0.64, top_y + 0.12, pz + 0.14, "steel_dark"))
    m.add(along_x(cylinder_y(0.5, 0.5, 0.07, 0.33, 0.67, 12, "brass"), cy=py, cz=pz))
    # wellhead in the front cell
    cx, cz = 0.5, -1.5
    m.add(box(0.1, 0, cz - 0.4, 0.9, 0.05, cz + 0.4, "concrete"))
    m.add(cylinder_y(cx, cz, 0.15, 0.05, 0.2, 14, "steel_dark"))
    m.add(washer_y(cx, cz, 0.0, 0.2, 0.18, 0.23, 14, "steel", inner=False))
    m.add(cylinder_y(cx, cz, 0.1, 0.23, 0.42, 12, "paint_red"))
    m.add(washer_y(cx, cz, 0.0, 0.13, 0.4, 0.44, 12, "steel", inner=False))
    m.add(cylinder_y(cx, cz, 0.065, 0.44, 0.58, 10, "brass"))  # stuffing box
    for sgn in (-1, 1):
        x_end = 0.5 + sgn * 0.5
        m.add(tube((cx, 0.31, cz), (x_end, 0.31, cz), 0.07, 10, "steel"))
        m.add(tube((x_end - sgn * 0.04, 0.31, cz), (x_end, 0.31, cz), 0.11, 10, "steel_dark"))
    m.add(cylinder_y(0.28, cz, 0.012, 0.38, 0.47, 6, "steel"))  # valve stem + hand wheel
    m.add(torus_y(0.28, 0.48, cz, 0.07, 0.013, 14, 6, "paint_red"))
    m.add(box(0.27, 0.47, cz - 0.07, 0.29, 0.49, cz + 0.07, "paint_red"))
    return m


def pumpjack_beam():
    """Walking beam + horsehead + equalizer. Rotates around X through PUMPJACK_PIVOT."""
    px_, py, pz = PUMPJACK_PIVOT
    m = Mesh()
    back_z = pz + PUMPJACK_BEAM_BACK
    m.add(box(0.41, py - 0.12, pz - 0.97, 0.59, py + 0.12, back_z + 0.1, "paint_yellow"))
    m.add(box(0.4, py + 0.12, pz - 0.97, 0.6, py + 0.14, back_z + 0.1, "steel"))  # top flange
    m.add(box(0.4, py - 0.14, pz - 0.97, 0.6, py - 0.12, back_z + 0.1, "steel"))  # bottom flange
    m.add(box(0.44, py - 0.1, pz - 0.15, 0.56, py + 0.1, pz + 0.15, "steel_dark"))
    # horsehead: curved slab centred on the pivot so the bridle always hangs vertically
    m.add(annular_sector_x(py, pz, 0.95, PUMPJACK_HEAD_R, -24, 24, 12, 0.36, 0.64, "paint_yellow", front_mat="steel"))
    m.add(annular_sector_x(py, pz, 0.94, 1.0, -26, 26, 12, 0.34, 0.66, "hazard"))
    # equalizer bar at the back end
    m.add(box(0.06, py - 0.2, back_z - 0.06, 0.94, py - 0.12, back_z + 0.06, "steel_dark"))
    m.add(box(0.44, py - 0.2, back_z - 0.08, 0.56, py - 0.12, back_z + 0.08, "steel"))
    return m


def pumpjack_crank():
    """Crank arms + counterweights around the X axis through PUMPJACK_CRANK."""
    _, cy, cz = PUMPJACK_CRANK
    r = PUMPJACK_CRANK_R
    m = Mesh()
    m.add(along_x(cylinder_y(0.5, 0.5, 0.06, 0.05, 0.95, 10, "steel"), cy=cy, cz=cz))
    for (x0, x1) in ((0.14, 0.22), (0.78, 0.86)):
        # arm towards the crank pin (pointing to +Z at rest = angle 0)
        m.add(box(x0, cy - 0.06, cz - 0.05, x1, cy + 0.06, cz + r + 0.05, "steel_dark"))
        # counterweight on the opposite side: free-form pie slice
        m.add(sector_plate_x(cy, cz, 0.08, 0.42, 145, 215, 10, x0 - 0.01, x1 + 0.01, "paint_red"))
        # crank pin sticking out sideways
        pin_x0, pin_x1 = (0.05, x0) if x0 < 0.5 else (x1, 0.95)
        m.add(along_x(cylinder_y(0.5, 0.5, 0.035, pin_x0, pin_x1, 8, "brass"), cy=cy, cz=cz + r))
    return m


def unit_rod(w, h, mat):
    """Unit length rod along +Y from the origin, centred on X/Z (stretched by the renderer)."""
    return box(-w / 2, 0, -h / 2, w / 2, 1, h / 2, mat)


def pumpjack_pitman():
    return unit_rod(0.06, 0.08, "steel")


def pumpjack_cable():
    return unit_rod(0.025, 0.025, "steel_dark")


def pumpjack_rod():
    """Carrier bar + polished rod at rest, moved vertically by the renderer."""
    cz = -1.5
    y = PUMPJACK_CARRIER_Y
    m = Mesh()
    m.add(box(0.38, y - 0.05, cz - 0.04, 0.62, y, cz + 0.04, "steel_dark"))
    m.add(cylinder_y(0.5, cz, 0.026, 0.45, y - 0.05, 8, "steel"))
    m.add(box(0.46, y - 0.12, cz - 0.04, 0.54, y - 0.05, cz + 0.04, "brass"))  # rod clamp
    return m


# ----------------------------------------------------------------------------- refinery
def refinery():
    m = Mesh()
    cx = cz = 0.5
    m.add(frustum_y(cx, cz, 0.48, 0.46, 0.0, 0.12, 8, "concrete", angle0=math.pi / 8))
    m.add(frustum_y(cx, cz, 0.4, 0.36, 0.12, 0.35, 20, "steel_dark"))
    m.add(cylinder_y(cx, cz, 0.34, 0.35, 2.74, 24, "column_white", cap_bottom=False, cap_top=False))
    m.add(sphere(cx, 2.74, cz, 0.34, 0.2, 0.34, 24, 6, "column_white", lat_from=0, lat_to=90))
    m.add(cylinder_y(cx, cz, 0.05, 2.9, 3.0, 8, "steel_dark"))
    for y in (0.95, 1.55, 2.15):
        m.add(torus_y(cx, y, cz, 0.345, 0.016, 24, 4, "steel"))
    # platforms with railings
    for y in (1.22, 2.3):
        m.add(washer_y(cx, cz, 0.34, 0.5, y - 0.03, y, 24, "grating"))
        for i in range(8):
            a = 2 * math.pi * (i + 0.5) / 8
            px_, pz_ = cx + 0.48 * math.cos(a), cz + 0.48 * math.sin(a)
            m.add(cylinder_y(px_, pz_, 0.012, y, y + 0.26, 4, "paint_yellow"))
        m.add(torus_y(cx, y + 0.26, cz, 0.48, 0.012, 24, 4, "paint_yellow"))
        m.add(torus_y(cx, y + 0.13, cz, 0.48, 0.009, 24, 4, "paint_yellow"))
    # ladder on the back (+Z)
    for x in (0.43, 0.57):
        m.add(box(x - 0.012, 0.35, 0.855, x + 0.012, 2.6, 0.875, "steel_dark"))
    y = 0.42
    while y < 2.58:
        m.add(box(0.43, y, 0.86, 0.57, y + 0.016, 0.872, "steel"))
        y += 0.12
    # nozzles: crude inlet low on the west, diesel draw-off on the east (middle), gas at the top
    def nozzle(y, side, r=0.09):
        x_end = 0.0 if side < 0 else 1.0
        x_col = 0.5 + side * 0.32
        mm = tube((x_col, y, cz), (x_end, y, cz), r, 12, "steel")
        mm.add(tube((x_end - side * 0.04, y, cz), (x_end, y, cz), r + 0.06, 12, "steel_dark"))
        return mm
    m.add(nozzle(0.6, -1))
    m.add(nozzle(0.6, 1))
    m.add(nozzle(1.8, 1, 0.07))
    m.add(nozzle(2.55, -1, 0.06))
    # gauge
    g = cylinder_y(0, 0, 0.07, 0, 0.025, 12, "steel_dark", cap_mat="gauge").rotate_x(-90).translate(0.5, 1.0, 0.17)
    m.add(g)
    m.add(box(0.48, 0.92, 0.15, 0.52, 0.96, 0.17, "steel"))
    # small flare stack beside the top
    m.add(tube((0.5, 2.5, 0.13), (0.5, 2.97, 0.09), 0.03, 8, "rust_steel"))
    m.add(cylinder_y(0.5, 0.09, 0.045, 2.95, 3.0, 8, "steel_dark"))
    return m


# ----------------------------------------------------------------------------- pipes, tank, cable
PIPE_R = 0.15


def pipe_center():
    return sphere(0.5, 0.5, 0.5, 0.2, 0.2, 0.2, 12, 8, "copper")


def pipe_arm():
    """Arm from the centre towards north (-Z)."""
    arm = cylinder_y(0.5, 0.5, PIPE_R, 0.5, 1.0, 12, "copper", cap_bottom=False, cap_top=False)
    flange = cylinder_y(0.5, 0.5, 0.195, 0.94, 1.0, 12, "brass")
    bolt_ring = torus_y(0.5, 0.92, 0.5, 0.19, 0.012, 12, 4, "steel_dark")
    return along_z(Mesh().add(arm).add(flange).add(bolt_ring)).rotate_y(180, (0.5, 0.5, 0.5))


def cable_center():
    return sphere(0.5, 0.5, 0.5, 0.105, 0.105, 0.105, 10, 6, "rubber")


def cable_arm():
    arm = cylinder_y(0.5, 0.5, 0.07, 0.5, 1.0, 8, "rubber", cap_bottom=False, cap_top=False)
    plug = cylinder_y(0.5, 0.5, 0.09, 0.93, 0.99, 8, "copper")
    return along_z(Mesh().add(arm).add(plug)).rotate_y(180, (0.5, 0.5, 0.5))


def oil_tank():
    m = Mesh()
    cx = cz = 0.5
    m.add(cylinder_y(cx, cz, 0.44, 0.1, 0.84, 28, "column_white", cap_top=False))
    m.add(sphere(cx, 0.84, cz, 0.44, 0.12, 0.44, 28, 5, "column_white", lat_from=0, lat_to=90))
    for y in (0.3, 0.62):
        m.add(torus_y(cx, y, cz, 0.445, 0.018, 28, 4, "steel"))
    for i in range(4):
        a = math.pi / 4 + i * math.pi / 2
        m.add(cylinder_y(cx + 0.33 * math.cos(a), cz + 0.33 * math.sin(a), 0.045, 0.0, 0.11, 8, "steel_dark"))
    m.add(box(0.455, 0.18, 0.045, 0.545, 0.78, 0.075, "glass_dark"))
    m.add(box(0.44, 0.16, 0.05, 0.56, 0.18, 0.08, "steel"))
    m.add(box(0.44, 0.78, 0.05, 0.56, 0.8, 0.08, "steel"))
    m.add(cylinder_y(cx, cz, 0.1, 0.94, 1.0, 12, "steel_dark"))
    m.add(box(0.2, 0.9, 0.55, 0.3, 0.94, 0.65, "paint_red"))  # vent
    m.add(box(0.12, 0.72, 0.42, 0.14, 0.82, 0.58, "brass"))
    return m


# ----------------------------------------------------------------------------- datacenter
RACK_BAY0 = 0.12
RACK_BAY_H = 0.21
RACK_BAYS = 8


def server_rack():
    m = Mesh()
    for x0 in (0.04, 0.9):
        for z0 in (0.04, 0.9):
            m.add(box(x0, 0, z0, x0 + 0.06, 2.0, z0 + 0.06, "steel_dark"))
    for x0 in (0.05, 0.91):
        m.add(box(x0, 0.06, 0.1, x0 + 0.04, 1.94, 0.9, "server_side"))
    m.add(box(0.1, 0.06, 0.9, 0.9, 1.94, 0.94, "server_back"))
    m.add(box(0.04, 1.94, 0.04, 0.96, 2.0, 0.96, "steel_dark"))
    m.add(box(0.04, 0.0, 0.04, 0.96, 0.06, 0.96, "steel_dark"))
    m.add(box(0.1, 0.06, 0.84, 0.9, 1.94, 0.9, "server_bay"))
    for i in range(RACK_BAYS + 1):
        y = RACK_BAY0 + i * RACK_BAY_H
        m.add(box(0.1, y - 0.008, 0.1, 0.9, y + 0.004, 0.84, "steel"))
    # status display above the bays
    m.add(box(0.12, 1.82, 0.09, 0.88, 1.93, 0.12, "steel_dark", {"n": "screen"}))
    # brand strip at the bottom
    m.add(box(0.1, 0.06, 0.09, 0.9, 0.1, 0.12, "led_blue"))
    # cable bundle on top
    for i, (mat, x) in enumerate((("rubber", 0.3), ("paint_blue", 0.4), ("paint_yellow", 0.5), ("rubber", 0.62))):
        m.add(bezier_tube((x, 2.0, 0.92), (x, 2.1 + 0.02 * i, 0.88), (x + 0.05, 2.08, 0.35), (x + 0.1, 2.0, 0.12), 0.028, 10, 6, mat))
    m.add(box(0.25, 2.0, 0.1, 0.8, 2.03, 0.96, "grating"))
    return m


def gpu_blade():
    """One accelerator blade sitting in bay 0 (renderer translates it per slot)."""
    y0 = RACK_BAY0 + 0.012
    y1 = RACK_BAY0 + RACK_BAY_H - 0.012
    m = box(0.12, y0, 0.12, 0.88, y1, 0.84, "gpu_side",
            {"n": "gpu_front", "u": "steel_dark", "d": "steel_dark"})
    for x in (0.14, 0.82):
        m.add(box(x, y0 + 0.03, 0.085, x + 0.04, y1 - 0.03, 0.12, "brass"))
    return m


def cooling_unit(active):
    m = Mesh()
    h = 0.78
    m.add(box(0.1, 0, 0.04, 0.9, h, 0.96, "plastic_white"))
    m.add(box(0.04, 0, 0.1, 0.96, h, 0.9, "plastic_white"))
    for x in (0.1, 0.9):
        for z in (0.1, 0.9):
            m.add(cylinder_y(x, z, 0.06, 0, h, 8, "plastic_white"))
    for rot in (0, 90, 180, 270):
        panel = box(0.22, 0.12, 0.03, 0.78, 0.58, 0.041, "vent")
        m.add(panel.rotate_y(rot, (0.5, 0.5, 0.5)))
    m.add(box(0.3, 0.66, 0.028, 0.7, 0.71, 0.041, "led_blue" if active else "steel_dark"))
    m.add(washer_y(0.5, 0.5, 0.37, 0.45, h, 0.88, 24, "steel_dark"))
    m.add(disc_y(0.5, 0.5, 0.37, h + 0.002, 24, "rubber"))
    m.add(disc_y(0.5, 0.5, 0.37, 0.875, 24, "fan_grille", uv_r=0.5))
    m.add(disc_y(0.5, 0.5, 0.37, 0.874, 24, "fan_grille", up=False, uv_r=0.5))
    # coolant lines on the back
    m.add(bezier_tube((0.3, 0.08, 0.9), (0.3, 0.08, 1.0), (0.3, 0.7, 1.0), (0.3, 0.7, 0.9), 0.035, 10, 8, "paint_blue"))
    m.add(bezier_tube((0.7, 0.08, 0.9), (0.7, 0.08, 1.0), (0.7, 0.7, 1.0), (0.7, 0.7, 0.9), 0.035, 10, 8, "copper"))
    return m


def cooling_unit_anim():
    m = Mesh()
    m.add(cylinder_y(0.5, 0.5, 0.075, 0.79, 0.86, 10, "steel_dark"))
    b = blade(0.06, 0.34, 0.16, 0.12, 38, 20, 0.012, 4, "fan_blade").rotate_x(90)
    for i in range(7):
        m.add(b.rotate_y(i * 360 / 7).translate(0.5, 0.83, 0.5))
    return m


SCREEN_R = 2.0
SCREEN_Z = 0.6  # z of the screen centre; the edges curve towards the viewer
SCREEN_Y0, SCREEN_Y1 = 0.53, 0.97
SCREEN_X0, SCREEN_X1 = 0.06, 0.94


def _arc_z(x, offset=0.0):
    dx = x - 0.5
    return SCREEN_Z + offset - (SCREEN_R - math.sqrt(SCREEN_R ** 2 - dx * dx))


def ai_terminal():
    m = Mesh()
    profile = [(0.1, 0.0), (0.95, 0.0), (0.95, 0.48), (0.6, 0.48), (0.1, 0.34)]
    m.add(prism_x(profile, 0.06, 0.94, "plastic_dark", side_mat="plastic_white"))
    m.add(box(0.04, 0.0, 0.08, 0.96, 0.04, 0.97, "steel_dark"))
    # keyboard on the slope
    m.add(beam((0.5, 0.392, 0.24), (0.5, 0.462, 0.49), 0.62, 0.025, "plastic_dark", mats={"top": "keyboard"}))
    # curved monitor
    segs = 12
    thick = 0.035
    for i in range(segs):
        xa = SCREEN_X0 + (SCREEN_X1 - SCREEN_X0) * i / segs
        xb = SCREEN_X0 + (SCREEN_X1 - SCREEN_X0) * (i + 1) / segs
        za, zb = _arc_z(xa), _arc_z(xb)
        ua, ub = i / segs, (i + 1) / segs
        normal_a = (-(xa - 0.5) / SCREEN_R, 0, -math.sqrt(1 - ((xa - 0.5) / SCREEN_R) ** 2))
        normal_b = (-(xb - 0.5) / SCREEN_R, 0, -math.sqrt(1 - ((xb - 0.5) / SCREEN_R) ** 2))
        m.quad("screen", (xa, SCREEN_Y1, za), (xb, SCREEN_Y1, zb), (xb, SCREEN_Y0, zb), (xa, SCREEN_Y0, za),
               ((ua, 0), (ub, 0), (ub, 1), (ua, 1)), outward=(0, 0, -1),
               normals=(normal_a, normal_b, normal_b, normal_a))
        zba, zbb = za + thick, zb + thick
        m.quad("plastic_dark", (xa, SCREEN_Y1 + 0.02, zba), (xb, SCREEN_Y1 + 0.02, zbb), (xb, SCREEN_Y0 - 0.02, zbb),
               (xa, SCREEN_Y0 - 0.02, zba), outward=(0, 0, 1))
        for (y, sgn) in ((SCREEN_Y1 + 0.02, 1), (SCREEN_Y0 - 0.02, -1)):
            m.quad("plastic_dark", (xa, y, za - 0.004), (xb, y, zb - 0.004), (xb, y, zbb), (xa, y, zba), outward=(0, sgn, 0))
        # bezel strips above/below the panel
        for (ya, yb) in ((SCREEN_Y1, SCREEN_Y1 + 0.02), (SCREEN_Y0 - 0.02, SCREEN_Y0)):
            m.quad("plastic_dark", (xa, yb, za - 0.004), (xb, yb, zb - 0.004), (xb, ya, zb - 0.004), (xa, ya, za - 0.004),
                   outward=(0, 0, -1))
    for x, sgn in ((SCREEN_X0, -1), (SCREEN_X1, 1)):
        z = _arc_z(x)
        m.quad("plastic_dark", (x, SCREEN_Y1 + 0.02, z - 0.004), (x, SCREEN_Y1 + 0.02, z + thick),
               (x, SCREEN_Y0 - 0.02, z + thick), (x, SCREEN_Y0 - 0.02, z - 0.004), outward=(sgn, 0, 0))
    # stand
    m.add(beam((0.5, 0.48, 0.8), (0.5, 0.72, SCREEN_Z + thick + 0.01), 0.14, 0.05, "steel"))
    m.add(box(0.36, 0.48, 0.72, 0.64, 0.5, 0.9, "steel"))
    # model drive reader with LED
    m.add(box(0.72, 0.48, 0.74, 0.9, 0.56, 0.92, "steel_dark"))
    m.add(box(0.74, 0.535, 0.735, 0.88, 0.545, 0.74, "rubber"))
    m.add(box(0.85, 0.5, 0.734, 0.87, 0.515, 0.74, "led_green"))
    return m


def mesh_bounds_px(mesh):
    lo, hi = mesh.bbox()
    return lo, hi
