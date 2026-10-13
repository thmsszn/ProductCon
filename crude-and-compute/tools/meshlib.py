"""Tiny procedural mesh toolkit that writes Wavefront OBJ files for NeoForge's OBJ loader.

Coordinates are in block units (1.0 = one block), Y is up and models face north (-Z) by default.
Faces are triangles or quads only (the NeoForge loader ignores vertices beyond the fourth).
UVs follow Minecraft's convention (v = 0 is the top row of the texture), so models are loaded
with "flip_v": false. Each material maps to exactly one block texture.
"""

import math

MODID = "crudecompute"


def v_add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def v_sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def v_mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def v_dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def v_cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def v_len(a):
    return math.sqrt(v_dot(a, a))


def v_norm(a):
    length = v_len(a)
    if length < 1e-9:
        return (0.0, 1.0, 0.0)
    return (a[0] / length, a[1] / length, a[2] / length)


def rot_x(p, deg, o=(0, 0, 0)):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v_sub(p, o)
    return v_add((x, y * c - z * s, y * s + z * c), o)


def rot_y(p, deg, o=(0, 0, 0)):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v_sub(p, o)
    return v_add((x * c + z * s, y, -x * s + z * c), o)


def rot_z(p, deg, o=(0, 0, 0)):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v_sub(p, o)
    return v_add((x * c - y * s, x * s + y * c, z), o)


def _span_uv(extent):
    """Texture span for a face of the given size: 1 block = whole texture, bigger faces are squeezed."""
    return min(abs(extent), 1.0)


