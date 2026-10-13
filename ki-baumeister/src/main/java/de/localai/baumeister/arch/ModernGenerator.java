package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.List;

/**
 * Moderne Villa: zwei versetzte Kuben, der obere kragt über den Eingang aus. Flachdächer mit dunkler Attika,
 * raumhohe Glasfronten mit Pfosten, Holzverkleidung, offener Wohn- und Küchenbereich, Treppe ins Obergeschoss,
 * Dachterrasse mit Glasgeländer, Pool mit Holzdeck, Bodenleuchten und ein schlichter Garten mit Hecke.
 */
final class ModernGenerator {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final Rng rng;

    private String plankB, woodTrap, slabB;
    private Materials.Family qz;
    private String wallB, woodB, frameB, darkB, glassB, roofB;
    private Materials.Family trim, floorF;

    private int gx0, gx1, gz0, gz1;
    private boolean upper, rightSide, hasStairs;
    private int ux0, ux1, uz0;
    private int doorX, sx, sz;

    ModernGenerator(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.rng = site.rng;
    }

    Voxels build() {
        materials();
        s.clearSite("grass_block");
        if (villaLayout()) {
            villa();
            return v;
        }
        if (!layout()) {
            pavilion();
            return v;
        }
        groundFloor();
        if (upper) upperFloor();
        stairs();
        interior();
        terrace();
        exterior();
        return v;
    }

    private void materials() {
        wallB = p.wall();
        int wk = rng.nextInt(3);
        woodB = wk == 0 ? "stripped_spruce_log" : wk == 1 ? "stripped_dark_oak_log" : "stripped_oak_log";
        plankB = B.id(wk == 0 ? "spruce_planks" : wk == 1 ? "dark_oak_planks" : "oak_planks");
        woodTrap = B.id(wk == 0 ? "spruce_trapdoor" : wk == 1 ? "dark_oak_trapdoor" : "oak_trapdoor");
        slabB = B.id(wallB);
        qz = Materials.family("smooth_quartz");
        frameB = p.frame();
        darkB = p.trim().block();
        trim = p.trim();
        floorF = p.floor();
        glassB = B.id("glass_pane");
        roofB = B.id(p.roof().block());
    }

    private boolean layout() {
        int W = s.w, H = s.h, D = s.d;
        int mx = W >= 20 ? 2 : W >= 11 ? 1 : 0;
        int mf = D >= 22 ? 5 : D >= 16 ? 4 : D >= 11 ? 2 : 0;
        int mb = D >= 28 ? 9 : D >= 20 ? 6 : D >= 14 ? 2 : 0;
        int gw = Math.min(W - 2 * mx, 25);
        int gd = Math.min(D - mf - mb, 15);
        if (gw < 7 || gd < 6 || H < 6) return false;
        gx0 = (W - gw) / 2;
        gx1 = gx0 + gw - 1;
        gz0 = mf;
        gz1 = gz0 + gd - 1;
        upper = H >= 10 && gw >= 9 && gd >= 7;
        rightSide = rng.chance(0.5);
        if (upper) {
            int uw = Math.max(7, gw * 3 / 5);
            if (rightSide) {
                ux1 = gx1;
                ux0 = gx1 - uw + 1;
            } else {
                ux0 = gx0;
                ux1 = gx0 + uw - 1;
            }
            int cant = gz0 >= 4 ? 2 : gz0 >= 2 ? 1 : 0;
            uz0 = gz0 - cant;
        }
        doorX = upper ? (ux0 + ux1) / 2 : gx0 + gw / 3;
        hasStairs = upper && gd >= 7;
        if (hasStairs) {
            sx = rightSide ? ux1 - 1 : ux0 + 1;
            sz = gz1 - 4;
        }
        return true;
    }

    private static boolean onRect(int x, int z, int ax, int az, int bx, int bz) {
        return x == ax || x == bx || z == az || z == bz;
    }

    private String wood() {
        return B.axis(woodB, 'y');
    }

    private String frame() {
        return B.axis(frameB, 'y');
    }

    /** Dünne, dunkle Attika als Ring aus oberen Halbplatten um ein Flachdach. */
    private void fascia(int ax, int az, int bx, int bz, int y) {
        for (int x = ax - 1; x <= bx + 1; x++) {
            v.setIfEmpty(x, y, az - 1, B.slab(trim, true));
            v.setIfEmpty(x, y, bz + 1, B.slab(trim, true));
        }
        for (int z = az; z <= bz; z++) {
            v.setIfEmpty(ax - 1, y, z, B.slab(trim, true));
            v.setIfEmpty(bx + 1, y, z, B.slab(trim, true));
        }
    }

    // ------------------------------------------------------------------ Baukörper

    private void groundFloor() {
        for (int x = gx0; x <= gx1; x++)
            for (int z = gz0; z <= gz1; z++) {
                boolean edge = onRect(x, z, gx0, gz0, gx1, gz1);
                v.set(x, 0, z, B.id(edge ? p.foundation() : floorF.block()));
                v.set(x, 4, z, roofB);
                if (!edge) continue;
                for (int y = 1; y <= 3; y++) v.set(x, y, z, upper ? wood() : B.id(wallB));
            }
        // Front: raumhohe Verglasung mit Pfosten, Holzfelder neben der Tür
        for (int x = gx0 + 1; x <= gx1 - 1; x++)
            for (int y = 1; y <= 3; y++) {
                boolean mullion = (x - gx0) % 4 == 0;
                v.set(x, y, gz0, Math.abs(x - doorX) == 1 ? wood() : mullion ? frame() : glassB);
            }
        v.set(doorX, 1, gz0, B.door(p.door(), Dir.SOUTH, false));
        v.set(doorX, 2, gz0, B.AIR);
        // Rückseite zum Garten: komplett verglast, Terrassentür in der Mitte
        int bx = (gx0 + gx1) / 2;
        for (int x = gx0 + 1; x <= gx1 - 1; x++)
            for (int y = 1; y <= 3; y++) v.set(x, y, gz1, (x - gx0) % 4 == 0 ? frame() : glassB);
        if (!(hasStairs && bx == sx)) {
            v.set(bx, 1, gz1, B.door(p.door(), Dir.NORTH, false));
            v.set(bx, 2, gz1, B.AIR);
        }
        // Seiten: Fensterband
        for (int z = gz0 + 2; z <= gz1 - 2; z++) {
            v.set(gx0, 2, z, glassB);
            v.set(gx1, 2, z, glassB);
        }
        fascia(gx0, gz0, gx1, gz1, 4);
    }

