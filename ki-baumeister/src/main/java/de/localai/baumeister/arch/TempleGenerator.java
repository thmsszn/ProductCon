package de.localai.baumeister.arch;

import java.util.List;

/**
 * Tempel: antiker Ringhallentempel auf gestuftem Unterbau mit Säulenkranz (Basis, Schaft, Kapitell), Gebälk mit
 * Gesims, flachem Giebeldach aus Stufen-Halbplatten mit Giebelfeld und einer Cella mit Altar und ewiger Flamme.
 * Im japanischen Stil entsteht stattdessen ein Schrein mit Torii, Steinlaternen und geschwungenem Walmdach.
 */
final class TempleGenerator {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final Rng rng;
    private final Palette.Style style;

    private String ground, shaft, capB, entB, friezeB, cellaB, floorA, floorB;
    private Materials.Family stepF, roof;

    private int cx, st, cx0, cx1, cz0, cz1, colH, ey, half;

    TempleGenerator(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.spec = site.spec;
        this.rng = site.rng;
        this.style = site.spec.style();
    }

    Voxels build() {
        materials();
        s.clearSite(ground);
        if (style == Palette.Style.JAPANESE) {
            if (!shrine()) tiny();
            return v;
        }
        if (!layout()) {
            tiny();
            return v;
        }
        platform();
        colonnade();
        entablature();
        roof();
        cella();
        exterior();
        return v;
    }

    // ------------------------------------------------------------------ Material

    private void materials() {
        ground = style.sandy() ? "sand" : "grass_block";
        roof = p.roof();
        stepF = Materials.family("smooth_quartz");
        shaft = "quartz_pillar";
        capB = "chiseled_quartz_block";
        entB = "smooth_quartz";
        friezeB = "quartz_bricks";
        floorA = "smooth_quartz";
        floorB = "polished_diorite";
        switch (style) {
            case DESERT -> {
                stepF = Materials.family("sandstone");
                shaft = "cut_sandstone";
                capB = "chiseled_sandstone";
                entB = "smooth_sandstone";
                friezeB = "orange_terracotta";
                floorA = "smooth_sandstone";
                floorB = "cut_sandstone";
            }
            case GOTHIC -> {
                stepF = Materials.family("polished_blackstone_brick");
                shaft = "polished_blackstone";
                capB = "chiseled_polished_blackstone";
                entB = "polished_blackstone_bricks";
                friezeB = "gilded_blackstone";
                floorA = "polished_blackstone";
                floorB = "polished_deepslate";
            }
            case STEAMPUNK -> {
                stepF = Materials.family("waxed_cut_copper");
                shaft = "waxed_cut_copper";
                capB = "waxed_chiseled_copper";
                entB = "waxed_cut_copper";
                friezeB = "waxed_oxidized_cut_copper";
                floorA = "polished_andesite";
                floorB = "waxed_cut_copper";
            }
            case ORIENTAL -> {
                stepF = Materials.family("sandstone");
                shaft = "cut_sandstone";
                capB = "chiseled_sandstone";
                entB = "smooth_sandstone";
                friezeB = "blue_terracotta";
                floorA = "smooth_sandstone";
                floorB = "blue_terracotta";
            }
            case STONE, MEDIEVAL, RUSTIC, NORDIC, VIKING -> {
                stepF = Materials.family("stone_brick");
                shaft = "polished_andesite";
                capB = "chiseled_stone_bricks";
                entB = "stone_bricks";
                friezeB = "polished_andesite";
                floorA = "polished_andesite";
                floorB = "stone_bricks";
            }
            case FANTASY -> {
                stepF = Materials.family("polished_diorite");
                capB = "amethyst_block";
                entB = "calcite";
                friezeB = "polished_deepslate";
                floorA = "calcite";
                floorB = "polished_deepslate";
            }
            case MODERN -> {
                shaft = "white_concrete";
                capB = "light_gray_concrete";
                entB = "white_concrete";
                friezeB = "light_gray_concrete";
                floorA = "smooth_stone";
                floorB = "light_gray_concrete";
            }
            default -> { } // CLASSICAL, VICTORIAN: Marmor
        }
        cellaB = spec.wall() != null ? p.wall() : entB;
    }