class Mesh:
    def __init__(self):
        # list of (material, [(pos, uv, normal), ...])
        self.faces = []

    # ------------------------------------------------------------------ basics
    def face(self, mat, verts, outward=None):
        """Add a face. verts = [(pos, uv)] or [(pos, uv, normal)]. If outward is given the winding
        is fixed so that the face is visible from that side (Minecraft culls back faces)."""
        pts = [v[0] for v in verts]
        geo_n = v_cross(v_sub(pts[1], pts[0]), v_sub(pts[2], pts[0]))
        if v_len(geo_n) < 1e-12 and len(pts) == 4:
            geo_n = v_cross(v_sub(pts[2], pts[0]), v_sub(pts[3], pts[0]))
        if v_len(geo_n) < 1e-12:
            return
        geo_n = v_norm(geo_n)
        if outward is not None and v_dot(geo_n, outward) < 0:
            verts = list(reversed(verts))
            geo_n = v_mul(geo_n, -1)
        out = []
        for v in verts:
            n = v[2] if len(v) > 2 else geo_n
            out.append((v[0], v[1], n))
        self.faces.append((mat, out))

    def quad(self, mat, p0, p1, p2, p3, uv=((0, 0), (1, 0), (1, 1), (0, 1)), outward=None, normals=None):
        verts = []
        for i, p in enumerate((p0, p1, p2, p3)):
            if normals:
                verts.append((p, uv[i], normals[i]))
            else:
                verts.append((p, uv[i]))
        self.face(mat, verts, outward)

    def tri(self, mat, p0, p1, p2, uv=((0, 0), (1, 0), (0.5, 1)), outward=None):
        self.face(mat, [(p0, uv[0]), (p1, uv[1]), (p2, uv[2])], outward)

    def add(self, other):
        self.faces.extend(other.faces)
        return self

    def copy(self):
        m = Mesh()
        m.faces = [(mat, list(vs)) for mat, vs in self.faces]
        return m

    def map(self, fp, fn=None):
        m = Mesh()
        for mat, vs in self.faces:
            m.faces.append((mat, [(fp(p), uv, v_norm(fn(n)) if fn else n) for p, uv, n in vs]))
        return m

    def translate(self, dx, dy, dz):
        return self.map(lambda p: (p[0] + dx, p[1] + dy, p[2] + dz))

    def rotate_x(self, deg, o=(0, 0, 0)):
        return self.map(lambda p: rot_x(p, deg, o), lambda n: rot_x(n, deg))

    def rotate_y(self, deg, o=(0, 0, 0)):
        return self.map(lambda p: rot_y(p, deg, o), lambda n: rot_y(n, deg))

    def rotate_z(self, deg, o=(0, 0, 0)):
        return self.map(lambda p: rot_z(p, deg, o), lambda n: rot_z(n, deg))

    def scale(self, s, o=(0, 0, 0)):
        return self.map(lambda p: v_add(v_mul(v_sub(p, o), s), o))

    def with_material(self, mat):
        m = Mesh()
        m.faces = [(mat, vs) for _, vs in self.faces]
        return m

    def bbox(self):
        xs, ys, zs = [], [], []
        for _, vs in self.faces:
            for p, _, _ in vs:
                xs.append(p[0])
                ys.append(p[1])
                zs.append(p[2])
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def materials(self):
        seen = []
        for mat, _ in self.faces:
            if mat not in seen:
                seen.append(mat)
        return seen

    def fit_unit_cube(self, margin=0.0):
        """Uniformly scale + center into the unit cube (used for inventory item models)."""
        lo, hi = self.bbox()
        size = max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
        s = (1.0 - 2 * margin) / size
        c = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
        return self.map(lambda p: (0.5 + (p[0] - c[0]) * s, 0.5 + (p[1] - c[1]) * s, 0.5 + (p[2] - c[2]) * s))

    # ------------------------------------------------------------------ export
    def write_obj(self, path, mtl_name):
        lines = ["# Generated by tools/generate_assets.py - free-form model for Crude & Compute",
                 f"mtllib {mtl_name}"]
        vs, vts, vns = [], [], []
        vidx, tidx, nidx = {}, {}, {}

        def idx(table, store, key):
            if key not in table:
                store.append(key)
                table[key] = len(store)
            return table[key]

        body = []
        by_mat = {}
        for mat, verts in self.faces:
            by_mat.setdefault(mat, []).append(verts)
        for mat, faces in by_mat.items():
            body.append(f"usemtl {mat}")
            for verts in faces:
                refs = []
                for p, uv, n in verts:
                    pk = tuple(round(c, 5) for c in p)
                    tk = (round(uv[0], 5), round(uv[1], 5))
                    nk = tuple(round(c, 4) for c in n)
                    refs.append(f"{idx(vidx, vs, pk)}/{idx(tidx, vts, tk)}/{idx(nidx, vns, nk)}")
                body.append("f " + " ".join(refs))
        lines += [f"v {p[0]:.5f} {p[1]:.5f} {p[2]:.5f}" for p in vs]
        lines += [f"vt {t[0]:.5f} {t[1]:.5f}" for t in vts]
        lines += [f"vn {n[0]:.4f} {n[1]:.4f} {n[2]:.4f}" for n in vns]
        lines += body
        with open(path, "w", newline="\n") as f:
            f.write("\n".join(lines) + "\n")


EMISSIVE = set()


def write_mtl(path, materials):
    lines = ["# Generated by tools/generate_assets.py"]
    for mat in materials:
        lines.append(f"newmtl {mat}")
        lines.append("Kd 1.0 1.0 1.0")
        if mat in EMISSIVE:
            lines.append("Ka 1.0 1.0 1.0")
        lines.append(f"map_Kd {MODID}:block/{mat}")
        lines.append("")
    with open(path, "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------------- primitives
def box(x0, y0, z0, x1, y1, z1, mat, mats=None):
    """Axis aligned box. mats can override per face: dict with keys n,s,e,w,u,d."""
    m = Mesh()
    mats = mats or {}
    sx, sy, sz = _span_uv(x1 - x0), _span_uv(y1 - y0), _span_uv(z1 - z0)
    # north (-z)
    m.quad(mats.get("n", mat), (x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0),
           ((0, 0), (sx, 0), (sx, sy), (0, sy)), outward=(0, 0, -1))
    # south (+z)
    m.quad(mats.get("s", mat), (x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1),
           ((0, 0), (sx, 0), (sx, sy), (0, sy)), outward=(0, 0, 1))
    # west (-x)
    m.quad(mats.get("w", mat), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0),
           ((0, 0), (sz, 0), (sz, sy), (0, sy)), outward=(-1, 0, 0))
    # east (+x)
    m.quad(mats.get("e", mat), (x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1),
           ((0, 0), (sz, 0), (sz, sy), (0, sy)), outward=(1, 0, 0))
    # up
    m.quad(mats.get("u", mat), (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1),
           ((0, 0), (sx, 0), (sx, sz), (0, sz)), outward=(0, 1, 0))
    # down
    m.quad(mats.get("d", mat), (x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0),
           ((0, 0), (sx, 0), (sx, sz), (0, sz)), outward=(0, -1, 0))
    return m


