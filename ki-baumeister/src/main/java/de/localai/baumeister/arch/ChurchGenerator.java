package de.localai.baumeister.arch;

import java.util.List;

/**
 * Kirche: Langhaus mit steilem Satteldach, Strebepfeilern und hohen Buntglasfenstern, Glockenturm mit Turmhelm an der
 * Front, niedrigerer Chor mit erhöhtem Altar, Kirchenbänke, Kronleuchter und Kirchhof mit Mauer und Grabsteinen.
 * Schmale oder niedrige Bauplätze ergeben eine Kapelle mit Dachreiter, winzige einen Bildstock.
 */
final class ChurchGenerator {
    private static final List<String> STAINED = List.of("blue_stained_glass_pane", "red_stained_glass_pane",
        "yellow_stained_glass_pane", "light_blue_stained_glass_pane", "purple_stained_glass_pane",
        "orange_stained_glass_pane");
    private static final String CHAIN = B.with("iron_chain", "axis", "y");

    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final Rng rng;
    private final Palette.Style style;

    private String wallB, quoin, floorA, floorB, ground, altarB, graveWall;
    private Materials.Family roof, trim;
    private boolean plainGlass;

    // Langhaus
    private int cx, nw, x0, x1, nz0, nz1, eaveY, ridgeY;
    // Turm
    private boolean tower;
    private int tw, tx0, tx1, tz0, tz1, belfryY, towerTop, spireH;
    // Chor
    private boolean chancel;
    private int cw, chx0, chx1, cz1, chEave;
    private int windowNo;

    ChurchGenerator(Site site) {
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
        if (!layout()) {
            shrine();
            return v;
        }
        nave();
        if (chancel) chancel();
        roofs();
        if (tower) tower();
        else bellcote();
        facade();
        interior();
        exterior();
        return v;
    }

    // ------------------------------------------------------------------ Material und Grundriss

    private void materials() {
        ground = style.sandy() ? "sand" : "grass_block";
        roof = style.sandy() && spec.roofMaterial() == null ? Materials.family("red_sandstone") : p.roof();
        trim = Materials.family("stone_brick");
        wallB = p.groundWall();
        quoin = "polished_andesite";
        floorA = "polished_andesite";
        floorB = "smooth_stone";
        altarB = "chiseled_stone_bricks";
        graveWall = "stone_brick_wall";
        switch (style) {
            case CLASSICAL -> {
                quoin = "quartz_pillar";
                trim = Materials.family("quartz");
                floorA = "quartz_block";
                floorB = "polished_diorite";
                altarB = "chiseled_quartz_block";
                graveWall = "diorite_wall";
            }
            case DESERT -> {
                quoin = "cut_sandstone";
                trim = Materials.family("sandstone");
                floorA = "smooth_sandstone";
                floorB = "cut_sandstone";
                altarB = "chiseled_sandstone";
                graveWall = "sandstone_wall";
            }
            case MODERN -> {
                quoin = "light_gray_concrete";
                trim = p.trim();
                floorA = "smooth_stone";
                floorB = "light_gray_concrete";
                altarB = "smooth_quartz";
                plainGlass = true;
            }
            case NORDIC -> {
                quoin = "dark_oak_log";
                trim = p.trim();
                floorA = "spruce_planks";
                floorB = "dark_oak_planks";
                altarB = "dark_oak_planks";
                graveWall = "cobblestone_wall";
            }
            case RUSTIC -> {
                quoin = "stone_bricks";
                trim = Materials.family("cobblestone");
                floorA = "spruce_planks";
                floorB = "oak_planks";
                graveWall = "mossy_cobblestone_wall";
            }
            case JAPANESE -> {
                quoin = "stripped_dark_oak_log";
                trim = p.trim();
                floorA = "spruce_planks";
                floorB = "spruce_planks";
                altarB = "dark_oak_planks";
                plainGlass = true;
            }
            case FANTASY -> {
                floorA = "calcite";
                floorB = "polished_deepslate";
                altarB = "amethyst_block";
            }
            case VIKING -> {
                quoin = "spruce_log";
                trim = p.trim();
                floorA = "spruce_planks";
                floorB = "dark_oak_planks";
                altarB = "dark_oak_planks";
                graveWall = "mossy_cobblestone_wall";
            }
            case GOTHIC -> {
                quoin = "polished_blackstone";
                trim = p.trim();
                floorA = "polished_blackstone";
                floorB = "polished_deepslate";
                altarB = "chiseled_polished_blackstone";
                graveWall = "blackstone_wall";
            }
            case STEAMPUNK -> {
                quoin = "polished_basalt";
                trim = p.trim();
                floorA = "polished_andesite";
                floorB = "waxed_cut_copper";
                altarB = "waxed_chiseled_copper";
                graveWall = "andesite_wall";
            }
            case ORIENTAL -> {
                quoin = "cut_sandstone";
                trim = p.trim();
                floorA = "smooth_sandstone";
                floorB = "blue_terracotta";
                altarB = "chiseled_sandstone";
                graveWall = "sandstone_wall";
            }
            case VICTORIAN -> {
                quoin = "smooth_quartz";
                trim = Materials.family("quartz");
                floorB = "smooth_quartz";
            }
            default -> { }
        }
        if (spec.wall() != null) wallB = p.wall();
    }