    private void upperFloor() {
        for (int x = ux0; x <= ux1; x++)
            for (int z = uz0; z <= gz1; z++) {
                boolean edge = onRect(x, z, ux0, uz0, ux1, gz1);
                v.set(x, 4, z, B.id(edge ? darkB : floorF.block()));
                v.set(x, 8, z, roofB);
                if (!edge) continue;
                for (int y = 5; y <= 7; y++) v.set(x, y, z, B.id(wallB));
            }
        // Front über dem Eingang: raumhoch verglast
        for (int x = ux0 + 1; x <= ux1 - 1; x++)
            for (int y = 5; y <= 7; y++) v.set(x, y, uz0, (x - ux0) % 3 == 0 ? frame() : glassB);
        // Außenseite: hohe Schlitzfenster, Rückseite: Fensterband
        int outer = rightSide ? ux1 : ux0, inner = rightSide ? ux0 : ux1;
        for (int z = uz0 + 2; z <= gz1 - 2; z += 3)
            for (int y = 5; y <= 7; y++) v.set(outer, y, z, glassB);
        for (int x = ux0 + 2; x <= ux1 - 2; x++) v.set(x, 6, gz1, glassB);
        // Tür zur Dachterrasse
        int tz = Math.max(gz0 + 1, (uz0 + gz1) / 2);
        if (inner != gx0 && inner != gx1) {
            v.set(inner, 5, tz, B.door(p.door(), rightSide ? Dir.EAST : Dir.WEST, false));
            v.set(inner, 6, tz, B.AIR);
            for (int z = tz + 2; z <= gz1 - 2; z += 2) v.set(inner, 6, z, glassB);
        }
        fascia(ux0, uz0, ux1, gz1, 8);
    }

    private void stairs() {
        if (!hasStairs) return;
        for (int i = 0; i < 3; i++) {
            int z = sz + i, y = 1 + i;
            for (int yy = 1; yy < y; yy++) v.set(sx, yy, z, B.id(floorF.block()));
            v.set(sx, y, z, B.stairs(floorF, Dir.SOUTH, false));
            v.set(sx, 4, z, B.AIR);
        }
        // Glasgeländer um die Treppenöffnung im Obergeschoss
        int rx = rightSide ? sx - 1 : sx + 1;
        for (int z = sz; z <= sz + 2; z++) v.set(rx, 5, z, glassB);
        v.set(sx, 5, sz - 1, glassB);
        // Zugang und Austritt freihalten
        s.reserve(sx, 1, sz - 1);
        s.reserve(sx, 2, sz - 1);
        for (int z = sz - 1; z <= sz + 3; z++) {
            s.reserve(sx, 5, z);
            s.reserve(rx, 5, z);
        }
    }

    private void interior() {
        int mid = (gz0 + gz1) / 2;
        s.reserve(doorX, 1, gz0 + 1);
        s.reserve((gx0 + gx1) / 2, 1, gz1 - 1);
        s.furnish(new Site.Room(gx0 + 1, gz0 + 1, gx1 - 1, mid, 1, 4, "living"));
        s.furnish(new Site.Room(gx0 + 1, mid + 1, gx1 - 1, gz1 - 1, 1, 4, "kitchen"));
        if (upper) {
            int umid = (uz0 + gz1) / 2;
            int inner = rightSide ? ux0 : ux1;
            s.reserve(inner + (rightSide ? 1 : -1), 5, Math.max(gz0 + 1, umid));
            s.furnish(new Site.Room(ux0 + 1, uz0 + 1, ux1 - 1, umid, 5, 8, "bedroom"));
            s.furnish(new Site.Room(ux0 + 1, umid + 1, ux1 - 1, gz1 - 1, 5, 8, "office"));
        }
    }

    /** Dachterrasse auf dem Erdgeschoss neben dem oberen Kubus. */
    private void terrace() {
        if (!upper) return;
        int tx0 = rightSide ? gx0 : ux1 + 1, tx1 = rightSide ? ux0 - 1 : gx1;
        if (tx1 < tx0) return;
        int outer = rightSide ? gx0 : gx1;
        for (int x = tx0; x <= tx1; x++)
            for (int z = gz0; z <= gz1; z++) v.set(x, 4, z, B.id(((x + z) & 1) == 0 ? "spruce_planks" : "dark_oak_planks"));
        for (int x = tx0; x <= tx1; x++) {
            v.set(x, 5, gz0, glassB);
            v.set(x, 5, gz1, glassB);
        }
        for (int z = gz0; z <= gz1; z++) v.set(outer, 5, z, glassB);
        // Pflanzkübel und Leuchten
        int px = rightSide ? gx0 + 1 : gx1 - 1;
        if (px >= tx0 && px <= tx1) {
            v.setIfEmpty(px, 5, gz0 + 1, B.leaves("flowering_azalea_leaves"));
            v.setIfEmpty(px, 5, gz1 - 1, B.leaves("azalea_leaves"));
            if (gz1 - gz0 >= 6) v.setIfEmpty(px, 5, (gz0 + gz1) / 2, B.lantern("lantern", false));
        }
    }

    // ------------------------------------------------------------------ Außenanlagen