    // ------------------------------------------------------------------ Antiker Tempel

    private boolean layout() {
        int W = s.w, H = s.h, D = s.d;
        int mx = W >= 13 ? 1 : 0;
        int mf = D >= 18 ? 3 : D >= 11 ? 1 : 0;
        int mb = D >= 16 ? 1 : 0;
        int aw = W - 2 * mx, ad = D - mf - mb;
        st = aw >= 15 && ad >= 19 ? 3 : aw >= 11 && ad >= 13 ? 2 : 1;
        int cwid = aw - 2 * st;
        int cdep = ad - 2 * st;
        if (cwid % 2 == 0) cwid--;
        if (cdep % 2 == 0) cdep--;
        cwid = Math.min(cwid, 25);
        cdep = Math.min(cdep, 2 * cwid + 1);
        if (cwid < 5 || cdep < 5 || H < 7) return false;
        cx = W / 2;
        cx0 = cx - cwid / 2;
        cx1 = cx + cwid / 2;
        cz0 = mf + st;
        cz1 = cz0 + cdep - 1;
        half = (cwid + 2) / 2;
        int rise = half / 2 + 1;
        colH = Math.min(Math.max(4, cwid * 2 / 3 + 1), H - 3 - st - rise);
        while (colH < 3 && st > 1) {
            st--;
            colH++;
        }
        if (colH < 3) return false;
        ey = st + colH + 1;
        return true;
    }

    /** Gestufter Unterbau (Krepis): jede Stufe ein Ring aus Treppen, oben eine ebene Plattform. */
    private void platform() {
        for (int k = 1; k <= st; k++) {
            int g = st - k + 1;
            int ax = cx0 - g, bx = cx1 + g, az = cz0 - g, bz = cz1 + g;
            for (int x = ax; x <= bx; x++)
                for (int z = az; z <= bz; z++) {
                    for (int y = 1; y < k; y++) v.set(x, y, z, B.id(stepF.block()));
                    boolean edge = x == ax || x == bx || z == az || z == bz;
                    String top;
                    if (!edge || k == st) top = k == st ? floorTile(x, z) : B.id(stepF.block());
                    else if (z == az) top = B.stairs(stepF, Dir.SOUTH, false);
                    else if (z == bz) top = B.stairs(stepF, Dir.NORTH, false);
                    else if (x == ax) top = B.stairs(stepF, Dir.EAST, false);
                    else top = B.stairs(stepF, Dir.WEST, false);
                    v.set(x, k, z, top);
                }
        }
    }

    private String floorTile(int x, int z) {
        return B.id(((x + z) & 1) == 0 ? floorA : floorB);
    }

    private boolean isColumn(int x, int z) {
        if (z == cz0 || z == cz1) return x >= cx0 && x <= cx1 && (x - cx0) % 2 == 0;
        if (x == cx0 || x == cx1) return z >= cz0 && z <= cz1 && (z - cz0) % 2 == 0;
        return false;
    }

    private void column(int x, int z) {
        v.set(x, st + 1, z, B.id(capB));
        for (int y = st + 2; y < st + colH; y++) v.set(x, y, z, B.axis(shaft, 'y'));
        v.set(x, st + colH, z, B.id(capB));
    }

    private void colonnade() {
        for (int x = cx0; x <= cx1; x++)
            for (int z = cz0; z <= cz1; z++)
                if (isColumn(x, z)) column(x, z);
    }

    /** Gebälk als Decke über dem ganzen Säulenkranz, Fries im Wechsel, Gesims als umlaufende Konsole. */
    private void entablature() {
        for (int x = cx0; x <= cx1; x++)
            for (int z = cz0; z <= cz1; z++) {
                boolean edge = x == cx0 || x == cx1 || z == cz0 || z == cz1;
                v.set(x, ey, z, B.id(edge && ((x + z) & 1) == 1 ? friezeB : entB));
            }
        for (int x = cx0 - 1; x <= cx1 + 1; x++) {
            v.set(x, ey, cz0 - 1, B.stairs(stepF, Dir.SOUTH, true));
            v.set(x, ey, cz1 + 1, B.stairs(stepF, Dir.NORTH, true));
        }
        for (int z = cz0; z <= cz1; z++) {
            v.set(cx0 - 1, ey, z, B.stairs(stepF, Dir.EAST, true));
            v.set(cx1 + 1, ey, z, B.stairs(stepF, Dir.WEST, true));
        }
    }