    private boolean layout() {
        int W = s.w, H = s.h, D = s.d;
        int mx = W >= 17 ? 2 : W >= 9 ? 1 : 0;
        int mf = D >= 24 ? 3 : D >= 14 ? 2 : D >= 10 ? 1 : 0;
        int mb = D >= 24 ? 2 : D >= 14 ? 1 : 0;
        nw = Math.min(W - 2 * mx, 15);
        if (nw % 2 == 0) nw--;
        // Das Dach braucht nw/2+1 Lagen über der Traufe
        while (nw > 5 && H - 3 - (nw / 2 + 1) < 5) nw -= 2;
        int depth = D - mf - mb;
        if (nw < 5 || depth < 7 || H < 9) return false;
        cx = W / 2;
        x0 = cx - nw / 2;
        x1 = cx + nw / 2;
        int rise = nw / 2 + 1;
        eaveY = Math.min(Math.max(5, nw / 2 + 3), H - 3 - rise);
        if (eaveY < 3) return false;
        ridgeY = eaveY + rise;

        tw = nw >= 11 ? 7 : 5;
        // Glockenstuhl über dem First, darüber ein steiler Helm; bei wenig Höhe tiefer und mit flacherem Helm
        int r0 = tw / 2;
        int top = H - 1 - (2 * r0 + 2);
        belfryY = Math.min(ridgeY + 1, top - 4);
        if (belfryY < eaveY + 2) {
            top = H - 1 - (r0 + 2);
            belfryY = Math.min(ridgeY + 1, top - 4);
        }
        towerTop = belfryY + 4;
        spireH = H - 3 - towerTop;
        tower = depth >= 16 && nw >= 7 && belfryY >= eaveY + 2;
        if (tower) {
            tz0 = mf;
            tz1 = tz0 + tw - 1;
            tx0 = cx - tw / 2;
            tx1 = cx + tw / 2;
            nz0 = tz1;
        } else {
            nz0 = mf;
        }
        int end = D - 1 - mb;
        cw = nw - 4;
        int rest = end - nz0 + 1;
        chancel = nw >= 9 && rest >= 16 && eaveY >= 6;
        int cd = chancel ? Math.max(4, Math.min(cw, rest / 4)) : 0;
        nz1 = end - cd;
        cz1 = end;
        chx0 = cx - cw / 2;
        chx1 = cx + cw / 2;
        chEave = eaveY - 2;
        return true;
    }

    // ------------------------------------------------------------------ Baukörper

    private String wall() {
        return s.vary(wallB);
    }

    private String floorTile(int x, int z) {
        return B.id(((x + z) & 1) == 0 ? floorA : floorB);
    }

    private static boolean onRect(int x, int z, int ax, int az, int bx, int bz) {
        return x == ax || x == bx || z == az || z == bz;
    }