    private void exterior() {
        // Weg mit Bodenleuchten, Einfahrt
        for (int z = 0; z < gz0; z++) {
            for (int x = doorX - 1; x <= doorX + 1; x++) v.set(x, 0, z, B.id(p.path()));
            if (z % 3 == 1 && z < gz0 - 1) {
                v.set(doorX - 2, 0, z, B.id("sea_lantern"));
                v.set(doorX + 2, 0, z, B.id("sea_lantern"));
            }
        }
        int dx0 = rightSide ? gx0 : gx1 - 2;
        if (gz0 >= 3 && Math.abs(dx0 + 1 - doorX) > 3) {
            for (int z = 0; z < gz0; z++)
                for (int x = dx0; x <= dx0 + 2; x++) v.set(x, 0, z, B.id("gray_concrete"));
        }
        // Holzdeck und Pool hinter dem Haus
        int pz0 = gz1 + 2, pz1 = s.d - 3;
        int px0 = gx0 + 1, px1 = Math.min(gx1 - 1, px0 + 11);
        if (pz1 - pz0 >= 2 && px1 - px0 >= 4) {
            for (int x = gx0; x <= gx1; x++)
                for (int z = gz1 + 1; z <= Math.min(s.d - 2, pz1 + 1); z++) v.set(x, 0, z, B.id("spruce_planks"));
            for (int x = px0 - 1; x <= px1 + 1; x++)
                for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                    boolean rim = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
                    if (z >= s.d - 1) continue;
                    v.set(x, 0, z, B.id(rim ? "smooth_quartz" : "water"));
                }
            // Liegen am Beckenrand
            int lx = px1 + 2;
            if (lx + 1 < gx1 && lx < s.w - 1) {
                for (int z = pz0; z <= Math.min(pz1, pz0 + 2); z += 2) {
                    v.set(lx, 1, z, B.slab(Materials.family("smooth_quartz"), false));
                    v.set(lx + 1, 1, z, B.stairs(Materials.family("smooth_quartz"), Dir.EAST, false));
                }
            }
        } else if (gz1 + 1 < s.d) {
            for (int x = gx0; x <= gx1; x++) v.set(x, 0, gz1 + 1, B.id("spruce_planks"));
        }
        // Hecke an den Seiten, Bäume vorn, Rasen mit wenigen Blumen
        if (gx0 >= 2) {
            for (int z = 1; z < s.d - 1; z++) {
                if ("minecraft:grass_block".equals(v.get(0, 0, z))) v.setIfEmpty(0, 1, z, B.leaves(p.leaves()));
                if ("minecraft:grass_block".equals(v.get(s.w - 1, 0, z))) v.setIfEmpty(s.w - 1, 1, z, B.leaves(p.leaves()));
            }
        }
        if (gz0 >= 4) {
            int tx = rightSide ? gx1 - 1 : gx0 + 1;
            if (Math.abs(tx - doorX) > 3) s.tree(tx, 1, 4);
        }
        if (s.spec.has(BuildingSpec.Feature.GARDEN) || s.spec.has(BuildingSpec.Feature.TREES)) s.garden(s.spec.has(BuildingSpec.Feature.GARDEN) ? 0.7 : 0.4);
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.015, 0.025);
    }

    // ================================================================== Große Villa (gestaffelte Etagen)
    // Mehrere versetzte Etagen mit dünnen weißen Platten, die über die Glaswände auskragen; die freien Plattenflächen
    // sind Terrassen mit Glasgeländer, Außentreppe, Liegen, Pflanzkübeln. Auf dem Sockeldeck liegt der Pool.

    private static final String LIGHT = "sea_lantern";
    private static final String HEDGE = "oak_leaves";

    private int nl, f0, rx0, rx1, rz0, rz1, ps, phase, doorX0, doorZ0;
    private boolean[][][] fp;
    private final List<int[]> rects = new ArrayList<>();
    private int[] poolRect;

    private boolean fp(int l, int x, int z) {
        return l >= 0 && l < nl && x >= 0 && x < s.w && z >= 0 && z < s.d && fp[l][x][z];
    }

    private boolean interior(int l, int x, int z) {
        return fp(l, x, z) && fp(l, x + 1, z) && fp(l, x - 1, z) && fp(l, x, z + 1) && fp(l, x, z - 1);
    }

    private boolean addRect(int l, int x0, int x1, int z0, int z1) {
        x0 = Math.max(x0, rx0);
        x1 = Math.min(x1, rx1 - 1);
        z0 = Math.max(z0, rz0 + 1);
        z1 = Math.min(z1, rz1 - 1);
        if (l >= 3 || x1 - x0 < 4 || z1 - z0 < 4) return false;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) fp[l][x][z] = true;
        rects.add(new int[] {l, x0, x1, z0, z1});
        return true;
    }

    /** Rechteck in Region-Koordinaten (Terrassenseite bei hohem x), je nach Seed gespiegelt. */
    private int[] absRect(int ra, int rb, int za, int zb) {
        int x0 = ps > 0 ? rx0 + ra : rx1 - rb, x1 = ps > 0 ? rx0 + rb : rx1 - ra;
        return new int[] {x0, x1, rz0 + za, rz0 + zb};
    }

    private int[] rect(int l, int ra, int rb, int za, int zb) {
        int[] r = absRect(ra, rb, za, zb);
        return addRect(l, r[0], r[1], r[2], r[3]) ? r : null;
    }

    /** Nächste Etage auf einem Teilstück von {@code r}: Platz für Terrasse davor bzw. daneben. */
    private int[] upperOf(int l, int[] r) {
        if (r == null || l + 1 >= nlMax) return null;
        int w = r[1] - r[0] + 1, d = r[3] - r[2] + 1;
        int pw = Math.max(7, Math.min(w, Math.round(w * (0.55f + 0.05f * rng.nextInt(3)))));
        int t = d >= 10 ? 3 : 0;
        if (pw >= w && t == 0 && w >= 10) pw = w - 3;
        int x0 = rng.chance(0.5) ? r[0] : r[1] - pw + 1;
        int[] u = {x0, x0 + pw - 1, r[2] + t, r[3]};
        return addRect(l + 1, u[0], u[1], u[2], u[3]) ? u : null;
    }

    private int nlMax;

    private boolean villaLayout() {
        int W = s.w, H = s.h, D = s.d;
        if (W < 15 || D < 15 || H < 6) return false;
        int mx = W >= 27 ? 3 : W >= 21 ? 2 : 1;
        int mf = D >= 30 ? 6 : D >= 22 ? 5 : 4;
        int mb = D >= 26 ? 2 : 1;
        int hw = Math.min(W - 2 * mx, 36), hd = Math.min(D - mf - mb, 28);
        if (hw < 13 || hd < 11) return false;
        rx0 = (W - hw) / 2;
        rx1 = rx0 + hw - 1;
        rz0 = mf;
        rz1 = rz0 + hd - 1;
        ps = rng.chance(0.5) ? 1 : -1;
        f0 = 1;
        nlMax = Math.max(1, Math.min(3, (H - 2) / 4));
        phase = rng.nextInt(5);
        nl = 3;
        fp = new boolean[3][W][D];
        int variant = rng.nextInt(3);
        int zf = Math.max(2, Math.round(hd * 0.2f)), zb = hd - 2;
        int[] l1 = null;
        switch (variant) {
            case 0 -> { // gestaffelter Stapel: Erdgeschoss links, Obergeschoss kragt über die Terrasse aus
                int w0 = Math.max(9, Math.min(Math.round(hw * 0.56f), hw - 4));
                if (rect(0, 0, w0 - 1, zf, zb) == null) return false;
                if (nlMax >= 2) l1 = rect(1, hw >= 19 ? 6 : 3, Math.min(hw - 3, w0 + (hw - w0) / 3 + 1), zf + 3, zb);
            }
            case 1 -> { // Winkel: Flügel vorn, Riegel hinten, Terrasse in der Ecke
                int bz = Math.round(hd * 0.42f), wx = Math.max(6, Math.min(Math.round(hw * 0.4f), hw - 6));
                if (rect(0, 0, hw - 1, bz, zb) == null) return false;
                rect(0, 0, wx - 1, 1, bz + 1);
                if (nlMax >= 2) {
                    l1 = rect(1, 3, hw - 2, bz + 3, zb);
                    rect(1, 0, wx - 1, 1, bz + 2);
                }
            }
            default -> { // Langer Riegel, Obergeschoss kragt nach vorn über das Deck aus
                int zb0 = Math.round(hd * 0.38f), xl = Math.max(8, Math.min(Math.round(hw * 0.55f), hw - 6));
                if (rect(0, 0, hw - 1, zb0, zb) == null) return false;
                if (nlMax >= 2) l1 = rect(1, 0, xl - 1, Math.max(1, zb0 - 3), zb);
            }
        }
        if (l1 != null && nlMax >= 3) upperOf(1, l1);
        int n = 0;
        for (int l = 0; l < 3; l++) {
            boolean any = false;
            for (int[] r : rects) if (r[0] == l) any = true;
            if (!any) break;
            n++;
        }
        nl = n;
        return nl >= 1;
    }

    private String deck(int x, int z) {
        return B.id(((x + z) & 3) < 2 ? floorF.block() : plankB);
    }

    private void villa() {
        platform();
        for (int l = 0; l < nl; l++) shell(l);
        pillars();
        interiorStairs();
        doors();
        exteriorStairs();
        terraceRails();
        lights();
        furnishVilla();
        outdoor();
    }

    private void platform() {
        for (int x = rx0; x <= rx1; x++)
            for (int z = rz0; z <= rz1; z++) {
                boolean rim = x == rx0 || x == rx1 || z == rz0 || z == rz1;
                v.set(x, f0, z, rim ? slabB : deck(x, z));
            }
    }

    // ------------------------------------------------------------------ Hülle

    private void shell(int l) {
        int fy = f0 + 4 * l, sy = fy + 4;
        for (int x = 0; x < s.w; x++)
            for (int z = 0; z < s.d; z++) {
                if (!fp(l, x, z)) continue;
                boolean wall = !interior(l, x, z);
                if (l == 0) v.set(x, fy, z, wall ? slabB : B.id(floorF.block()));
                else v.setIfEmpty(x, fy, z, slabB);
                if (wall) wallCell(l, x, z);
                v.set(x, sy, z, slabB);
            }
        // Auskragende Dachplatte: 1 Block ringsum, vorn 2 Blöcke
        for (int x = 0; x < s.w; x++)
            for (int z = 0; z < s.d; z++) {
                if (fp(l, x, z)) continue;
                boolean ring = false;
                for (int dx = -1; dx <= 1 && !ring; dx++)
                    for (int dz = -1; dz <= 1; dz++) if (fp(l, x + dx, z + dz)) ring = true;
                if (!ring && fp(l, x, z + 2) && !fp(l, x, z + 1)) ring = true;
                if (ring) v.setIfEmpty(x, sy, z, slabB);
            }
    }

    private void wallCell(int l, int x, int z) {
        int fy = f0 + 4 * l;
        boolean n = !fp(l, x, z - 1), e = !fp(l, x + 1, z), so = !fp(l, x, z + 1), w = !fp(l, x - 1, z);
        int open = (n ? 1 : 0) + (e ? 1 : 0) + (so ? 1 : 0) + (w ? 1 : 0);
        boolean diag = !fp(l, x - 1, z - 1) || !fp(l, x + 1, z - 1) || !fp(l, x - 1, z + 1) || !fp(l, x + 1, z + 1);
        Dir od = n ? Dir.NORTH : so ? Dir.SOUTH : e ? Dir.EAST : w ? Dir.WEST : null;
        if (open >= 2 || od == null) { // Ecken: weiße Pfeiler
            for (int y = fy + 1; y <= fy + 3; y++) v.set(x, y, z, slabB);
            return;
        }
        int c = od.dx == 0 ? x : z;
        int pat = Math.floorMod(c + phase + l * 2, 5);
        int ox = x + od.dx, oz = z + od.dz;
        boolean terrace = v.get(ox, fy, oz) != null && !B.isAir(v.get(ox, fy, oz));
        boolean glassy = od == Dir.NORTH || od == Dir.SOUTH || terrace || Math.floorMod(c / 5 + l, 2) == 0;
        for (int y = fy + 1; y <= fy + 3; y++) {
            String b;
            if (glassy) b = pat <= 2 ? (pat == 0 ? frame() : glassB) : B.id(plankB);
            else if (pat == 4) b = B.id(plankB);
            else b = y == fy + 2 && pat >= 1 && pat <= 3 ? glassB : slabB;
            v.set(x, y, z, b);
        }
        if (diag && glassy && pat <= 2) v.set(x, fy + 3, z, glassB);
    }

    /** Stützen unter auskragenden Wänden (Ecken weiß, dazwischen alle 5 Blöcke Holz). */
    private void pillars() {
        for (int l = 1; l < nl; l++) {
            int fy = f0 + 4 * l;
            for (int x = 0; x < s.w; x++)
                for (int z = 0; z < s.d; z++) {
                    if (!fp(l, x, z) || interior(l, x, z) || !v.isEmpty(x, fy - 1, z)) continue;
                    int open = (!fp(l, x, z - 1) ? 1 : 0) + (!fp(l, x + 1, z) ? 1 : 0) + (!fp(l, x, z + 1) ? 1 : 0)
                        + (!fp(l, x - 1, z) ? 1 : 0);
                    boolean corner = open >= 2;
                    if (!corner && Math.floorMod(x + z, 5) != 0) continue;
                    for (int y = fy - 1; y > f0 && v.isEmpty(x, y, z); y--) v.set(x, y, z, corner ? slabB : wood());
                }
        }
    }

    // ------------------------------------------------------------------ Treppen & Türen

    private void interiorStairs() {
        for (int l = 0; l + 1 < nl; l++) {
            int fy = f0 + 4 * l;
            List<int[]> cand = new ArrayList<>();
            for (int sx = 0; sx < s.w; sx++)
                for (int z = 1; z < s.d; z++)
                    for (int side = -1; side <= 1; side += 2) {
                        int rx = sx + side;
                        boolean ok = true;
                        for (int i = -1; i <= 3 && ok; i++) {
                            ok = interior(l + 1, sx, z + i) && interior(l + 1, rx, z + i);
                            if (i <= 2) ok = ok && interior(l, sx, z + i) && interior(l, rx, z + i);
                        }
                        if (ok) cand.add(new int[] {sx, z, rx});
                    }
            if (cand.isEmpty()) continue;
            int[] c = cand.get(rng.nextInt(cand.size()));
            int sx = c[0], z = c[1], rx = c[2];
            for (int i = 0; i < 3; i++) {
                int y = fy + 1 + i;
                for (int yy = fy + 1; yy < y; yy++) v.set(sx, yy, z + i, B.id(floorF.block()));
                v.set(sx, y, z + i, B.stairs(floorF, Dir.SOUTH, false));
                v.set(sx, fy + 4, z + i, B.AIR);
                v.set(rx, fy + 5, z + i, glassB);
            }
            v.set(sx, fy + 5, z - 1, glassB);
            for (int i = -1; i <= 3; i++) {
                s.reserve(sx, fy + 1, z - 1);
                s.reserve(sx, fy + 2, z - 1);
                s.reserve(sx, fy + 5, z + i);
                s.reserve(rx, fy + 5, z + i);
            }
        }
    }

    private void doors() {
        for (int l = 0; l < nl; l++) {
            int fy = f0 + 4 * l;
            List<int[]> cand = new ArrayList<>();
            for (int x = 0; x < s.w; x++)
                for (int z = 0; z < s.d; z++) {
                    if (!fp(l, x, z) || interior(l, x, z)) continue;
                    boolean n = !fp(l, x, z - 1), e = !fp(l, x + 1, z), so = !fp(l, x, z + 1), w = !fp(l, x - 1, z);
                    if ((n ? 1 : 0) + (e ? 1 : 0) + (so ? 1 : 0) + (w ? 1 : 0) != 1) continue;
                    Dir od = n ? Dir.NORTH : so ? Dir.SOUTH : e ? Dir.EAST : Dir.WEST;
                    // Nachbarn entlang der Wand müssen ebenfalls gerade Wand sein (Holzfelder links/rechts)
                    int ax = od.dx == 0 ? 1 : 0, az = od.dx == 0 ? 0 : 1;
                    if (!fp(l, x + ax, z + az) || !fp(l, x - ax, z - az) || interior(l, x + ax, z + az)
                        || interior(l, x - ax, z - az)) continue;
                    int ox = x + od.dx, oz = z + od.dz, ix = x - od.dx, iz = z - od.dz;
                    String ob = v.get(ox, fy, oz);
                    if (ob == null || B.isAir(ob) || !v.isEmpty(ox, fy + 1, oz) || !v.isEmpty(ix, fy + 1, iz)) continue;
                    if (l == 0 && od != Dir.NORTH) continue;
                    cand.add(new int[] {x, z, od.ordinal()});
                }
            if (cand.isEmpty()) continue;
            int[] pick;
            List<int[]> north = new ArrayList<>();
            for (int[] c : cand) if (c[2] == Dir.NORTH.ordinal()) north.add(c);
            List<int[]> pool = north.isEmpty() ? cand : north;
            if (l == 0) {
                int zmin = Integer.MAX_VALUE;
                for (int[] c : pool) zmin = Math.min(zmin, c[1]);
                List<int[]> front = new ArrayList<>();
                for (int[] c : pool) if (c[1] == zmin) front.add(c);
                front.sort((a, b) -> Integer.compare(a[0], b[0]));
                pick = front.get(front.size() / 2);
            } else {
                pick = pool.get(rng.nextInt(pool.size()));
            }
            Dir od = Dir.values()[pick[2]], in = od.opposite();
            v.set(pick[0], fy + 1, pick[1], B.door(p.door(), in, false));
            v.set(pick[0], fy + 2, pick[1], B.AIR);
            v.set(pick[0], fy + 3, pick[1], glassB);
            s.reserve(pick[0] + in.dx, fy + 1, pick[1] + in.dz);
            s.reserve(pick[0] + in.dx * 2, fy + 1, pick[1] + in.dz * 2);
            if (l == 0) {
                doorX0 = pick[0];
                doorZ0 = pick[1];
            }
        }
    }

    private boolean slabAt(int x, int y, int z) {
        return slabB.equals(v.get(x, y, z));
    }

    private boolean airColumn(int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) if (!v.isEmpty(x, y, z)) return false;
        return true;
    }

    private final java.util.Set<Long> noRail = new java.util.HashSet<>();

    /** Außentreppe von einer Terrasse zur nächsthöheren (3 Stufen + Plattenkante), seitlich mit Glas. */
    private void exteriorStairs() {
        for (int l = 0; l + 1 < nl; l++) {
            int sy = f0 + 4 * l + 4;
            List<int[]> cand = new ArrayList<>();
            for (int x = 0; x < s.w; x++)
                for (int z = 1; z < s.d - 1; z++)
                    for (int dx = -1; dx <= 1; dx += 2) {
                        boolean ok = true;
                        for (int i = -1; i <= 2 && ok; i++) {
                            int xi = x + dx * i;
                            for (int dz = -1; dz <= 1 && ok; dz++) {
                                if (i == -1 && dz != 0) continue;
                                ok = slabAt(xi, sy, z + dz) && airColumn(xi, z + dz, sy + 1, sy + 5);
                            }
                        }
                        int tx = x + dx * 3;
                        ok = ok && slabAt(tx, sy + 4, z) && airColumn(tx, z, sy + 5, sy + 6);
                        if (ok) cand.add(new int[] {x, z, dx});
                    }
            if (cand.isEmpty()) continue;
            int[] c = cand.get(rng.nextInt(cand.size()));
            for (int i = 0; i < 3; i++) {
                int xi = c[0] + c[2] * i, y = sy + 1 + i;
                for (int yy = sy + 1; yy < y; yy++) v.set(xi, yy, c[1], slabB);
                v.set(xi, y, c[1], B.stairs(qz, c[2] > 0 ? Dir.EAST : Dir.WEST, false));
                for (int dz = -1; dz <= 1; dz += 2)
                    for (int yy = sy + 1; yy <= y + 1; yy++) v.set(xi, yy, c[1] + dz, glassB);
                s.reserve(xi, y + 1, c[1]);
            }
            noRail.add(key(c[0] + c[2] * 3, c[1]));
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ Terrassen, Geländer, Licht

    private boolean exposed(int x, int sy, int z) {
        return slabAt(x, sy, z) && v.isEmpty(x, sy + 1, z);
    }

    private void terraceRails() {
        for (int l = 0; l < nl; l++) {
            int sy = f0 + 4 * l + 4;
            for (int x = 0; x < s.w; x++)
                for (int z = 0; z < s.d; z++) {
                    if (!exposed(x, sy, z)) continue;
                    boolean edge = !slabAt(x + 1, sy, z) || !slabAt(x - 1, sy, z) || !slabAt(x, sy, z + 1)
                        || !slabAt(x, sy, z - 1);
                    if (!edge) continue;
                    if (l == nl - 1) { // oberstes Dach: flache Attika-Lippe
                        v.set(x, sy + 1, z, B.slab(qz, false));
                    } else if (!s.isReserved(x, sy + 1, z) && !isStairTop(x, z, sy)) {
                        v.set(x, sy + 1, z, glassB);
                    }
                }
        }
    }

    private boolean isStairTop(int x, int z, int sy) {
        return noRail.contains(key(x, z));
    }

    private void lights() {
        // Bodenleuchten unter den Dachkanten (bündig in der Platte) und Deckenspots
        for (int l = 0; l < nl; l++) {
            int sy = f0 + 4 * l + 4;
            for (int x = 0; x < s.w; x++)
                for (int z = 0; z < s.d; z++) {
                    if (!slabAt(x, sy, z) || s.isReserved(x, sy, z)) continue;
                    if (noRail.contains(key(x, z))) continue;
                    if (fp(l, x, z)) {
                        if (interior(l, x, z) && Math.floorMod(x - rx0, 5) == 2 && Math.floorMod(z - rz0, 5) == 2
                            && (l == nl - 1 || !fp(l + 1, x, z)) && v.isEmpty(x, sy + 1, z)) v.set(x, sy, z, B.id(LIGHT));
                        continue;
                    }
                    boolean next = fp(l, x + 1, z) || fp(l, x - 1, z) || fp(l, x, z + 1) || fp(l, x, z - 1);
                    if (next && Math.floorMod(x + z, 4) == 0 && v.isEmpty(x, sy - 1, z)) v.set(x, sy, z, B.id(LIGHT));
                }
        }
    }

    private void furnishVilla() {
        for (int[] r : rects) {
            int l = r[0], fy = f0 + 4 * l;
            int x0 = r[1] + 1, x1 = r[2] - 1, z0 = r[3] + 1, z1 = r[4] - 1;
            String a = l == 0 ? "living" : l == 1 ? "bedroom" : "bedroom";
            String b = l == 0 ? "kitchen" : l == 1 ? "office" : "library";
            if (z1 - z0 >= 8) {
                int m = (z0 + z1) / 2;
                s.furnish(new Site.Room(x0, z0, x1, m, fy + 1, fy + 4, a));
                s.furnish(new Site.Room(x0, m + 1, x1, z1, fy + 1, fy + 4, b));
            } else if (x1 - x0 >= 11) {
                int m = (x0 + x1) / 2;
                s.furnish(new Site.Room(x0, z0, m, z1, fy + 1, fy + 4, a));
                s.furnish(new Site.Room(m + 1, z0, x1, z1, fy + 1, fy + 4, b));
            } else {
                s.furnish(new Site.Room(x0, z0, x1, z1, fy + 1, fy + 4, a));
            }
        }
    }

    // ------------------------------------------------------------------ Außenanlagen

    private boolean openSky(int x, int z) {
        return airColumn(x, z, f0 + 1, Math.min(s.h - 1, f0 + 4)) && !s.isReserved(x, f0 + 1, z)
            && !(Math.abs(x - doorX0) <= 1 && z < doorZ0);
    }

    private void loungerAt(int x, int y, int z) {
        v.set(x, y, z, B.stairs(qz, Dir.NORTH, false));
        v.set(x, y, z + 1, B.slab(qz, false));
        v.set(x, y, z + 2, B.slab(qz, false));
    }

    private boolean loungerFree(int x, int y, int z) {
        for (int i = 0; i < 3; i++) if (!v.isEmpty(x, y, z + i) || s.isReserved(x, y, z + i)) return false;
        return true;
    }

    /** Pflanzkübel: Steinkasten mit gestutzter Hecke. */
    private void planter(int x, int y, int z, int hedge) {
        if (!v.isEmpty(x, y, z) || s.isReserved(x, y, z)) return;
        v.set(x, y, z, slabB);
        for (int i = 1; i <= hedge; i++) v.setIfEmpty(x, y + i, z, B.leaves(i == hedge && rng.chance(0.25) ? "flowering_azalea_leaves" : HEDGE));
    }

    /** Hecke in einer Trapdoor-Box: Blätter in der Mitte, offene Falltüren an den vier Seiten. */
    private void trapPlanter(int x, int y, int z) {
        if (!v.isEmpty(x, y, z) || s.isReserved(x, y, z)) return;
        v.set(x, y, z, B.leaves(HEDGE));
        v.setIfEmpty(x, y + 1, z, B.leaves(HEDGE));
        for (Dir d : Dir.values()) {
            int nx = x + d.dx, nz = z + d.dz;
            if (v.isEmpty(nx, y, nz) && !s.isReserved(nx, y, nz)) v.set(nx, y, nz, B.trapdoor(woodTrap, d, false, true));
        }
    }

    private void outdoor() {
        entrance();
        pool();
        basin();
        decks();
        terracesDecor();
        garden();
    }

    private void entrance() {
        int dx0 = doorX0 > 0 || doorZ0 > 0 ? doorX0 : (rx0 + rx1) / 2;
        if (rz0 - 1 >= 0) {
            for (int x = dx0 - 2; x <= dx0 + 2; x++)
                if (x > rx0 && x < rx1) v.set(x, f0, rz0 - 1, B.stairs(qz, Dir.SOUTH, false));
            v.set(dx0 - 3, f0, rz0 - 1, slabB);
            v.set(dx0 + 3, f0, rz0 - 1, slabB);
            v.setIfEmpty(dx0 - 3, f0 + 1, rz0 - 1, B.slab(qz, false));
            v.setIfEmpty(dx0 + 3, f0 + 1, rz0 - 1, B.slab(qz, false));
        }
        // Weg mit Bodenleuchten
        for (int z = 0; z < rz0 - 1; z++) {
            for (int x = dx0 - 1; x <= dx0 + 1; x++) v.set(x, 0, z, B.id(p.path()));
            if (z % 3 == 1) {
                v.set(dx0 - 2, 0, z, B.id(LIGHT));
                v.set(dx0 + 2, 0, z, B.id(LIGHT));
            }
        }
    }

    private void pool() {
        int[] fit = null;
        for (int pw = 9; pw >= 4 && fit == null; pw--)
            for (int pd = Math.min(6, pw); pd >= 3 && fit == null; pd--)
                for (int z = rz0 + 1; z + pd + 1 <= rz1 - 1 && fit == null; z++)
                    for (int i = 0; i < s.w && fit == null; i++) {
                        int x = ps > 0 ? rx1 - 1 - (pw + 1) - i + 1 : rx0 + 1 + i;
                        boolean ok = x > rx0 && x + pw + 1 < rx1;
                        for (int xx = x; ok && xx <= x + pw + 1; xx++)
                            for (int zz = z; ok && zz <= z + pd + 1; zz++) ok = openSky(xx, zz);
                        if (ok) fit = new int[] {x + 1, x + pw, z + 1, z + pd};
                    }
        if (fit == null) return;
        poolRect = fit;
        for (int x = fit[0] - 1; x <= fit[1] + 1; x++)
            for (int z = fit[2] - 1; z <= fit[3] + 1; z++) {
                boolean rim = x < fit[0] || x > fit[1] || z < fit[2] || z > fit[3];
                if (rim) {
                    v.set(x, f0, z, B.id("smooth_quartz"));
                } else {
                    v.set(x, f0, z, B.id("water"));
                    v.set(x, f0 - 1, z, B.id("light_blue_concrete"));
                }
            }
        // Glasgeländer an drei Seiten, offen zur Hausseite
        int open = 3, dist = 99; // 0 N, 1 E, 2 S, 3 W
        int[][] probe = {{(fit[0] + fit[1]) / 2, fit[2] - 1, 0, -1}, {fit[1] + 1, (fit[2] + fit[3]) / 2, 1, 0},
            {(fit[0] + fit[1]) / 2, fit[3] + 1, 0, 1}, {fit[0] - 1, (fit[2] + fit[3]) / 2, -1, 0}};
        for (int k = 0; k < 4; k++) {
            for (int st = 1; st < 12; st++) {
                int px = probe[k][0] + probe[k][2] * st, pz = probe[k][1] + probe[k][3] * st;
                if (!v.isEmpty(px, f0 + 1, pz) || !fp(0, px, pz) && v.isSolid(px, f0 + 2, pz)) {
                    if (st < dist) {
                        dist = st;
                        open = k;
                    }
                    break;
                }
            }
        }
        for (int x = fit[0] - 1; x <= fit[1] + 1; x++)
            for (int z = fit[2] - 1; z <= fit[3] + 1; z++) {
                boolean n = z == fit[2] - 1, so = z == fit[3] + 1, w = x == fit[0] - 1, e = x == fit[1] + 1;
                if (!(n || so || w || e)) continue;
                boolean rail = n && open != 0 || e && open != 1 || so && open != 2 || w && open != 3;
                if (rail) v.setIfEmpty(x, f0 + 1, z, glassB);
            }
        // Liegen am Beckenrand auf der offenen Seite
        int[] c = {(fit[0] + fit[1]) / 2, (fit[2] + fit[3]) / 2};
        List<int[]> spots = new ArrayList<>();
        for (int x = rx0 + 1; x < rx1; x++)
            for (int z = rz0 + 1; z < rz1 - 2; z++) {
                boolean nearPool = x >= fit[0] - 3 && x <= fit[1] + 3 && z >= fit[2] - 4 && z <= fit[3] + 3
                    && !(x >= fit[0] - 1 && x <= fit[1] + 1 && z + 2 >= fit[2] - 1 && z <= fit[3] + 1);
                if (nearPool && loungerFree(x, f0 + 1, z)) spots.add(new int[] {x, z});
            }
        spots.sort((a, b) -> Integer.compare(Math.abs(a[0] - c[0]) + Math.abs(a[1] - c[1]), Math.abs(b[0] - c[0]) + Math.abs(b[1] - c[1])));
        int placed = 0;
        List<int[]> got = new ArrayList<>();
        for (int[] sp : spots) {
            boolean clash = false;
            for (int[] g : got) if (Math.abs(g[0] - sp[0]) < 2 && Math.abs(g[1] - sp[1]) < 4) clash = true;
            if (clash || !loungerFree(sp[0], f0 + 1, sp[1])) continue;
            if (Math.abs(sp[0] - doorX0) <= 2 && sp[1] <= doorZ0) continue;
            loungerAt(sp[0], f0 + 1, sp[1]);
            got.add(sp);
            if (++placed >= 4) break;
        }
    }

    /** Seitliches Wasserbecken auf Rasenhöhe mit Quarzrand. */
    private void basin() {
        int mx = ps > 0 ? s.w - 1 - rx1 : rx0;
        if (mx < 2) return;
        int xa = ps > 0 ? rx1 + 1 : 0, xb = ps > 0 ? s.w - 1 : rx0 - 1;
        int za = rz0 + 2, zb = rz1 - 2;
        for (int x = xa; x <= xb; x++)
            for (int z = za; z <= zb; z++) {
                boolean outer = ps > 0 ? x == xb : x == xa;
                boolean end = z == za || z == zb;
                v.set(x, 0, z, B.id(outer || end ? "smooth_quartz" : "water"));
            }
    }

    private void decks() {
        // Pflanzkästen mit Trapdoor-Hecken und Steinkübeln auf dem Sockeldeck, entlang der Hauswand
        int y = f0 + 1;
        List<int[]> spots = new ArrayList<>();
        for (int x = rx0 + 1; x < rx1; x++)
            for (int z = rz0 + 1; z < rz1; z++) {
                if (!v.isEmpty(x, y, z) || s.isReserved(x, y, z)) continue;
                boolean wallNext = v.isSolid(x + 1, y, z) || v.isSolid(x - 1, y, z) || v.isSolid(x, y, z + 1) || v.isSolid(x, y, z - 1);
                if (!wallNext || Math.abs(x - doorX0) <= 2 && z <= doorZ0 + 1 && z >= doorZ0 - 3) continue;
                spots.add(new int[] {x, z});
            }
        List<int[]> got = new ArrayList<>();
        int target = 3 + rng.nextInt(3);
        for (int k = 0; k < 60 && got.size() < target && !spots.isEmpty(); k++) {
            int[] sp = spots.get(rng.nextInt(spots.size()));
            boolean clash = false;
            for (int[] g : got) if (Math.abs(g[0] - sp[0]) + Math.abs(g[1] - sp[1]) < 4) clash = true;
            if (clash) continue;
            got.add(sp);
            if (got.size() % 2 == 1) trapPlanter(sp[0], y, sp[1]);
            else {
                planter(sp[0], y, sp[1], 2);
                planter(sp[0] + (v.isEmpty(sp[0] + 1, y, sp[1]) ? 1 : 0), y, sp[1] + (v.isEmpty(sp[0] + 1, y, sp[1]) ? 0 : 1), 2);
            }
        }
        // Liegen im überdachten Bereich, falls vorhanden
        int placed = 0;
        for (int x = rx0 + 1; x < rx1 && placed < 3; x++)
            for (int z = rz0 + 1; z < rz1 - 2 && placed < 3; z++) {
                if (v.get(x, f0 + 4, z) == null || B.isAir(v.get(x, f0 + 4, z))) continue;
                if (Math.abs(x - doorX0) <= 2 && z <= doorZ0) continue;
                if (!loungerFree(x, y, z) || Math.floorMod(x, 3) != 0) continue;
                boolean free = true;
                for (int i = 0; i < 3; i++) if (v.get(x, f0 + 4, z + i) == null || B.isAir(v.get(x, f0 + 4, z + i))) free = false;
                if (!free) continue;
                loungerAt(x, y, z);
                placed++;
            }
    }

    /** Liegen und Pflanzkübel auf den Dachterrassen. */
    private void terracesDecor() {
        for (int l = 0; l + 1 < nl; l++) {
            int sy = f0 + 4 * l + 4, y = sy + 1;
            // Liegen: Rücken zur Glasbrüstung (hinten = Norden), je max. 3
            int placed = 0;
            for (int z = 0; z < s.d - 2 && placed < 3; z++)
                for (int x = 0; x < s.w && placed < 3; x++) {
                    boolean ok = true;
                    for (int i = 0; i < 3 && ok; i++) ok = exposed(x, sy, z + i) && v.isEmpty(x, y, z + i) && !s.isReserved(x, y, z + i);
                    String back = v.get(x, y, z - 1);
                    ok = ok && back != null && !B.isAir(back) && Math.floorMod(x + z, 2) == 0;
                    if (!ok) continue;
                    boolean next = false;
                    for (int dx = -2; dx <= 2; dx++) if (v.get(x + dx, y, z) != null && v.get(x + dx, y, z).startsWith("minecraft:smooth_quartz_")) next = true;
                    if (next) continue;
                    loungerAt(x, y, z);
                    placed++;
                }
            // Pflanzkübel an Wänden/Geländerenden
            List<int[]> spots = new ArrayList<>();
            for (int x = 0; x < s.w; x++)
                for (int z = 0; z < s.d; z++) {
                    if (!exposed(x, sy, z) || !v.isEmpty(x, y, z) || s.isReserved(x, y, z)) continue;
                    if (v.isSolid(x + 1, y, z) || v.isSolid(x - 1, y, z) || v.isSolid(x, y, z - 1) || v.isSolid(x, y, z + 1)) spots.add(new int[] {x, z});
                }
            List<int[]> got = new ArrayList<>();
            for (int k = 0; k < 40 && got.size() < 3 && !spots.isEmpty(); k++) {
                int[] sp = spots.get(rng.nextInt(spots.size()));
                boolean clash = false;
                for (int[] g : got) if (Math.abs(g[0] - sp[0]) + Math.abs(g[1] - sp[1]) < 4) clash = true;
                if (clash || noRail.contains(key(sp[0], sp[1]))) continue;
                got.add(sp);
                planter(sp[0], y, sp[1], 2);
            }
        }
    }

    private void leafCube(int cx, int cz, int y, int r, int hgt) {
        for (int yy = y; yy < y + hgt; yy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) v.setIfEmpty(cx + dx, yy, cz + dz, B.leaves(HEDGE));
    }

    /** Einzelner „Lollipop“-Baum: kurzer Stamm, kantige Krone. */
    private void lollipop(int x, int z, int trunk) {
        if (!(v.isEmpty(x, 1, z) && "minecraft:grass_block".equals(v.get(x, 0, z)))) return;
        for (int y = 1; y <= trunk; y++) v.set(x, y, z, B.axis("oak_log", 'y'));
        leafCube(x, z, trunk + 1, 1, 2);
        v.setIfEmpty(x, trunk + 3, z, B.leaves(HEDGE));
    }

    private void garden() {
        int dx0 = doorX0 > 0 ? doorX0 : (rx0 + rx1) / 2;
        // Bäumchen auf dem Vorgarten
        int zr = Math.max(2, rz0 - 3);
        for (int x = 1; x < s.w - 1; x += 3 + rng.nextInt(2)) {
            if (Math.abs(x - dx0) <= 3) continue;
            lollipop(x, zr + (x / 3 % 2), 1 + rng.nextInt(2));
        }
        // Zypressen-Gruppe auf der Gegenseite der Terrasse
        int cx = ps > 0 ? Math.max(1, rx0 - 1) : Math.min(s.w - 2, rx1 + 1);
        int cz = rz0 + 2;
        int[][] cyp = {{0, 0, 6}, {1, 0, 5}, {0, 1, 4}, {1, 1, 7}};
        for (int[] c : cyp) {
            int px = cx + (ps > 0 ? -c[0] : c[0]), pz = cz + c[1];
            if (!"minecraft:grass_block".equals(v.get(px, 0, pz))) continue;
            for (int y = 1; y <= Math.min(c[2], s.h - 2); y++) v.setIfEmpty(px, y, pz, B.leaves(HEDGE));
        }
        // Palme auf dem Deck an der Terrassenseite
        int px = ps > 0 ? rx1 - 1 : rx0 + 1, pz = rz1 - 1;
        int trunk = Math.min(8, s.h - 4 - f0);
        if (trunk >= 4 && v.isEmpty(px, f0 + 1, pz) && airColumn(px, pz, f0 + 1, f0 + trunk + 1)) {
            for (int y = f0 + 1; y <= f0 + trunk; y++) v.set(px, y, pz, B.axis("dark_oak_log", 'y'));
            int ty = f0 + trunk + 1;
            for (Dir d : Dir.values()) {
                v.setIfEmpty(px + d.dx, ty, pz + d.dz, B.leaves("jungle_leaves"));
                v.setIfEmpty(px + d.dx * 2, ty, pz + d.dz * 2, B.leaves("jungle_leaves"));
                v.setIfEmpty(px + d.dx, ty - 1, pz + d.dz, B.leaves("jungle_leaves"));
            }
            v.setIfEmpty(px, ty, pz, B.leaves("jungle_leaves"));
            v.setIfEmpty(px, ty + 1, pz, B.leaves("jungle_leaves"));
        }
        // Hecken am Grundstücksrand
        for (int z = 1; z < s.d - 1; z++)
            for (int x : new int[] {0, s.w - 1})
                if ("minecraft:grass_block".equals(v.get(x, 0, z)) && v.isEmpty(x, 1, z) ) v.set(x, 1, z, B.leaves(HEDGE));
        for (int x = 1; x < s.w - 1; x++)
            if ("minecraft:grass_block".equals(v.get(x, 0, s.d - 1)) && v.isEmpty(x, 1, s.d - 1)) v.set(x, 1, s.d - 1, B.leaves(HEDGE));
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.015, 0.02);
    }

    /** Kleiner Kubus mit Glasfront für enge Bauplätze; winzig: nur eine Pergola-Bank mit Licht. */
    private void pavilion() {
        int W = s.w, D = s.d, H = s.h;
        int x0 = W >= 7 ? 1 : 0, x1 = W - 1 - x0, z0 = D >= 7 ? 1 : 0, z1 = D - 1 - z0;
        int top = Math.min(4, H - 1);
        if (x1 - x0 < 2 || z1 - z0 < 2 || top < 2) {
            v.set(W / 2, 1, D / 2, B.stairs(floorF, Dir.NORTH, false));
            return;
        }
        int cx = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = onRect(x, z, x0, z0, x1, z1);
                v.set(x, 0, z, B.id(edge ? p.foundation() : floorF.block()));
                v.set(x, top, z, roofB);
                if (!edge) continue;
                for (int y = 1; y < top; y++) {
                    String b = z == z0 && x != x0 && x != x1 ? glassB : x == x0 || x == x1 ? wood() : B.id(wallB);
                    v.set(x, y, z, b);
                }
            }
        if (top >= 3) {
            v.set(cx, 1, z0, B.door(p.door(), Dir.SOUTH, false));
            v.set(cx, 2, z0, B.AIR);
        } else {
            v.set(cx, 1, z0, B.AIR);
        }
        if (top + 1 < H) fascia(x0, z0, x1, z1, top);
        if (x1 - x0 >= 3 && z1 - z0 >= 3) s.hangingLight(cx, top - 1, (z0 + z1) / 2);
        s.flowersAndBushes(0, 0, W - 1, D - 1, 0.02, 0.03);
    }
}
