package de.localai.baumeister.arch;

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
        woodB = rng.chance(0.5) ? "stripped_spruce_log" : "stripped_dark_oak_log";
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
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.015, 0.025);
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
