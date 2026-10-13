"""Software rasterizer that renders a Mesh with its textures to a PNG (for model previews / the mod logo)."""

import math

import numpy as np
from PIL import Image


def render(mesh, textures, size=512, yaw=145.0, pitch=28.0, bg=(0, 0, 0, 0), supersample=2, light_dir=(-0.4, 0.85, -0.55)):
    S = size * supersample
    tex_arrays = {k: np.asarray(v.convert("RGBA"), dtype=np.float32) / 255.0 for k, v in textures.items()}

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p
        # yaw around Y, then pitch around X (camera looks along -Z)
        x1 = x * cy + z * sy
        z1 = -x * sy + z * cy
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        return x1, y2, z2

    tris = []
    for mat, verts in mesh.faces:
        idx = [(0, 1, 2)] if len(verts) == 3 else [(0, 1, 2), (0, 2, 3)]
        for a, b, c in idx:
            tris.append((mat, verts[a], verts[b], verts[c]))

    pts = np.array([view(v[0]) for t in tris for v in t[1:]], dtype=np.float64)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    extent = max(hi[0] - lo[0], hi[1] - lo[1]) * 1.08
    scale = S / extent
    cx, cyy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    color = np.zeros((S, S, 4), dtype=np.float32)
    color[:] = np.array(bg, dtype=np.float32) / 255.0
    depth = np.full((S, S), -1e9, dtype=np.float64)
    L = np.array(light_dir, dtype=np.float64)
    L /= np.linalg.norm(L)

    for mat, v0, v1, v2 in tris:
        tex = tex_arrays.get(mat)
        if tex is None:
            continue
        P = []
        for v in (v0, v1, v2):
            x, y, z = view(v[0])
            P.append(((x - cx) * scale + S / 2, (cyy - y) * scale + S / 2, z))
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = P
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        # back-face culling (Minecraft culls clockwise-from-viewer faces)
        if area > 0:
            continue
        minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(S - 1, math.ceil(max(x0, x1, x2))))
        miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(S - 1, math.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            continue
        xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
        w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * z0 + w1 * z1 + w2 * z2
        sub_depth = depth[miny:maxy + 1, minx:maxx + 1]
        vis = inside & (z > sub_depth)
        if not vis.any():
            continue
        u = w0 * v0[1][0] + w1 * v1[1][0] + w2 * v2[1][0]
        vv = w0 * v0[1][1] + w1 * v1[1][1] + w2 * v2[1][1]
        th, tw = tex.shape[:2]
        tu = np.clip((u * tw).astype(int), 0, tw - 1)
        tv = np.clip((vv * th).astype(int), 0, th - 1)
        texel = tex[tv, tu]
        alpha_ok = texel[..., 3] > 0.1
        vis &= alpha_ok
        if not vis.any():
            continue
        n = np.array(v0[2]) * 0 + (np.array(v0[2]) + np.array(v1[2]) + np.array(v2[2])) / 3
        n = n / (np.linalg.norm(n) + 1e-9)
        lam = 0.55 + 0.45 * max(0.0, float(np.dot(n, L)))
        from meshlib import EMISSIVE
        if mat in EMISSIVE:
            lam = 1.0
        shaded = texel[..., :3] * lam
        sub_col = color[miny:maxy + 1, minx:maxx + 1]
        sub_col[vis, :3] = shaded[vis]
        sub_col[vis, 3] = 1.0
        sub_depth[vis] = z[vis]

    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), "RGBA")
    if supersample > 1:
        img = img.resize((size, size), Image.LANCZOS)
    return img