    private void box(int ax, int az, int bx, int bz, int top) {
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                boolean edge = onRect(x, z, ax, az, bx, bz);
                v.set(x, 0, z, edge ? B.id(p.foundation()) : floorTile(x, z));
                if (!edge) continue;
                for (int y = 1; y <= top; y++) v.set(x, y, z, wall());
            }
        for (int y = 1; y <= top; y++) {
            v.set(ax, y, az, B.axis(quoin, 'y'));
            v.set(bx, y, az, B.axis(quoin, 'y'));
            v.set(ax, y, bz, B.axis(quoin, 'y'));
            v.set(bx, y, bz, B.axis(quoin, 'y'));
        }
    }

    private void nave() {
        box(x0, nz0, x1, nz1, eaveY);
        for (int z = nz0; z <= nz1; z++) {
            // Sockel und Traufgesims
            v.setIfEmpty(x0 - 1, 1, z, B.stairs(trim, Dir.EAST, false));
            v.setIfEmpty(x1 + 1, 1, z, B.stairs(trim, Dir.WEST, false));
            if (eaveY >= 5) {
                v.setIfEmpty(x0 - 1, eaveY - 1, z, B.stairs(trim, Dir.EAST, true));
                v.setIfEmpty(x1 + 1, eaveY - 1, z, B.stairs(trim, Dir.WEST, true));
            }
        }
        // Joche zu je vier Blöcken: Strebepfeiler, dazwischen hohe Spitzbogenfenster
        for (int z = nz0 + 1; z < nz1; z++) {
            int k = (z - nz0) % 4;
            if (k == 0 && z < nz1 - 1 && eaveY >= 6) {
                buttress(x0 - 1, z, Dir.WEST);
                buttress(x1 + 1, z, Dir.EAST);
            } else if (k == 2 && z <= nz1 - 2) {
                lancet(x0, z, 2, eaveY - 2);
                lancet(x1, z, 2, eaveY - 2);
            }
        }
    }

    private void buttress(int x, int z, Dir out) {
        Dir in = out.opposite();
        int top = eaveY - 3;
        for (int y = 1; y <= top; y++) v.set(x, y, z, wall());
        v.set(x, top + 1, z, B.stairs(trim, in, false));
        if (nw >= 11 && top >= 4) {
            int ox = x + out.dx;
            for (int y = 1; y <= top - 2; y++) v.set(ox, y, z, wall());
            v.set(ox, top - 1, z, B.stairs(trim, in, false));
        }
    }

    private String glass() {
        if (plainGlass) return B.id(style == Palette.Style.JAPANESE ? "white_stained_glass_pane" : "glass_pane");
        return B.id(STAINED.get(windowNo % STAINED.size()));
    }

    private void lancet(int x, int z, int y0, int y1) {
        if (y1 < y0) return;
        String g = glass();
        windowNo++;
        for (int y = y0; y <= y1; y++) v.set(x, y, z, g);
    }

    private void chancel() {
        box(chx0, nz1, chx1, cz1, chEave);
        // Nave-Rückwand bleibt stehen, nur der Chorbogen wird geöffnet
        for (int y = 1; y <= eaveY; y++) {
            v.set(chx0, y, nz1, wall());
            v.set(chx1, y, nz1, wall());
        }
        for (int x = chx0 + 1; x <= chx1 - 1; x++)
            for (int y = 1; y <= chEave - 1; y++) v.set(x, y, nz1, B.AIR);
        v.set(chx0 + 1, chEave - 1, nz1, B.stairs(trim, Dir.WEST, true));
        v.set(chx1 - 1, chEave - 1, nz1, B.stairs(trim, Dir.EAST, true));
        // Altarpodest mit Stufe im Chorbogen
        for (int x = chx0 + 1; x <= chx1 - 1; x++) {
            v.set(x, 1, nz1, B.stairs(trim, Dir.SOUTH, false));
            for (int z = nz1 + 1; z <= cz1 - 1; z++) v.set(x, 1, z, floorTile(x, z));
        }
        // Seitenfenster und dreiteiliges Chorfenster über dem Altar
        int zm = (nz1 + cz1 + 1) / 2;
        if (zm < cz1) {
            lancet(chx0, zm, 3, chEave - 1);
            lancet(chx1, zm, 3, chEave - 1);
        }
        for (int x = cx - 1; x <= cx + 1; x++) lancet(x, cz1, 3, chEave - 1);
        rose(cz1, chx0, chx1, chEave);
    }

    // ------------------------------------------------------------------ Dächer

    /** Satteldach mit First entlang z über [rx0..rx1] (ungerade Breite), Traufe auf {@code eave}, Überstand 1. */
    private void gableRoof(int rx0, int rx1, int za, int zb, int eave) {
        for (int i = 0; ; i++) {
            int xa = rx0 - 1 + i, xb = rx1 + 1 - i, y = eave + i;
            if (xa > xb) break;
            for (int z = za; z <= zb; z++) {
                if (xa == xb) {
                    v.set(xa, y, z, B.id(roof.block()));
                    v.set(xa, y + 1, z, B.slab(roof, false));
                } else {
                    v.set(xa, y, z, B.stairs(roof, Dir.EAST, false));
                    v.set(xb, y, z, B.stairs(roof, Dir.WEST, false));
                }
            }
        }
    }

    private void gableWall(int rx0, int rx1, int z, int eave) {
        for (int i = 1; ; i++) {
            int xa = rx0 + i, xb = rx1 - i;
            if (xa > xb) break;
            for (int x = xa; x <= xb; x++) v.set(x, eave + i, z, wall());
        }
    }

    private void roofs() {
        gableRoof(x0, x1, nz0 - 1, chancel ? nz1 : nz1 + 1, eaveY);
        gableWall(x0, x1, nz0, eaveY);
        gableWall(x0, x1, nz1, eaveY);
        if (chancel) {
            gableRoof(chx0, chx1, nz1 + 1, cz1 + 1, chEave);
            gableWall(chx0, chx1, cz1, chEave);
        } else {
            rose(nz1, x0, x1, eaveY);
        }
    }

    /** Rundfenster (Kreuzform) im Giebel. */
    private void rose(int z, int rx0, int rx1, int eave) {
        int top = eave + (rx1 - rx0) / 2;
        String outer = plainGlass ? glass() : B.id("blue_stained_glass_pane");
        String center = plainGlass ? glass() : B.id("yellow_stained_glass_pane");
        if (eave + 3 < top) {
            int y = eave + 2;
            v.set(cx, y, z, center);
            v.set(cx - 1, y, z, outer);
            v.set(cx + 1, y, z, outer);
            v.set(cx, y - 1, z, outer);
            v.set(cx, y + 1, z, outer);
        } else if (eave + 1 < top) {
            v.set(cx, eave + 1, z, center);
        }
    }

    // ------------------------------------------------------------------ Turm

    private void tower() {
        int tzc = (tz0 + tz1) / 2;
        // Dachüberstand des Langhauses im Turm entfernen
        for (int y = 1; y <= towerTop; y++)
            for (int x = tx0 + 1; x <= tx1 - 1; x++)
                for (int z = tz0 + 1; z <= tz1 - 1; z++) v.set(x, y, z, B.AIR);
        box(tx0, tz0, tx1, tz1, towerTop);
        // Gesimsbänder
        for (int y : new int[] {eaveY + 1, belfryY, towerTop}) {
            if (y <= 2) continue;
            for (int x = tx0 - 1; x <= tx1 + 1; x++) {
                v.setIfEmpty(x, y, tz0 - 1, B.stairs(trim, Dir.SOUTH, true));
                v.setIfEmpty(x, y, tz1 + 1, B.stairs(trim, Dir.NORTH, true));
            }
            for (int z = tz0; z <= tz1; z++) {
                v.setIfEmpty(tx0 - 1, y, z, B.stairs(trim, Dir.EAST, true));
                v.setIfEmpty(tx1 + 1, y, z, B.stairs(trim, Dir.WEST, true));
            }
        }
        // Schmale Fenster im Schaft
        for (int y = Math.max(6, belfryY - 4); y <= belfryY - 2; y++) {
            v.set(cx, y, tz0, glass());
            v.set(tx0, y, tzc, glass());
            v.set(tx1, y, tzc, glass());
        }
        windowNo++;
        // Glockenstuhl mit Schallöffnungen auf allen vier Seiten
        int half = tw >= 7 ? 1 : 0;
        for (int y = belfryY + 1; y <= belfryY + 3; y++)
            for (int d = -half; d <= half; d++) {
                String fill = y == belfryY + 1 ? B.slab(trim, false) : B.AIR;
                v.set(cx + d, y, tz0, fill);
                v.set(cx + d, y, tz1, fill);
                v.set(tx0, y, tzc + d, fill);
                v.set(tx1, y, tzc + d, fill);
            }
        if (half == 1) {
            int y = belfryY + 3;
            v.set(cx - 1, y, tz0, B.stairs(trim, Dir.WEST, true));
            v.set(cx + 1, y, tz0, B.stairs(trim, Dir.EAST, true));
            v.set(cx - 1, y, tz1, B.stairs(trim, Dir.WEST, true));
            v.set(cx + 1, y, tz1, B.stairs(trim, Dir.EAST, true));
            v.set(tx0, y, tzc - 1, B.stairs(trim, Dir.NORTH, true));
            v.set(tx0, y, tzc + 1, B.stairs(trim, Dir.SOUTH, true));
            v.set(tx1, y, tzc - 1, B.stairs(trim, Dir.NORTH, true));
            v.set(tx1, y, tzc + 1, B.stairs(trim, Dir.SOUTH, true));
        }
        // Glockenboden, Decke, Glocke und Leiter
        for (int x = tx0 + 1; x <= tx1 - 1; x++)
            for (int z = tz0 + 1; z <= tz1 - 1; z++) {
                v.set(x, belfryY, z, B.id(p.floor().block()));
                v.set(x, towerTop, z, B.id(wallB));
            }
        v.set(cx, towerTop - 1, tzc, B.with("bell", "attachment", "ceiling", "facing", "north"));
        for (int y = 1; y <= belfryY; y++) v.set(tx0 + 1, y, tz0 + 1, B.ladder(Dir.EAST));
        v.set(tx1 - 1, 3, tz0 + 1, B.wallTorch(Dir.SOUTH));
        // Durchgang von der Turmhalle ins Langhaus
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int y = 1; y <= 2; y++) v.set(x, y, tz1, B.AIR);
        v.set(cx, 3, tz1, B.AIR);
        spire(cx, tzc, tw / 2, towerTop + 1, spireH);
    }

    /** Turmhelm: Pyramide aus Stufenringen (steil: je Ring ein senkrechter Block und eine Stufe) mit Spitze. */
    private void spire(int sx, int sz, int r0, int yb, int hgt) {
        boolean steep = hgt >= 2 * r0 + 1;
        if (!steep && hgt < r0 + 1) {
            for (int x = sx - r0; x <= sx + r0; x++)
                for (int z = sz - r0; z <= sz + r0; z++) {
                    boolean ring = Math.abs(x - sx) == r0 || Math.abs(z - sz) == r0;
                    if (ring && ((x + z) & 1) == 0) v.set(x, yb, z, wall());
                }
            return;
        }
        int y = yb;
        for (int r = r0; r >= 1; r--) {
            if (steep) spireRing(sx, sz, r, y++, false);
            spireRing(sx, sz, r, y++, true);
        }
        v.set(sx, y, sz, B.id(roof.block()));
        v.set(sx, y + 1, sz, B.with("end_rod", "facing", "up"));
    }

    private void spireRing(int sx, int sz, int r, int y, boolean stairs) {
        for (int x = sx - r; x <= sx + r; x++)
            for (int z = sz - r; z <= sz + r; z++) {
                if (Math.abs(x - sx) != r && Math.abs(z - sz) != r) continue;
                String st;
                if (!stairs) st = B.id(roof.block());
                else if (z == sz - r) st = B.stairs(roof, Dir.SOUTH, false);
                else if (z == sz + r) st = B.stairs(roof, Dir.NORTH, false);
                else if (x == sx - r) st = B.stairs(roof, Dir.EAST, false);
                else st = B.stairs(roof, Dir.WEST, false);
                v.set(x, y, z, st);
            }
    }

    /** Dachreiter mit Glocke über dem Frontgiebel (Kapelle ohne Turm). */
    private void bellcote() {
        int y = ridgeY + 1, z = nz0;
        if (y + 2 > s.h - 1) return;
        v.set(cx - 1, y, z, wall());
        v.set(cx + 1, y, z, wall());
        v.set(cx, y, z, B.with("bell", "attachment", "ceiling", "facing", "north"));
        v.set(cx - 1, y + 1, z, B.stairs(roof, Dir.EAST, false));
        v.set(cx, y + 1, z, B.id(roof.block()));
        v.set(cx + 1, y + 1, z, B.stairs(roof, Dir.WEST, false));
        v.set(cx, y + 2, z, B.with("end_rod", "facing", "up"));
    }

    // ------------------------------------------------------------------ Fassade und Innenraum

    private void facade() {
        int dz = tower ? tz0 : nz0;
        v.set(cx, 1, dz, B.door(p.door(), Dir.SOUTH, false));
        v.set(cx, 2, dz, B.AIR);
        if (dz >= 1) {
            // Vorgesetztes Portal mit kleinem Giebel
            for (int y = 1; y <= 3; y++) {
                v.set(cx - 1, y, dz - 1, B.axis(quoin, 'y'));
                v.set(cx + 1, y, dz - 1, B.axis(quoin, 'y'));
            }
            v.set(cx, 3, dz - 1, B.stairs(trim, Dir.SOUTH, true));
            v.set(cx - 1, 4, dz - 1, B.stairs(trim, Dir.EAST, false));
            v.set(cx, 4, dz - 1, B.id(trim.block()));
            v.set(cx + 1, 4, dz - 1, B.stairs(trim, Dir.WEST, false));
            v.set(cx, 5, dz - 1, B.slab(trim, false));
            v.set(cx - 2, 3, dz - 1, B.wallTorch(Dir.NORTH));
            v.set(cx + 2, 3, dz - 1, B.wallTorch(Dir.NORTH));
        }
        if (!tower) {
            rose(nz0, x0, x1, eaveY);
            if (nw >= 9) {
                lancet(cx - 3, nz0, 2, eaveY - 2);
                lancet(cx + 3, nz0, 2, eaveY - 2);
            }
        } else if (nw - tw >= 4) {
            // Seitliche Fenster neben dem Turm
            lancet(tx0 - 2, nz0, 2, eaveY - 2);
            lancet(tx1 + 2, nz0, 2, eaveY - 2);
        }
    }

    private void interior() {
        int altarZ, altarY;
        if (chancel) {
            altarZ = cz1 - 1;
            altarY = 2;
        } else {
            altarZ = nz1 - 1;
            altarY = 1;
            if (eaveY >= 5 && nz1 - nz0 >= 9) {
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    v.set(x, 1, nz1 - 3, B.stairs(trim, Dir.SOUTH, false));
                    v.set(x, 1, nz1 - 2, floorTile(x, nz1 - 2));
                    v.set(x, 1, nz1 - 1, floorTile(x, nz1 - 1));
                }
                altarY = 2;
            }
        }
        // Altar mit Kerzen und Laterne
        v.set(cx - 1, altarY, altarZ, B.id(altarB));
        v.set(cx, altarY, altarZ, B.id(altarB));
        v.set(cx + 1, altarY, altarZ, B.id(altarB));
        v.set(cx - 1, altarY + 1, altarZ, B.with("candle", "candles", "3", "lit", "true"));
        v.set(cx + 1, altarY + 1, altarZ, B.with("candle", "candles", "3", "lit", "true"));
        v.set(cx, altarY + 1, altarZ, B.lantern(p.light(), false));
        // Lesepult am Chorbogen
        int lz = chancel ? nz1 - 1 : nz1 - 4;
        if (lz > nz0 + 2) v.set(x1 - 2 > cx ? x1 - 2 : x1 - 1, 1, lz, B.with("lectern", "facing", "south"));

        // Kirchenbänke beidseits des Mittelgangs, Blick zum Altar (Lehne nach Norden)
        int pewEnd = chancel ? nz1 - 3 : nz1 - 5;
        int aisle = nw >= 11 ? 1 : 0;
        int inset = nw >= 9 ? 2 : 1;
        for (int z = nz0 + 3; z <= pewEnd; z += 2)
            for (int x = x0 + inset; x <= x1 - inset; x++) {
                if (Math.abs(x - cx) <= aisle) continue;
                if (v.isEmpty(x, 1, z)) v.set(x, 1, z, B.stairs(p.floor(), Dir.NORTH, false));
            }
        // Läufer im Mittelgang
        for (int z = nz0 + 1; z <= (chancel ? nz1 - 1 : nz1 - 4); z++)
            if (v.isEmpty(cx, 1, z) && v.isSolid(cx, 0, z)) v.set(cx, 1, z, B.id(p.carpet()));
        // Kronleuchter unter dem First
        for (int z = nz0 + 3; z <= nz1 - 3; z += 5) chandelier(cx, z);
        // Wandleuchter an den Pfeilern
        if (eaveY >= 6) {
            for (int z = nz0 + 4; z < nz1 - 1; z += 4) {
                v.setIfEmpty(x0 + 1, eaveY - 2, z, B.wallTorch(Dir.EAST));
                v.setIfEmpty(x1 - 1, eaveY - 2, z, B.wallTorch(Dir.WEST));
            }
        }
        if (chancel) {
            for (int z = nz1 + 2; z < cz1 - 1; z += 3) {
                v.setIfEmpty(chx0 + 1, chEave - 1, z, B.wallTorch(Dir.EAST));
                v.setIfEmpty(chx1 - 1, chEave - 1, z, B.wallTorch(Dir.WEST));
            }
        }
    }

    private void chandelier(int x, int z) {
        if (!v.isSolid(x, ridgeY, z)) return;
        int low = Math.min(Math.max(eaveY, 4), ridgeY - 1);
        for (int y = ridgeY - 1; y > low; y--) v.set(x, y, z, CHAIN);
        v.set(x, low, z, B.lantern(p.light(), true));
    }

    // ------------------------------------------------------------------ Kirchhof

    private void exterior() {
        int dz = tower ? tz0 : nz0;
        for (int z = 0; z < dz; z++)
            for (int x = cx - 1; x <= cx + 1; x++) v.set(x, 0, z, B.id(p.path()));
        if (dz >= 3) s.lanternPosts(cx - 1, 1, 3);

        boolean churchyard = style != Palette.Style.MODERN && style != Palette.Style.JAPANESE;
        if (churchyard && s.w >= 13 && s.d >= 16) {
            for (int x = 0; x < s.w; x++) {
                if (Math.abs(x - cx) > 1) v.setIfEmpty(x, 1, 0, B.id(graveWall));
                v.setIfEmpty(x, 1, s.d - 1, B.id(graveWall));
            }
            for (int z = 1; z < s.d - 1; z++) {
                v.setIfEmpty(0, 1, z, B.id(graveWall));
                v.setIfEmpty(s.w - 1, 1, z, B.id(graveWall));
            }
        }
        if (churchyard && style != Palette.Style.CLASSICAL) {
            graves(2, x0 - 4);
            graves(x1 + 4, s.w - 3);
        }
        int back = chancel ? cz1 : nz1;
        for (int[] t : new int[][] {{2, s.d - 3}, {s.w - 3, s.d - 3}, {2, dz + 2}, {s.w - 3, dz + 2}}) {
            boolean clear = t[0] <= x0 - 4 || t[0] >= x1 + 4 || t[1] >= back + 4;
            if (clear && rng.chance(0.8)) s.tree(t[0], t[1], 3 + rng.nextInt(2));
        }
        if (spec.has(BuildingSpec.Feature.GARDEN)) s.garden(0.5);
        s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.05, 0.015);
    }

    private void graves(int xa, int xb) {
        if (xb - xa < 0) return;
        String mossy = switch (graveWall) {
            case "stone_brick_wall" -> "mossy_stone_brick_wall";
            case "cobblestone_wall" -> "mossy_cobblestone_wall";
            default -> graveWall;
        };
        String[] stones = {graveWall, mossy, "andesite_wall"};
        int start = (tower ? tz0 : nz0) + 2;
        for (int z = start; z < s.d - 2; z += 3)
            for (int x = xa; x <= xb; x += 2) {
                if (!rng.chance(0.7) || !v.isEmpty(x, 1, z)) continue;
                v.set(x, 1, z, B.id(stones[rng.nextInt(stones.length)]));
                if (rng.chance(0.4)) v.setIfEmpty(x, 1, z - 1, B.id(rng.pick(List.of("poppy", "oxeye_daisy", "cornflower"))));
            }
    }

    /** Bildstock für winzige Bauplätze: Säule mit Laterne unter einem kleinen Dach. */
    private void shrine() {
        int x = s.w / 2, z = s.d / 2;
        v.set(x, 0, z, B.id(p.foundation()));
        v.set(x, 1, z, wall());
        if (s.h < 4) return;
        v.set(x, 2, z, B.lantern(p.light(), false));
        if (s.h >= 5) {
            v.set(x, 3, z, B.id(roof.block()));
            v.set(x - 1, 3, z, B.stairs(roof, Dir.EAST, false));
            v.set(x + 1, 3, z, B.stairs(roof, Dir.WEST, false));
            v.set(x, 3, z - 1, B.stairs(roof, Dir.SOUTH, false));
            v.set(x, 3, z + 1, B.stairs(roof, Dir.NORTH, false));
        }
        if (s.h >= 6) v.set(x, 4, z, B.with("end_rod", "facing", "up"));
        s.flowersAndBushes(x - 2, z - 2, x + 2, z + 2, 0.5, 0.0);
    }
}