def px(*vals):
    """Convert pixel units (0-16) into block units."""
    return [v / 16.0 for v in vals]


def box_px(x0, y0, z0, x1, y1, z1, mat, mats=None):
    return box(*px(x0, y0, z0, x1, y1, z1), mat, mats)


def frustum_y(cx, cz, r0, r1, y0, y1, seg, mat, cap_bottom=True, cap_top=True, cap_mat=None,
              u_repeat=1.0, smooth=True, angle0=0.0):
    """Cylinder/cone along +Y. r0 at y0, r1 at y1."""
    m = Mesh()
    h = y1 - y0
    sv = _span_uv(h)
    slope = (r0 - r1) / h if h != 0 else 0
    for i in range(seg):
        a0 = angle0 + 2 * math.pi * i / seg
        a1 = angle0 + 2 * math.pi * (i + 1) / seg
        c0, s0, c1, s1 = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
        p00 = (cx + r0 * c0, y0, cz + r0 * s0)
        p01 = (cx + r0 * c1, y0, cz + r0 * s1)
        p10 = (cx + r1 * c0, y1, cz + r1 * s0)
        p11 = (cx + r1 * c1, y1, cz + r1 * s1)
        u0, u1 = u_repeat * i / seg, u_repeat * (i + 1) / seg
        if smooth:
            n0 = v_norm((c0, slope, s0))
            n1 = v_norm((c1, slope, s1))
            normals = (n0, n1, n1, n0)
        else:
            normals = None
        mid = ((c0 + c1) / 2, slope, (s0 + s1) / 2)
        if r1 <= 1e-6:
            m.face(mat, [(p00, (u0, sv)), (p01, (u1, sv)), (p10, ((u0 + u1) / 2, 0))], outward=mid)
        elif r0 <= 1e-6:
            m.face(mat, [(p10, (u0, 0)), (p11, (u1, 0)), (p00, ((u0 + u1) / 2, sv))], outward=mid)
        else:
            m.quad(mat, p10, p11, p01, p00, ((u0, 0), (u1, 0), (u1, sv), (u0, sv)), outward=mid,
                   normals=(normals[0], normals[1], normals[1], normals[0]) if normals else None)
    cm = cap_mat or mat
    if cap_top and r1 > 1e-6:
        m.add(disc_y(cx, cz, r1, y1, seg, cm, up=True, angle0=angle0))
    if cap_bottom and r0 > 1e-6:
        m.add(disc_y(cx, cz, r0, y0, seg, cm, up=False, angle0=angle0))
    return m


def cylinder_y(cx, cz, r, y0, y1, seg, mat, **kw):
    return frustum_y(cx, cz, r, r, y0, y1, seg, mat, **kw)


def disc_y(cx, cz, r, y, seg, mat, up=True, angle0=0.0, uv_r=None):
    m = Mesh()
    ur = uv_r if uv_r is not None else min(0.5, r)
    centre = (cx, y, cz)
    for i in range(seg):
        a0 = angle0 + 2 * math.pi * i / seg
        a1 = angle0 + 2 * math.pi * (i + 1) / seg
        p0 = (cx + r * math.cos(a0), y, cz + r * math.sin(a0))
        p1 = (cx + r * math.cos(a1), y, cz + r * math.sin(a1))
        uv0 = (0.5 + ur * math.cos(a0), 0.5 + ur * math.sin(a0))
        uv1 = (0.5 + ur * math.cos(a1), 0.5 + ur * math.sin(a1))
        m.face(mat, [(centre, (0.5, 0.5)), (p0, uv0), (p1, uv1)], outward=(0, 1 if up else -1, 0))
    return m