    /** Flaches Satteldach (halbe Neigung aus Halbplatten), First entlang z, Giebelfelder vorn und hinten. */
    private void roof() {
        for (int j = 0; j <= half; j++) {
            int xa = cx0 - 1 + j, xb = cx1 + 1 - j;
            if (xa > xb) break;
            int y = ey + 1 + j / 2;
            boolean upper = (j & 1) == 1;
            String slab = B.slab(roof, upper);
            for (int z = cz0 - 1; z <= cz1 + 1; z++) {
                v.set(xa, y, z, slab);
                v.set(xb, y, z, slab);
                // Giebelfelder schließen
                if (z == cz0 || z == cz1) {
                    for (int yy = ey + 1; yy < y; yy++) {
                        v.set(xa, yy, z, B.id(entB));
                        v.set(xb, yy, z, B.id(entB));
                    }
                    if (upper) {
                        v.set(xa, y, z, B.id(roof.block()));
                        v.set(xb, y, z, B.id(roof.block()));
                    }
                }
            }
        }
        // Schmuck im Giebelfeld und Akroterien
        int apex = ey + 1 + half / 2;
        if (apex - 1 > ey + 1) {
            v.set(cx, ey + 1, cz0, B.id(capB));
            v.set(cx, ey + 1, cz1, B.id(capB));
        }
        if (apex + 1 < s.h) {
            v.set(cx, apex + 1, cz0 - 1, B.id(capB));
            v.set(cx, apex + 1, cz1 + 1, B.id(capB));
        }
    }

    /** Cella mit Vorhalle: Tor, Altar mit ewiger Flamme, Feuerschalen, Hängelampen. */
    private void cella() {
        int qx0 = cx0 + 2, qx1 = cx1 - 2;
        int depth = cz1 - cz0 + 1;
        int qz0 = cz0 + (depth >= 13 ? 4 : 2), qz1 = cz1 - 2;
        int y0 = st + 1;
        if (qx1 - qx0 < 4 || qz1 - qz0 < 4) {
            // Offener Rundtempel: Altar mitten im Säulenkranz
            int zc = (cz0 + cz1) / 2;
            v.set(cx, y0, zc, B.id(capB));
            v.set(cx, y0 + 1, zc, B.with("campfire", "lit", "true", "signal_fire", "false", "facing", "north"));
            return;
        }
        for (int y = y0; y < ey; y++)
            for (int x = qx0; x <= qx1; x++)
                for (int z = qz0; z <= qz1; z++)
                    if (x == qx0 || x == qx1 || z == qz0 || z == qz1) v.set(x, y, z, B.id(cellaB));
        // Anten (Pfeiler an der Front) und Tor
        for (int y = y0; y < ey; y++) {
            v.set(qx0, y, qz0, B.axis(shaft, 'y'));
            v.set(qx1, y, qz0, B.axis(shaft, 'y'));
        }
        int dw = qx1 - qx0 >= 6 ? 1 : 0;
        int dh = Math.min(4, colH - 1);
        for (int x = cx - dw; x <= cx + dw; x++)
            for (int y = y0; y < y0 + dh; y++) v.set(x, y, qz0, B.AIR);
        if (dw == 1 && dh >= 3) {
            v.set(cx - 1, y0 + dh - 1, qz0, B.stairs(stepF, Dir.WEST, true));
            v.set(cx + 1, y0 + dh - 1, qz0, B.stairs(stepF, Dir.EAST, true));
        }
        // Altar mit ewiger Flamme, Feuerschalen daneben
        int az = qz1 - 1;
        v.set(cx, y0, az, B.id(capB));
        v.set(cx, y0 + 1, az, B.with("campfire", "lit", "true", "signal_fire", "false", "facing", "north"));
        if (qx1 - qx0 >= 6) {
            for (int x : new int[] {cx - 2, cx + 2}) {
                v.set(x, y0, az, B.axis(shaft, 'y'));
                v.set(x, y0 + 1, az, B.lantern(p.light(), false));
            }
        }
        // Läufer, Wasserbecken am Eingang, Hängelampen
        for (int z = qz0 + 1; z < az - 1; z++) v.setIfEmpty(cx, y0, z, B.id(p.carpet()));
        if (qx1 - qx0 >= 6 && qz1 - qz0 >= 6) {
            v.setIfEmpty(qx0 + 1, y0, qz0 + 1, B.with("water_cauldron", "level", "3"));
            v.setIfEmpty(qx1 - 1, y0, qz0 + 1, B.with("water_cauldron", "level", "3"));
        }
        for (int z = qz0 + 2; z < qz1; z += 3) v.setIfEmpty(cx, ey - 1, z, B.lantern(p.light(), true));
        // Licht in der Vorhalle
        v.setIfEmpty(cx, ey - 1, (cz0 + qz0) / 2, B.lantern(p.light(), true));
    }