def ring_y(cx, cz, r_in, r_out, y, seg, mat, up=True):
    """Flat annulus facing up (or down)."""
    m = Mesh()
    for i in range(seg):
        a0 = 2 * math.pi * i / seg
        a1 = 2 * math.pi * (i + 1) / seg
        pts = [(cx + r_in * math.cos(a0), y, cz + r_in * math.sin(a0)),
               (cx + r_out * math.cos(a0), y, cz + r_out * math.sin(a0)),
               (cx + r_out * math.cos(a1), y, cz + r_out * math.sin(a1)),
               (cx + r_in * math.cos(a1), y, cz + r_in * math.sin(a1))]
        uvs = [(0.5 + 0.5 * (p[0] - cx) / r_out, 0.5 + 0.5 * (p[2] - cz) / r_out) for p in pts]
        m.face(mat, list(zip(pts, uvs)), outward=(0, 1 if up else -1, 0))
    return m


def washer_y(cx, cz, r_in, r_out, y0, y1, seg, mat, inner=True):
    """Thick ring (flange / platform / cooling fin) along Y."""
    m = Mesh()
    m.add(ring_y(cx, cz, r_in, r_out, y1, seg, mat, up=True))
    m.add(ring_y(cx, cz, r_in, r_out, y0, seg, mat, up=False))
    m.add(frustum_y(cx, cz, r_out, r_out, y0, y1, seg, mat, cap_bottom=False, cap_top=False))
    if inner:
        inner_m = frustum_y(cx, cz, r_in, r_in, y0, y1, seg, mat, cap_bottom=False, cap_top=False)
        # flip inner faces so they point towards the axis
        flipped = Mesh()
        for mat_, vs in inner_m.faces:
            flipped.faces.append((mat_, [(p, uv, v_mul(n, -1)) for p, uv, n in reversed(vs)]))
        m.add(flipped)
    return m


def torus_y(cx, cy, cz, big_r, small_r, seg_u, seg_v, mat, arc_deg=360.0, arc_start=0.0):
    """Torus around the Y axis (ring lying flat). arc_deg < 360 makes a bent pipe."""
    m = Mesh()
    for i in range(seg_u):
        a0 = math.radians(arc_start + arc_deg * i / seg_u)
        a1 = math.radians(arc_start + arc_deg * (i + 1) / seg_u)
        for j in range(seg_v):
            b0 = 2 * math.pi * j / seg_v
            b1 = 2 * math.pi * (j + 1) / seg_v

            def pt(a, b):
                r = big_r + small_r * math.cos(b)
                return (cx + r * math.cos(a), cy + small_r * math.sin(b), cz + r * math.sin(a))

            def nrm(a, b):
                return (math.cos(b) * math.cos(a), math.sin(b), math.cos(b) * math.sin(a))

            ps = [pt(a0, b0), pt(a1, b0), pt(a1, b1), pt(a0, b1)]
            ns = [nrm(a0, b0), nrm(a1, b0), nrm(a1, b1), nrm(a0, b1)]
            uvs = [(i / seg_u, j / seg_v), ((i + 1) / seg_u, j / seg_v),
                   ((i + 1) / seg_u, (j + 1) / seg_v), (i / seg_u, (j + 1) / seg_v)]
            outward = v_norm(v_add(v_add(ns[0], ns[1]), v_add(ns[2], ns[3])))
            m.quad(mat, *ps, uv=uvs, outward=outward, normals=ns)
    return m