    private void exterior() {
        int front = cz0 - st;
        for (int z = 0; z < front; z++)
            for (int x = cx - 1; x <= cx + 1; x++) v.set(x, 0, z, B.id(p.path()));
        // Feuerschalen am Aufgang
        if (front >= 3) {
            for (int x : new int[] {cx - 3, cx + 3}) {
                v.set(x, 1, front - 2, B.id(capB));
                v.set(x, 2, front - 2, B.with("campfire", "lit", "true", "signal_fire", "false", "facing", "north"));
            }
        }
        // Zypressen an den Seiten, Blumen
        int outerX0 = cx0 - st - 1, outerX1 = cx1 + st + 1;
        for (int z = cz0; z <= cz1; z += 4) {
            if (outerX0 >= 2) cypress(outerX0 - 1, z);
            if (outerX1 <= s.w - 3) cypress(outerX1 + 1, z);
        }
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.04, 0.02);
    }

    /** Schmale Säulenzypresse aus Laub. */
    private void cypress(int x, int z) {
        if (style.sandy() || !v.isEmpty(x, 1, z)) return;
        int hgt = Math.min(3 + rng.nextInt(3), s.h - 2);
        String leaves = B.leaves(style == Palette.Style.FANTASY ? "flowering_azalea_leaves" : "spruce_leaves");
        for (int y = 1; y <= hgt; y++) v.set(x, y, z, leaves);
    }

    // ------------------------------------------------------------------ Japanischer Schrein

    private boolean shrine() {
        int W = s.w, H = s.h, D = s.d;
        int mx = W >= 13 ? 2 : W >= 9 ? 1 : 0;
        int mf = D >= 22 ? 8 : D >= 14 ? 4 : D >= 10 ? 2 : 0;
        int mb = D >= 14 ? 1 : 0;
        int pw = Math.min(W - 2 * mx, 15);
        int pd = Math.min(D - mf - mb, 15);
        if (pw % 2 == 0) pw--;
        if (pw < 7 || pd < 7 || H < 9) return false;
        cx = W / 2;
        int px0 = cx - pw / 2, px1 = cx + pw / 2, pz0 = mf, pz1 = mf + pd - 1;
        int hx0 = px0 + 1, hx1 = px1 - 1, hz0 = pz0 + 1, hz1 = pz1 - 1;
        int wallTop = 5;
        Materials.Family roofF = p.roof();
        Materials.Family wood = p.trim();
        String post = B.axis(p.frame(), 'y');

        // Steinsockel mit Holzboden, Treppe vorn
        for (int x = px0; x <= px1; x++)
            for (int z = pz0; z <= pz1; z++) {
                v.set(x, 0, z, B.id(p.foundation()));
                boolean edge = x == px0 || x == px1 || z == pz0 || z == pz1;
                v.set(x, 1, z, B.id(edge ? p.foundation() : wood.block()));
            }
        for (int x = cx - 1; x <= cx + 1; x++) v.set(x, 1, pz0 - 1, B.stairs(Materials.family("stone_brick"), Dir.SOUTH, false));
        // Geländer der umlaufenden Veranda
        for (int x = px0; x <= px1; x++) {
            if (Math.abs(x - cx) > 1) v.set(x, 2, pz0, B.id(p.fence()));
            v.set(x, 2, pz1, B.id(p.fence()));
        }
        for (int z = pz0 + 1; z < pz1; z++) {
            v.set(px0, 2, z, B.id(p.fence()));
            v.set(px1, 2, z, B.id(p.fence()));
        }
        // Halle: Pfosten, weiße Wandfelder, Schiebetüren vorn, Fenster seitlich
        for (int y = 2; y <= wallTop; y++)
            for (int x = hx0; x <= hx1; x++)
                for (int z = hz0; z <= hz1; z++) {
                    if (x != hx0 && x != hx1 && z != hz0 && z != hz1) continue;
                    boolean corner = (x == hx0 || x == hx1) && (z == hz0 || z == hz1);
                    boolean postCol = corner || (z == hz0 || z == hz1) && (x - hx0) % 3 == 0 || (x == hx0 || x == hx1) && (z - hz0) % 3 == 0;
                    v.set(x, y, z, postCol ? post : y == wallTop ? B.id(wood.block()) : B.id(p.wall()));
                }
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int y = 2; y <= 4; y++) v.set(x, y, hz0, B.AIR);
        for (int z = hz0 + 2; z <= hz1 - 2; z += 3) {
            v.set(hx0, 3, z, B.id(p.window()));
            v.set(hx1, 3, z, B.id(p.window()));
        }
        // Decke, Matten, Altar, Laternen
        for (int x = hx0; x <= hx1; x++)
            for (int z = hz0; z <= hz1; z++) {
                v.set(x, wallTop + 1, z, B.id(wood.block()));
                if (x > hx0 && x < hx1 && z > hz0 && z < hz1) v.set(x, 2, z, B.id(p.carpet()));
            }
        v.set(cx, 2, hz1 - 1, B.id(wood.block()));
        v.set(cx - 1, 2, hz1 - 1, B.stairs(wood, Dir.EAST, true));
        v.set(cx + 1, 2, hz1 - 1, B.stairs(wood, Dir.WEST, true));
        v.set(cx, 3, hz1 - 1, B.with("candle", "candles", "4", "lit", "true"));
        if (cx - 2 > hx0 && hz0 + 2 < hz1) {
            v.set(cx - 2, wallTop, hz0 + 2, B.lantern(p.light(), true));
            v.set(cx + 2, wallTop, hz0 + 2, B.lantern(p.light(), true));
        }
        for (int x : new int[] {cx - 2, cx + 2}) v.set(x, wallTop, hz0 - 1, B.lantern(p.light(), true));

        // Walmdach mit breiter Traufe und aufgebogenen Ecken
        int ov = 2, base = wallTop + 1;
        int ridgeTop = base;
        for (int i = 0; ; i++) {
            int ax = hx0 - ov + i, bx = hx1 + ov - i, az = hz0 - ov + i, bz = hz1 + ov - i;
            int y = base + Math.max(0, i - 1);
            if (ax > bx || az > bz) break;
            if (y + 1 >= s.h) {
                // Kein Platz mehr: Dach flach schließen
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) v.set(x, y - 1, z, B.id(roofF.block()));
                break;
            }
            if (ax == bx || az == bz) {
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        v.set(x, y, z, B.id(roofF.block()));
                        v.set(x, y + 1, z, B.slab(roofF, false));
                    }
                ridgeTop = y + 1;
                // Firstenden etwas erhöht
                if (y + 2 < s.h) {
                    v.set(ax, y + 1, az, B.id(roofF.block()));
                    v.set(bx, y + 1, bz, B.id(roofF.block()));
                }
                break;
            }
            for (int x = ax; x <= bx; x++) {
                v.set(x, y, az, B.stairs(roofF, Dir.SOUTH, false));
                v.set(x, y, bz, B.stairs(roofF, Dir.NORTH, false));
            }
            for (int z = az + 1; z < bz; z++) {
                v.set(ax, y, z, B.stairs(roofF, Dir.EAST, false));
                v.set(bx, y, z, B.stairs(roofF, Dir.WEST, false));
            }
            if (i == 0) {
                // aufgebogene Traufecken
                for (int[] c : new int[][] {{ax, az}, {bx, az}, {ax, bz}, {bx, bz}}) {
                    v.set(c[0], y, c[1], B.id(roofF.block()));
                    v.set(c[0], y + 1, c[1], B.slab(roofF, false));
                }
            }
            ridgeTop = y;
        }

        // Torii, Steinlaternen, Kiesweg, Kirschbäume
        for (int z = 0; z < pz0 - 1; z++)
            for (int x = cx - 1; x <= cx + 1; x++) v.set(x, 0, z, B.id(p.path()));
        if (pz0 >= 5 && pw >= 9 && s.h >= 8) torii(Math.max(1, pz0 / 2 - 1));
        if (pz0 >= 3) {
            for (int x : new int[] {cx - 3, cx + 3}) toro(x, pz0 - 2);
        }
        for (int[] t : new int[][] {{2, pz0 + 1}, {s.w - 3, pz0 + 1}, {2, s.d - 3}, {s.w - 3, s.d - 3}}) {
            if (t[0] < px0 - 3 || t[0] > px1 + 3 || t[1] > pz1 + 3) s.tree(t[0], t[1], 3 + rng.nextInt(2));
        }
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.03, 0.03);
        return ridgeTop > 0;
    }

    /** Torii-Tor über dem Weg: zwei rote Pfeiler, Querbalken und dunkler, überstehender Deckbalken. */
    private void torii(int z) {
        String red = B.id("red_concrete");
        for (int y = 1; y <= 4; y++) {
            v.set(cx - 2, y, z, red);
            v.set(cx + 2, y, z, red);
        }
        for (int x = cx - 2; x <= cx + 2; x++) v.set(x, 3, z, red);
        for (int x = cx - 3; x <= cx + 3; x++) v.set(x, 5, z, B.id("polished_blackstone"));
        v.set(cx - 4, 5, z, B.stairs(Materials.family("polished_blackstone"), Dir.EAST, true));
        v.set(cx + 4, 5, z, B.stairs(Materials.family("polished_blackstone"), Dir.WEST, true));
        v.set(cx, 4, z, B.id("polished_blackstone"));
    }

    /** Steinlaterne (Toro). */
    private void toro(int x, int z) {
        if (!v.isEmpty(x, 1, z)) return;
        v.set(x, 1, z, B.id("stone_brick_wall"));
        v.set(x, 2, z, B.lantern("lantern", false));
        v.set(x, 3, z, B.slab(Materials.family("stone_brick"), false));
    }

    // ------------------------------------------------------------------ Winzig

    /** Kleiner Altar mit Feuerschale, bei etwas Platz unter vier Säulen und Dachplatte. */
    private void tiny() {
        int x = s.w / 2, z = s.d / 2;
        v.set(x, 0, z, B.id(stepF.block()));
        v.set(x, 1, z, B.id(capB));
        if (s.h >= 3) v.set(x, 2, z, B.lantern(p.light(), false));
        if (s.w >= 3 && s.d >= 3 && s.h >= 5) {
            int top = Math.min(4, s.h - 1);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    v.set(x + dx, 0, z + dz, B.id(stepF.block()));
                    if (dx != 0 && dz != 0) for (int y = 1; y < top; y++) v.set(x + dx, y, z + dz, B.axis(shaft, 'y'));
                    v.set(x + dx, top, z + dz, B.slab(stepF, false));
                }
        }
        List<String> flowers = List.of("poppy", "dandelion", "cornflower");
        for (Dir dir : Dir.values()) {
            int fx = x + 2 * dir.dx, fz = z + 2 * dir.dz;
            if (v.isEmpty(fx, 1, fz) && "minecraft:grass_block".equals(v.get(fx, 0, fz))) v.set(fx, 1, fz, B.id(rng.pick(flowers)));
        }
    }
}