def sphere(cx, cy, cz, rx, ry, rz, seg_u, seg_v, mat, lat_from=-90.0, lat_to=90.0, cap=False):
    """(Partial) ellipsoid. lat range in degrees (-90 bottom .. 90 top)."""
    m = Mesh()
    for j in range(seg_v):
        t0 = math.radians(lat_from + (lat_to - lat_from) * j / seg_v)
        t1 = math.radians(lat_from + (lat_to - lat_from) * (j + 1) / seg_v)
        for i in range(seg_u):
            a0 = 2 * math.pi * i / seg_u
            a1 = 2 * math.pi * (i + 1) / seg_u

            def pt(a, t):
                return (cx + rx * math.cos(t) * math.cos(a), cy + ry * math.sin(t), cz + rz * math.cos(t) * math.sin(a))

            def nrm(a, t):
                return v_norm((math.cos(t) * math.cos(a) / rx, math.sin(t) / ry, math.cos(t) * math.sin(a) / rz))

            ps = [pt(a0, t1), pt(a1, t1), pt(a1, t0), pt(a0, t0)]
            ns = [nrm(a0, t1), nrm(a1, t1), nrm(a1, t0), nrm(a0, t0)]
            uvs = [(i / seg_u, 1 - (j + 1) / seg_v), ((i + 1) / seg_u, 1 - (j + 1) / seg_v),
                   ((i + 1) / seg_u, 1 - j / seg_v), (i / seg_u, 1 - j / seg_v)]
            outward = v_norm(v_add(ns[0], ns[2]))
            if v_len(v_sub(ps[0], ps[1])) < 1e-7:  # pole
                m.face(mat, [(ps[0], uvs[0], ns[0]), (ps[2], uvs[2], ns[2]), (ps[3], uvs[3], ns[3])], outward)
            elif v_len(v_sub(ps[2], ps[3])) < 1e-7:
                m.face(mat, [(ps[0], uvs[0], ns[0]), (ps[1], uvs[1], ns[1]), (ps[2], uvs[2], ns[2])], outward)
            else:
                m.quad(mat, *ps, uv=uvs, outward=outward, normals=ns)
    if cap and lat_from > -90:
        y = cy + ry * math.sin(math.radians(lat_from))
        r = rx * math.cos(math.radians(lat_from))
        m.add(disc_y(cx, cz, r, y, seg_u, mat, up=False))
    return m


def beam(p0, p1, w, h, mat, up=(0, 1, 0), mats=None, end_caps=True):
    """Box of cross-section w x h spanning from p0 to p1 (any direction)."""
    d = v_sub(p1, p0)
    length = v_len(d)
    dn = v_norm(d)
    if abs(v_dot(dn, v_norm(up))) > 0.99:
        up = (1, 0, 0) if abs(dn[0]) < 0.9 else (0, 0, 1)
    side = v_norm(v_cross(dn, up))
    up2 = v_norm(v_cross(side, dn))
    hw, hh = w / 2, h / 2

    def c(t, a, b):
        base = p0 if t == 0 else p1
        return v_add(base, v_add(v_mul(side, a * hw), v_mul(up2, b * hh)))

    m = Mesh()
    mats = mats or {}
    sl = _span_uv(length)
    sw, sh = _span_uv(w), _span_uv(h)
    # long faces
    m.quad(mats.get("top", mat), c(0, -1, 1), c(0, 1, 1), c(1, 1, 1), c(1, -1, 1),
           ((0, 0), (sw, 0), (sw, sl), (0, sl)), outward=up2)
    m.quad(mats.get("bottom", mat), c(0, -1, -1), c(0, 1, -1), c(1, 1, -1), c(1, -1, -1),
           ((0, 0), (sw, 0), (sw, sl), (0, sl)), outward=v_mul(up2, -1))
    m.quad(mats.get("side", mat), c(0, 1, 1), c(0, 1, -1), c(1, 1, -1), c(1, 1, 1),
           ((0, 0), (sh, 0), (sh, sl), (0, sl)), outward=side)
    m.quad(mats.get("side", mat), c(0, -1, 1), c(0, -1, -1), c(1, -1, -1), c(1, -1, 1),
           ((0, 0), (sh, 0), (sh, sl), (0, sl)), outward=v_mul(side, -1))
    if end_caps:
        m.quad(mats.get("end", mat), c(0, -1, 1), c(0, 1, 1), c(0, 1, -1), c(0, -1, -1),
               ((0, 0), (sw, 0), (sw, sh), (0, sh)), outward=v_mul(dn, -1))
        m.quad(mats.get("end", mat), c(1, -1, 1), c(1, 1, 1), c(1, 1, -1), c(1, -1, -1),
               ((0, 0), (sw, 0), (sw, sh), (0, sh)), outward=dn)
    return m


def tube(p0, p1, r, seg, mat, caps=True, cap_mat=None):
    """Cylinder between two arbitrary points."""
    d = v_sub(p1, p0)
    length = v_len(d)
    m = cylinder_y(0, 0, r, 0, length, seg, mat, cap_bottom=caps, cap_top=caps, cap_mat=cap_mat)
    return orient_y_to(m, d).translate(*p0)


def orient_y_to(mesh, direction):
    """Rotate a mesh built along +Y so that +Y points along direction."""
    dn = v_norm(direction)
    # rotate around X to tilt into the y/z plane, then around Y
    pitch = math.degrees(math.acos(max(-1.0, min(1.0, dn[1]))))
    yaw = math.degrees(math.atan2(dn[0], dn[2]))
    return mesh.rotate_x(pitch).rotate_y(yaw)


def bezier_tube(p0, p1, p2, p3, r, seg_len, seg_round, mat):
    """Tube along a cubic Bezier curve (cables, bent pipes)."""
    def bez(t):
        mt = 1 - t
        return tuple(mt ** 3 * p0[i] + 3 * mt * mt * t * p1[i] + 3 * mt * t * t * p2[i] + t ** 3 * p3[i] for i in range(3))

    pts = [bez(i / seg_len) for i in range(seg_len + 1)]
    m = Mesh()
    prev_ring = None
    ref = (0, 1, 0)
    for k, p in enumerate(pts):
        if k == 0:
            t = v_norm(v_sub(pts[1], pts[0]))
        elif k == len(pts) - 1:
            t = v_norm(v_sub(pts[-1], pts[-2]))
        else:
            t = v_norm(v_sub(pts[k + 1], pts[k - 1]))
        if abs(v_dot(t, ref)) > 0.95:
            ref = (1, 0, 0)
        a = v_norm(v_cross(t, ref))
        b = v_norm(v_cross(t, a))
        ring = []
        for j in range(seg_round):
            ang = 2 * math.pi * j / seg_round
            n = v_add(v_mul(a, math.cos(ang)), v_mul(b, math.sin(ang)))
            ring.append((v_add(p, v_mul(n, r)), n))
        if prev_ring:
            for j in range(seg_round):
                j2 = (j + 1) % seg_round
                q = [prev_ring[j], prev_ring[j2], ring[j2], ring[j]]
                outward = v_norm(v_add(q[0][1], q[2][1]))
                uvs = [(j / seg_round, (k - 1) / seg_len), ((j + 1) / seg_round, (k - 1) / seg_len),
                       ((j + 1) / seg_round, k / seg_len), (j / seg_round, k / seg_len)]
                m.quad(mat, q[0][0], q[1][0], q[2][0], q[3][0], uv=uvs, outward=outward,
                       normals=[q[0][1], q[1][1], q[2][1], q[3][1]])
        prev_ring = ring
    return m


def prism_x(profile, x0, x1, mat, side_mat=None):
    """Extrude a convex polygon given in the (z, y) plane along X from x0 to x1."""
    m = Mesh()
    sm = side_mat or mat
    n = len(profile)
    cz = sum(p[0] for p in profile) / n
    cy = sum(p[1] for p in profile) / n
    zs = [p[0] for p in profile]
    ys = [p[1] for p in profile]
    zmin, ymin = min(zs), min(ys)
    span = max(max(zs) - zmin, max(ys) - ymin, 1e-6)
    sc = 1.0 if span <= 1 else 1.0 / span

    def uv(p):
        return ((p[0] - zmin) * sc, 1 - (p[1] - ymin) * sc)

    # side caps as triangle fans
    for i in range(1, n - 1):
        a, b, c = profile[0], profile[i], profile[i + 1]
        m.face(sm, [((x0, a[1], a[0]), uv(a)), ((x0, b[1], b[0]), uv(b)), ((x0, c[1], c[0]), uv(c))], outward=(-1, 0, 0))
        m.face(sm, [((x1, a[1], a[0]), uv(a)), ((x1, b[1], b[0]), uv(b)), ((x1, c[1], c[0]), uv(c))], outward=(1, 0, 0))
    w = _span_uv(x1 - x0)
    for i in range(n):
        a, b = profile[i], profile[(i + 1) % n]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        out = v_norm((0, mid[1] - cy, mid[0] - cz))
        el = _span_uv(math.hypot(b[0] - a[0], b[1] - a[1]))
        m.quad(mat, (x0, a[1], a[0]), (x1, a[1], a[0]), (x1, b[1], b[0]), (x0, b[1], b[0]),
               ((0, 0), (w, 0), (w, el), (0, el)), outward=out)
    return m


def annular_sector_x(cy, cz, r_in, r_out, a_from, a_to, seg, x0, x1, mat, front_mat=None):
    """A curved slab: annulus sector in the (z, y) plane around (cz, cy), extruded along X.
    Angles in degrees measured from -Z (forward) towards +Y (up)."""
    m = Mesh()
    fm = front_mat or mat

    def pt(r, a):
        rad = math.radians(a)
        return (cz - r * math.cos(rad), cy + r * math.sin(rad))  # (z, y)

    for i in range(seg):
        a0 = a_from + (a_to - a_from) * i / seg
        a1 = a_from + (a_to - a_from) * (i + 1) / seg
        o0, o1 = pt(r_out, a0), pt(r_out, a1)
        i0, i1 = pt(r_in, a0), pt(r_in, a1)
        v0, v1 = i / seg, (i + 1) / seg
        # outer curved face
        out_n = v_norm((0, (o0[1] + o1[1]) / 2 - cy, (o0[0] + o1[0]) / 2 - cz))
        m.quad(fm, (x0, o0[1], o0[0]), (x1, o0[1], o0[0]), (x1, o1[1], o1[0]), (x0, o1[1], o1[0]),
               ((0, v0), (1, v0), (1, v1), (0, v1)), outward=out_n)
        # inner curved face
        m.quad(mat, (x0, i0[1], i0[0]), (x1, i0[1], i0[0]), (x1, i1[1], i1[0]), (x0, i1[1], i1[0]),
               ((0, v0), (1, v0), (1, v1), (0, v1)), outward=v_mul(out_n, -1))
        # flat sides
        for x, sgn in ((x0, -1), (x1, 1)):
            m.quad(mat, (x, i0[1], i0[0]), (x, o0[1], o0[0]), (x, o1[1], o1[0]), (x, i1[1], i1[0]),
                   ((0, v0), (0.3, v0), (0.3, v1), (0, v1)), outward=(sgn, 0, 0))
    # end caps
    for a, sgn in ((a_from, -1), (a_to, 1)):
        o, i_ = pt(r_out, a), pt(r_in, a)
        rad = math.radians(a)
        tangent = (0, math.cos(rad) * sgn, math.sin(rad) * sgn)
        m.quad(mat, (x0, i_[1], i_[0]), (x1, i_[1], i_[0]), (x1, o[1], o[0]), (x0, o[1], o[0]),
               ((0, 0), (1, 0), (1, 0.3), (0, 0.3)), outward=tangent)
    return m


def sector_plate_x(cy, cz, r_in, r_out, a_from, a_to, seg, x0, x1, mat):
    """Pie-slice plate (e.g. pumpjack counterweight). Angles from +Z towards +Y in degrees."""
    m = Mesh()

    def pt(r, a):
        rad = math.radians(a)
        return (cz + r * math.cos(rad), cy + r * math.sin(rad))

    for i in range(seg):
        a0 = a_from + (a_to - a_from) * i / seg
        a1 = a_from + (a_to - a_from) * (i + 1) / seg
        o0, o1, i0, i1 = pt(r_out, a0), pt(r_out, a1), pt(r_in, a0), pt(r_in, a1)
        for x, sgn in ((x0, -1), (x1, 1)):
            uv = lambda p: (0.5 + (p[0] - cz) / (2 * r_out), 0.5 - (p[1] - cy) / (2 * r_out))
            m.face(mat, [((x, i0[1], i0[0]), uv(i0)), ((x, o0[1], o0[0]), uv(o0)),
                         ((x, o1[1], o1[0]), uv(o1)), ((x, i1[1], i1[0]), uv(i1))], outward=(sgn, 0, 0))
        n = v_norm((0, (o0[1] + o1[1]) / 2 - cy, (o0[0] + o1[0]) / 2 - cz))
        m.quad(mat, (x0, o0[1], o0[0]), (x1, o0[1], o0[0]), (x1, o1[1], o1[0]), (x0, o1[1], o1[0]),
               ((0, i / seg), (0.25, i / seg), (0.25, (i + 1) / seg), (0, (i + 1) / seg)), outward=n)
        if r_in > 0:
            m.quad(mat, (x0, i0[1], i0[0]), (x1, i0[1], i0[0]), (x1, i1[1], i1[0]), (x0, i1[1], i1[0]),
                   ((0, 0), (0.25, 0), (0.25, 0.2), (0, 0.2)), outward=v_mul(n, -1))
    for a, sgn in ((a_from, -1), (a_to, 1)):
        o, i_ = pt(r_out, a), pt(r_in, a)
        rad = math.radians(a)
        tangent = (0, math.cos(rad) * sgn, -math.sin(rad) * sgn)
        m.quad(mat, (x0, i_[1], i_[0]), (x1, i_[1], i_[0]), (x1, o[1], o[0]), (x0, o[1], o[0]),
               ((0, 0), (0.25, 0), (0.25, 0.5), (0, 0.5)), outward=tangent)
    return m


def blade(root_r, tip_r, root_chord, tip_chord, root_twist, tip_twist, thickness, segs, mat):
    """Twisted, tapered rotor blade along +Y in the XY plane (rotation axis = Z)."""
    m = Mesh()
    sections = []
    for i in range(segs + 1):
        t = i / segs
        r = root_r + (tip_r - root_r) * t
        chord = root_chord + (tip_chord - root_chord) * (t ** 0.8)
        twist = math.radians(root_twist + (tip_twist - root_twist) * t)
        th = thickness * (1 - 0.6 * t)
        # airfoil-ish lens: leading edge, top, trailing edge, bottom (in x/z, before twist)
        pts2 = [(-chord * 0.35, 0.0), (0.0, th), (chord * 0.65, 0.0), (0.0, -th)]
        ring = []
        for (x, z) in pts2:
            xr = x * math.cos(twist) - z * math.sin(twist)
            zr = x * math.sin(twist) + z * math.cos(twist)
            ring.append((xr, r, zr))
        sections.append(ring)
    for i in range(segs):
        a, b = sections[i], sections[i + 1]
        for j in range(4):
            j2 = (j + 1) % 4
            mid = v_mul(v_add(v_add(a[j], a[j2]), v_add(b[j], b[j2])), 0.25)
            axis_pt = (0, mid[1], 0)
            out = v_norm(v_sub(mid, axis_pt))
            m.quad(mat, a[j], a[j2], b[j2], b[j],
                   ((j / 4, 1 - i / segs), ((j + 1) / 4, 1 - i / segs), ((j + 1) / 4, 1 - (i + 1) / segs), (j / 4, 1 - (i + 1) / segs)),
                   outward=out)
    # tip + root caps
    tip = sections[-1]
    m.quad(mat, tip[0], tip[1], tip[2], tip[3], outward=(0, 1, 0))
    root = sections[0]
    m.quad(mat, root[0], root[1], root[2], root[3], outward=(0, -1, 0))
    return m
