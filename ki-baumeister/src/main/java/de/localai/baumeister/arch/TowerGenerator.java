package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Turm- und Leuchtturm-Generator.
 *
 * <p>Turm: runder (bei schmalen Bauplätzen quadratischer) Wehr- bzw. Zauberturm mit dicken, verwitterten Mauern,
 * Lisenen bzw. Eckvorlagen, geböschtem Sockel, Gesimsbändern, Schießscharten mit Nischen und Rundbogenfenstern,
 * Wendeltreppe um eine Spindel (oder Leitern), Geschossen alle 5–6 Blöcke und je nach Stil/Dachwunsch:
 * Zinnenkranz auf zweistufigem Kragsteinfries mit Erkertürmchen und Treppenhäuschen, Kegeldach mit Traufkonsolen,
 * auskragendes Fachwerk-Obergeschoss mit Kegeldach oder Wehrgang mit zurückgesetztem Tambour und Kegeldach.
 * Dazu Eingangsportal bzw. Vordach, angebautes Wohnhaus, auf großen Bauplätzen Ringmauer mit Ecktürmchen, Tor,
 * Brunnen und Hof, Einrichtung je Geschoss und Beleuchtung. Winzige Bauplätze bekommen einen kompakten Turm.
 *
 * <p>Leuchtturm: sich verjüngender, rot-weiß gestreifter Rundturm auf einem Felsensockel, Galerie mit Geländer auf
 * Kragsteinen, verglaste Laternenkammer mit Leuchtfeuer, kleine Haube, Wendeltreppe und Wärterhaus am Fuß.
 */
final class TowerGenerator {
    private enum Top { BATTLEMENT, CONE, JETTY, DRUM }

    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final Rng rng;
    private final Palette.Style style;
    private final boolean lighthouse;

    // Grundgeometrie (Mittelpunkt, Durchmesser, Radien)
    private double cx, cz;
    private int n;
    private double R;
    private double rTop;
    private int t = 1;
    private boolean round = true;
    private int fh = 5;
    private int floors = 1;
    private int yDeck;
    private int yJ = -1;
    private Top top = Top.BATTLEMENT;
    private boolean mini;
    private boolean timberTop;   // oberstes Geschoss als Fachwerk (bündig)
    private boolean porch;
    private int plinthH = 1;
    private int stripeH = 3;

    // Anbau (Wohnhaus am Turm / Wärterhaus)
    private int annexDir;
    private int annexW, annexD, annexFloors;

    // Materialien
    private String lowerShaft;
    private String shaft, base, floorBlock, groundFloor, window, frame, lantern, white, red;
    private Materials.Family trimF, baseF, roofF, woodF, frameF, darkF;

    // Treppe
    private int ringSide;
    private final List<int[]> ring = new ArrayList<>();
    private int spiralTopY = -1;

    // Tür (1 breit bei ungeradem, 2 breit bei geradem Durchmesser)
    private int doorW = 1;

    TowerGenerator(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.spec = site.spec;
        this.rng = site.rng;
        this.style = site.spec.style();
        this.lighthouse = site.spec.type() == BuildingSpec.Type.LIGHTHOUSE;
    }

    Voxels build() {
        s.clearSite(style.sandy() ? "sand" : "grass_block");
        materials();
        if (lighthouse) planLighthouse(); else planTower();
        if (mini) {
            miniTower();
            return v;
        }
        if (lighthouse) buildLighthouse(); else buildTower();
        return v;
    }

    // =================================================================== Materialien

    private static boolean woody(String block) {
        String n = B.name(block);
        return n.endsWith("_planks") || n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem")
            || n.endsWith("_hyphae") || n.startsWith("bamboo");
    }

    private void materials() {
        String gw = B.name(p.groundWall()), wl = B.name(p.wall());
        String user = spec.wall() == null ? null : B.name(spec.wall().trim().toLowerCase(Locale.ROOT));
        if (user != null && user.equals(wl) && !woody(wl)) shaft = wl;
        else if (!woody(gw) && !gw.equals("white_terracotta")) shaft = gw;
        else shaft = B.name(p.foundation());
        base = B.name(p.foundation());
        if (base.equals(shaft)) {
            base = switch (shaft) {
                case "stone_bricks" -> "cobblestone";
                case "smooth_sandstone" -> "sandstone";
                case "smooth_quartz" -> "quartz_block";
                default -> shaft;
            };
        }
        trimF = Materials.family(shaft, null);
        if (trimF == null) {
            trimF = style == Palette.Style.FANTASY ? Materials.family("polished_andesite")
                : woody(p.trim().block()) ? Materials.family("stone_brick") : p.trim();
        }
        baseF = Materials.family(base, trimF);
        if (shaft.equals("calcite") || shaft.equals("white_concrete") || shaft.equals("smooth_quartz")
            || shaft.equals("quartz_block") || shaft.endsWith("terracotta")) {
            lowerShaft = style == Palette.Style.FANTASY ? "stone_bricks" : null;
        }
        roofF = p.roof();
        woodF = p.floor();
        frame = B.name(p.frame());
        String fk = frame.replace("stripped_", "").replace("_log", "").replace("_wood", "");
        frameF = Materials.family(fk, null);
        if (frameF == null) frameF = woody(frame) ? woodF : trimF;
        floorBlock = B.name(woodF.block());
        groundFloor = B.name(trimF.block());
        window = B.name(p.window());
        String l = B.name(p.light());
        lantern = l.endsWith("lantern") && !l.equals("sea_lantern") ? l : "lantern";
        darkF = Materials.family("polished_deepslate");
        // Leuchtturm-Streifen
        boolean clean = switch (style) {
            case MODERN, CLASSICAL, FANTASY -> true;
            default -> false;
        };
        white = clean ? "white_concrete" : "calcite";
        red = clean ? "red_concrete" : "red_terracotta";
        if (user != null && user.equals(wl) && !woody(wl) && lighthouse) white = wl;
    }

    /** Mauerstein mit Verwitterung (Sockelnähe moosiger). */
    private String stone(int y) {
        double r = rng.nextDouble();
        if (lowerShaft != null && y <= fh && !lighthouse) return s.vary(lowerShaft);
        if (y <= 2 && r < 0.22) return s.vary(base);
        if (y <= 7 && r < 0.10 && shaft.equals("stone_bricks")) return B.id("mossy_stone_bricks");
        return s.vary(shaft);
    }

    // =================================================================== Geometrie-Helfer

    private boolean in(int x, int z, double r) {
        double dx = x - cx, dz = z - cz;
        if (round) return dx * dx + dz * dz <= r * r + 1e-6;
        return Math.max(Math.abs(dx), Math.abs(dz)) <= r - 0.5 + 1e-6;
    }

    /** Randzelle der Scheibe r (ein seitlicher Nachbar liegt außerhalb). */
    private boolean edge(int x, int z, double r) {
        if (!in(x, z, r)) return false;
        for (Dir d : Dir.values()) if (!in(x + d.dx, z + d.dz, r)) return true;
        return false;
    }

    private boolean touches(int x, int z, double r) {
        for (Dir d : Dir.values()) if (in(x + d.dx, z + d.dz, r)) return true;
        return false;
    }

    /** Ringzelle (r, r+1], die seitlich an die Scheibe r anschließt. */
    private boolean shell(int x, int z, double r) {
        return in(x, z, r + 1) && !in(x, z, r) && touches(x, z, r);
    }

    private int lo(double c, double r) {
        return (int) Math.floor(c - r) - 1;
    }

    private int hi(double c, double r) {
        return (int) Math.ceil(c + r) + 1;
    }

    /** Richtung zur Turmachse (für Dach-/Kragstein-Treppen). */
    private Dir inward(int x, int z) {
        double dx = x - cx, dz = z - cz;
        if (Math.abs(dx) > Math.abs(dz) + 1e-9) return dx > 0 ? Dir.WEST : Dir.EAST;
        return dz > 0 ? Dir.NORTH : Dir.SOUTH;
    }

    private char tangentAxis(int x, int z) {
        return Math.abs(x - cx) > Math.abs(z - cz) + 1e-9 ? 'z' : 'x';
    }

    private double angle(int x, int z) {
        return Math.toDegrees(Math.atan2(z - cz, x - cx));
    }

    private static double angDiff(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180 ? 360 - d : d;
    }

    private int footprint(double e) {
        return n % 2 == 1 ? 2 * (int) Math.floor(e + 1e-9) + 1 : 2 * (int) Math.floor(e + 0.5 + 1e-9);
    }

    private double wallR(int y) {
        if (lighthouse) return lhR(y);
        if (top == Top.JETTY && y > yJ) return R + 1;
        if (top == Top.DRUM && y > yDeck) return R - 1.5;
        return R;
    }

    private double inR(int y) {
        if (lighthouse) return lhR(y) - 1;
        if (top == Top.JETTY && y > yJ) return R;
        if (top == Top.DRUM && y > yDeck) return R - 2.5;
        return R - t;
    }

    private boolean inSpiral(int x, int z) {
        return ringSide > 0 && Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= (ringSide - 1) / 2.0 + 1e-6;
    }

    private boolean ground(int x, int z) {
        String g = v.get(x, 0, z);
        if (g == null) return false;
        String nm = B.name(g);
        return nm.equals("grass_block") || nm.equals("sand") || nm.equals("coarse_dirt") || nm.equals("podzol")
            || nm.equals("moss_block") || nm.equals("dirt");
    }

    private void fillDisk(int y, double r, String block) {
        for (int x = lo(cx, r); x <= hi(cx, r); x++)
            for (int z = lo(cz, r); z <= hi(cz, r); z++)
                if (in(x, z, r)) v.set(x, y, z, block);
    }

    // =================================================================== Planung Turm

    private int topHeight() {
        return switch (top) {
            case BATTLEMENT -> 3;
            case CONE -> coneRadii(R + 1, steep()).length + 1;
            case JETTY -> coneRadii(R + 2, steep()).length + 2;
            case DRUM -> coneRadii(R, steep()).length + 7;
        };
    }

    private double steep() {
        if (!round) return 1.8;
        if (style == Palette.Style.FANTASY) return 2.4;
        if (top == Top.DRUM) return 1.8;
        return 1.9;
    }

    private void planTower() {
        int W = s.w, H = s.h, D = s.d;
        Palette.Roof rr = spec.roof();
        boolean wantCone = rr == null
            ? switch (style) {
                case STONE, DESERT, CLASSICAL, MODERN -> false;
                default -> true;
            }
            : rr != Palette.Roof.FLAT;
        boolean timberTop = p.timber() && (!B.name(p.wall()).equals(shaft) || style == Palette.Style.FANTASY);
        this.timberTop = timberTop;
        boolean drumStyle = switch (style) {
            case STONE, VICTORIAN, CLASSICAL, DESERT -> true;
            default -> false;
        };
        int minWD = Math.min(W, D);
        boolean big = minWD >= 40;
        int cap = style == Palette.Style.FANTASY ? (big ? 13 : 11) : wantCone ? (big ? 15 : 13) : (big ? 17 : 15);
        int byH = wantCone ? (H - 4) * 10 / 31 : (H - 4) * 10 / 22;
        int squat = Math.min(11, H - 3);
        int nMax = Math.min(cap, Math.min(minWD - 2, Math.max(5, Math.max(byH, squat))));
        // 1. Durchgang: gewünschte Dachform (notfalls etwas schmaler), 2. Durchgang: Zinnenkranz
        for (int pass = wantCone ? 0 : 1; pass < 2; pass++) {
            for (n = nMax; n >= 5; n--) {
                round = n >= 7 && style != Palette.Style.MODERN && style != Palette.Style.JAPANESE;
                R = n / 2.0;
                t = round && n >= 11 ? 2 : 1;
                fh = n >= 13 ? 6 : n >= 7 ? 5 : 4;
                List<Top> options = new ArrayList<>();
                if (pass == 0) {
                    // Fachwerk-/Zauberturm: lieber etwas schlanker mit auskragendem Obergeschoss
                    if (timberTop && minWD - n < 4 && n - (4 - (minWD - n)) >= 9) continue;
                    if (timberTop && n >= 7 && minWD - n >= 4) options.add(Top.JETTY);
                    if (drumStyle && n >= 11) options.add(Top.DRUM);
                    options.add(Top.CONE);
                } else {
                    options.add(Top.BATTLEMENT);
                }
                for (Top o : options) {
                    top = o;
                    int need = top == Top.JETTY ? 2 : 1;
                    int maxFloors = (H - topHeight()) / fh;
                    if (maxFloors < need) continue;
                    // Kegeldächer brauchen einen Schaft, der mindestens so hoch wie breit ist
                    if (top != Top.BATTLEMENT && maxFloors * fh < n && n > 7) continue;
                    int prop = Math.max(2, (int) Math.round(2.0 * n / fh));
                    int fill = (int) Math.floor((H - topHeight()) * 0.8 / fh);
                    floors = Math.min(Math.min(Math.max(Math.max(spec.floors(), prop), fill), maxFloors), 9);
                    if (floors < need) continue;
                    finishTowerPlan();
                    return;
                }
            }
        }
        mini = true;
    }

    private void finishTowerPlan() {
        int W = s.w, D = s.d;
        final int H = s.h;
        yDeck = floors * fh;
        yJ = top == Top.JETTY ? (floors - 1) * fh : -1;
        plinthH = n >= 11 ? 2 : 1;
        int span = n - 2 * t;
        if (n % 2 == 1) ringSide = span >= 11 ? 5 : span >= 5 ? 3 : 0;
        else ringSide = span >= 6 ? 4 : 0;
        spiralTopY = switch (top) {
            case BATTLEMENT, DRUM -> yDeck;
            default -> floors >= 2 ? (floors - 1) * fh : 0;
        };

        int ext = top == Top.JETTY ? 2 : 1;
        int fw = footprint(R + ext);
        int off = (fw - n) / 2;
        // Großer Bauplatz: Platz für eine Ringmauer lassen
        int inset = W >= 25 && D >= 25 && H >= 10 && W - 8 >= fw + 2 && D - 8 >= fw + 5 ? 4 : 0;
        W -= 2 * inset;
        D -= 2 * inset;
        // Anbau links/rechts, wenn genug Breite übrig ist
        int maxAnnex = W - 1 - (off + n - 1);
        annexDir = 0;
        int x0;
        if (maxAnnex >= 8 && n >= 7 && floors >= 2 && H >= 12) {
            annexW = Math.min(11, maxAnnex);
            annexDir = rng.chance(0.5) ? 1 : -1;
            int slack = maxAnnex - annexW;
            x0 = annexDir > 0 ? off + slack / 2 : W - 1 - off - (n - 1) - slack / 2;
            annexD = n - 4;
            if (annexD < 5) annexD += 2;
            annexD = Math.min(annexD, n % 2 == 1 ? 9 : 8);
            // Der Anbau muss deutlich unter dem Turmkopf bleiben
            int ridgeH = (annexD + 1) / 2 + 2;
            int headroom = (top == Top.JETTY ? yJ : top == Top.CONE ? yDeck : yDeck - 2) - 2;
            annexFloors = 8 + ridgeH + 1 <= headroom ? 2 : 1;
            if (4 + ridgeH + 1 > headroom) {
                annexDir = 0;
                x0 = off + (W - fw) / 2;
            }
        } else {
            x0 = off + (W - fw) / 2;
        }
        int spareD = D - fw;
        porch = spareD >= 3;
        int fz0 = porch ? 3 + (spareD - 3) / 2 : spareD / 2;
        int z0 = fz0 + off;
        cx = x0 + inset + (n - 1) / 2.0;
        cz = z0 + inset + (n - 1) / 2.0;
        buildRing();
    }

    private void buildRing() {
        ring.clear();
        if (ringSide <= 0) return;
        double h = (ringSide - 1) / 2.0;
        int xa = (int) Math.round(cx - h), xb = (int) Math.round(cx + h);
        int za = (int) Math.round(cz - h), zb = (int) Math.round(cz + h);
        List<int[]> cells = new ArrayList<>();
        for (int x = xa; x <= xb; x++) cells.add(new int[] {x, za});
        for (int z = za + 1; z <= zb; z++) cells.add(new int[] {xb, z});
        for (int x = xb - 1; x >= xa; x--) cells.add(new int[] {x, zb});
        for (int z = zb - 1; z > za; z--) cells.add(new int[] {xa, z});
        for (int i = 0; i < cells.size(); i++) {
            int[] a = cells.get(i), b = cells.get((i + 1) % cells.size());
            Dir f = b[0] > a[0] ? Dir.EAST : b[0] < a[0] ? Dir.WEST : b[1] > a[1] ? Dir.SOUTH : Dir.NORTH;
            ring.add(new int[] {a[0], a[1], f.ordinal()});
        }
    }

    // =================================================================== Turm bauen

    private void buildTower() {
        groundTexture();
        shaftTower();
        topTower();
        spiral(spiralTopY);
        windowsTower();
        doorFront();
        if (annexDir != 0) annexTower();
        if (porch) porch();
        interiorTower();
        curtainWall();
        exterior();
    }

    /** Eckvorlagen (eckiger Turm) bzw. Lisenen (Rundturm), die die Fassaden einrahmen. */
    private boolean ribCand(int x, int z) {
        if (!in(x, z, R + 1) || in(x, z, R)) return false;
        double a = angle(x, z);
        for (double ra : new double[] {45, 135, -45, -135}) if (angDiff(a, ra) <= 14) return true;
        return false;
    }

    private boolean isRib(int x, int z) {
        if (round) return pilasters.contains(key(x, z));
        if (!ribCand(x, z)) return false;
        if (touches(x, z, R)) return true;
        for (Dir d : Dir.values()) {
            int nx = x + d.dx, nz = z + d.dz;
            if (ribCand(nx, nz) && touches(nx, nz, R)) return true;
        }
        return false;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private final java.util.Set<Long> pilasters = new java.util.HashSet<>();

    private void planPilasters() {
        pilasters.clear();
        if (!round || n < 9) return;
        List<int[]> cells = new ArrayList<>();
        for (int x = lo(cx, R + 1); x <= hi(cx, R + 1); x++)
            for (int z = lo(cz, R + 1); z <= hi(cz, R + 1); z++)
                if (shell(x, z, R)) cells.add(new int[] {x, z});
        for (int k = 0; k < 8; k++) {
            double target = 22.5 + 45 * k;
            int[] best = null;
            double bd = 999;
            for (int[] c : cells) {
                double d = angDiff(angle(c[0], c[1]), target);
                if (d < bd) { bd = d; best = c; }
            }
            if (best != null && bd < 12) pilasters.add(key(best[0], best[1]));
        }
    }

    private int ribTop() {
        return switch (top) {
            case BATTLEMENT, DRUM -> yDeck - 3;
            case CONE -> yDeck - 1;
            case JETTY -> yJ - 1;
        };
    }

    private boolean flushTimber() {
        return timberTop && top == Top.CONE && floors >= 2 && !lighthouse;
    }

    private boolean bandAt(int f) {
        if (f == 1) return true;
        return floors >= 4 && f == floors - 1 && top != Top.JETTY;
    }

    private void shaftTower() {
        planPilasters();
        int wallTop = switch (top) {
            case BATTLEMENT, DRUM -> yDeck - 1;
            case CONE -> yDeck;
            case JETTY -> yJ;
        };
        double r1 = R + 1;
        for (int x = lo(cx, r1); x <= hi(cx, r1); x++)
            for (int z = lo(cz, r1); z <= hi(cz, r1); z++) {
                if (in(x, z, R)) {
                    boolean wall = !in(x, z, R - t);
                    v.set(x, 0, z, wall ? B.id(base) : B.id(groundFloor));
                    if (wall) for (int y = 1; y <= wallTop; y++) v.set(x, y, z, stone(y));
                } else if (isRib(x, z)) {
                    v.set(x, 0, z, s.vary(base));
                    for (int y = 1; y <= ribTop(); y++) v.set(x, y, z, y <= plinthH ? s.vary(base) : stone(y));
                } else if (shell(x, z, R)) {
                    v.set(x, 0, z, s.vary(base));
                    for (int y = 1; y <= plinthH; y++) v.set(x, y, z, s.vary(base));
                    v.set(x, plinthH + 1, z, B.stairs(baseF, inward(x, z), false));
                    // Gesimsbänder je Geschoss
                    for (int f = 1; f < floors; f++) {
                        int y = f * fh;
                        if (y == yJ || y > wallTop - 2 || !bandAt(f)) continue;
                        v.set(x, y, z, style == Palette.Style.MODERN ? B.slab(trimF, true) : B.stairs(trimF, inward(x, z), true));
                    }
                }
            }
        // Geschossdecken
        for (int f = 1; f < floors; f++) fillDisk(f * fh, R - t, B.id(floorBlock));
        // bündiges Fachwerk-Obergeschoss
        if (flushTimber()) timberRing(R, R - 1, (floors - 1) * fh + 1, wallTop);
    }

    // ------------------------------------------------------------------- Dachabschlüsse

    private void topTower() {
        switch (top) {
            case BATTLEMENT -> {
                if (style == Palette.Style.MODERN) {
                    modernRoof();
                    break;
                }
                corbels(yDeck - 1, R);
                deck(yDeck, R + 1);
                parapet(yDeck + 1, R + 1);
                bartizans();
                if (!stairHut()) flag();
            }
            case DRUM -> {
                corbels(yDeck - 1, R);
                deck(yDeck, R + 1);
                parapet(yDeck + 1, R + 1);
                bartizans();
                drum();
                cone(yDeck + 6, R - 0.5, roofF);
            }
            case CONE -> {
                eaveBrackets(yDeck - 1);
                cone(yDeck, R + 1, roofF);
            }
            case JETTY -> {
                jetty();
                cone(yDeck + 1, R + 2, roofF);
            }
        }
    }

    /** Zweistufiger Kragsteinfries: einzelne Konsolen und darüber ein geschlossener Kranz. */
    private void corbels(int y, double r) {
        for (int x = lo(cx, r + 1); x <= hi(cx, r + 1); x++)
            for (int z = lo(cz, r + 1); z <= hi(cz, r + 1); z++) {
                if (!in(x, z, r + 1) || in(x, z, r)) continue;
                v.set(x, y, z, B.stairs(trimF, inward(x, z), true));
                if (shell(x, z, r) && (Math.floorMod(x + z, 2) == 0 || isRib(x, z))) {
                    v.set(x, y - 1, z, B.stairs(trimF, inward(x, z), true));
                }
            }
    }

    /** Erkertürmchen (Scharwachtürme) auf Kragsteinen an den Diagonalen der Plattform. */
    private void bartizans() {
        if (!round || n < 11 || yDeck + 9 >= s.h) return;
        double dist = R + 0.5;
        for (double a : new double[] {-45, -135, 45, 135}) {
            int tx = (int) Math.round(cx + dist * Math.cos(Math.toRadians(a)));
            int tz = (int) Math.round(cz + dist * Math.sin(Math.toRadians(a)));
            // Platz prüfen (Bauplatzgrenze, Anbau)
            boolean ok = true;
            for (int x = tx - 2; x <= tx + 2 && ok; x++)
                for (int z = tz - 2; z <= tz + 2 && ok; z++) {
                    if (!v.in(x, yDeck, z)) ok = false;
                    else if (!in(x, z, R + 1) && (!v.isEmpty(x, yDeck - 2, z) && !in(x, z, R + 1.01))) ok = false;
                }
            if (!ok) continue;
            for (int x = tx - 1; x <= tx + 1; x++)
                for (int z = tz - 1; z <= tz + 1; z++) {
                    boolean outside = !in(x, z, R + 1);
                    boolean ringCell = x != tx || z != tz;
                    v.set(x, yDeck, z, B.id(groundFloor));
                    if (outside) {
                        v.set(x, yDeck - 1, z, B.stairs(trimF, inward(x, z), true));
                        if (Math.abs(x - tx) + Math.abs(z - tz) <= 1) v.set(x, yDeck - 2, z, B.stairs(trimF, inward(x, z), true));
                    }
                    for (int y = yDeck + 1; y <= yDeck + 3; y++) {
                        if (ringCell && !in(x, z, R + 0.2)) v.set(x, y, z, y == yDeck + 2 && outside
                            && (x == tx || z == tz) ? B.id(window) : stone(y));
                        else v.set(x, y, z, B.AIR);
                    }
                }
            // Kegeldächlein
            for (int x = tx - 2; x <= tx + 2; x++)
                for (int z = tz - 2; z <= tz + 2; z++) {
                    int ax = Math.abs(x - tx), az = Math.abs(z - tz);
                    if (ax == 2 && az == 2) continue;
                    Dir in = towards(x, z, tx, tz);
                    if (Math.max(ax, az) == 2) v.set(x, yDeck + 4, z, B.stairs(roofF, in, false));
                    else v.set(x, yDeck + 4, z, B.id(roofF.block()));
                    if (Math.max(ax, az) == 1) v.set(x, yDeck + 5, z, B.stairs(roofF, in, false));
                }
            v.set(tx, yDeck + 5, tz, B.id(roofF.block()));
            v.set(tx, yDeck + 6, tz, B.id(roofF.block()));
            v.set(tx, yDeck + 7, tz, B.id(p.fence()));
            if (yDeck + 8 < s.h) v.set(tx, yDeck + 8, tz, B.id(p.fence()));
            if (v.isEmpty(tx, yDeck + 3, tz) && v.isSolid(tx, yDeck + 4, tz)) v.set(tx, yDeck + 3, tz, B.lantern(lantern, true));
        }
    }

    private static Dir towards(int x, int z, int tx, int tz) {
        int dx = tx - x, dz = tz - z;
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Dir.EAST : Dir.WEST;
        return dz > 0 ? Dir.SOUTH : Dir.NORTH;
    }

    /** Treppenhäuschen über der Wendeltreppe auf der Plattform (große Türme). */
    private boolean stairHut() {
        if (ringSide < 3 || n < 13) return false;
        double hh = (ringSide - 1) / 2.0 + 1;
        int roofLayers = (int) Math.ceil(hh + 1);
        if (yDeck + 4 + roofLayers + 1 >= s.h) return false;
        int L = ring.size();
        int[] arrive = ring.get((1 + yDeck - 1) % L);
        for (int x = lo(cx, hh + 1); x <= hi(cx, hh + 1); x++)
            for (int z = lo(cz, hh + 1); z <= hi(cz, hh + 1); z++) {
                double ch = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                if (Math.abs(ch - hh) < 0.01) {
                    boolean corner = Math.abs(Math.abs(x - cx) - hh) < 0.01 && Math.abs(Math.abs(z - cz) - hh) < 0.01;
                    for (int y = yDeck + 1; y <= yDeck + 3; y++) v.set(x, y, z, corner ? s.vary(base) : stone(y));
                }
                // Pyramidendach
                for (int k = 0; k <= roofLayers; k++) {
                    double rr = hh + 1 - k;
                    if (rr < 0) break;
                    if (Math.abs(ch - rr) < 0.01) {
                        Dir in = inward(x, z);
                        boolean apex = rr < 0.6;
                        v.set(x, yDeck + 4 + k, z, apex ? B.id(roofF.block()) : B.stairs(roofF, in, false));
                    } else if (ch < rr - 0.01 && k == 0) {
                        v.set(x, yDeck + 4, z, B.id(roofF.block()));
                    }
                }
            }
        // Tür neben der obersten Stufe
        for (Dir d : Dir.values()) {
            int x = arrive[0] + d.dx, z = arrive[1] + d.dz;
            if (inSpiral(x, z)) continue;
            v.set(x, yDeck + 1, z, B.door(p.door(), d.opposite(), false));
            v.set(x, yDeck + 2, z, B.AIR);
            break;
        }
        // kleine Fenster
        for (Dir o : Dir.values()) {
            int x = (int) Math.round(cx + o.dx * hh), z = (int) Math.round(cz + o.dz * hh);
            if (n % 2 == 0) { x = (int) Math.floor(cx + o.dx * hh + 0.5 * (o.dx == 0 ? 1 : 0)); z = (int) Math.floor(cz + o.dz * hh + 0.5 * (o.dz == 0 ? 1 : 0)); }
            String below = v.get(x, yDeck + 1, z);
            if (v.get(x, yDeck + 2, z) != null && !B.isAir(below) && !B.name(below).endsWith("_door")) {
                v.set(x, yDeck + 2, z, B.id(window));
            }
        }
        int fx = (int) Math.round(cx), fz = (int) Math.round(cz);
        int ay = yDeck + 4 + roofLayers;
        for (int y = ay; y < s.h; y++) {
            if (v.isEmpty(fx, y, fz)) {
                v.set(fx, y, fz, B.id(p.fence()));
                if (y + 1 < s.h) v.set(fx, y + 1, fz, B.with(p.bed() + "_banner", "rotation", "8"));
                break;
            }
        }
        // Laterne innen
        int lx = (int) Math.floor(cx), lz = (int) Math.floor(cz);
        if (v.isEmpty(lx, yDeck + 3, lz) && v.isSolid(lx, yDeck + 4, lz)) v.set(lx, yDeck + 3, lz, B.lantern(lantern, true));
        return true;
    }

    private void deck(int y, double r) {
        for (int x = lo(cx, r); x <= hi(cx, r); x++)
            for (int z = lo(cz, r); z <= hi(cz, r); z++) {
                if (!in(x, z, r)) continue;
                v.set(x, y, z, edge(x, z, r) ? stone(y) : B.id(groundFloor));
            }
    }

    private void parapet(int y, double r) {
        List<int[]> merlons = new ArrayList<>();
        for (int x = lo(cx, r); x <= hi(cx, r); x++)
            for (int z = lo(cz, r); z <= hi(cz, r); z++) {
                if (!edge(x, z, r)) continue;
                v.set(x, y, z, stone(y));
                if (Math.floorMod(x + z, 2) == 0) {
                    v.set(x, y + 1, z, stone(y + 1));
                    merlons.add(new int[] {x, z});
                }
            }
        // Laternen auf den Zinnen über den Strebepfeilern
        if (y + 2 >= s.h) return;
        for (double ra : new double[] {45, 135, -45, -135}) {
            int[] best = null;
            double bd = 999;
            for (int[] m : merlons) {
                double d = angDiff(angle(m[0], m[1]), ra);
                if (d < bd) { bd = d; best = m; }
            }
            if (best != null && bd < 20) v.set(best[0], y + 2, best[1], B.lantern(lantern, false));
        }
    }

    /** Flachdach mit dünner Kragplatte und Glasbrüstung (moderner Turm). */
    private void modernRoof() {
        for (int x = lo(cx, R + 1); x <= hi(cx, R + 1); x++)
            for (int z = lo(cz, R + 1); z <= hi(cz, R + 1); z++) {
                if (in(x, z, R)) {
                    v.set(x, yDeck, z, edge(x, z, R) ? B.id(shaft) : B.id(groundFloor));
                    if (edge(x, z, R)) v.set(x, yDeck + 1, z, B.id(window));
                } else if (in(x, z, R + 1)) {
                    v.set(x, yDeck, z, B.slab(trimF, true));
                }
            }
    }

    private void flag() {
        if (ringSide <= 0 || yDeck + 4 >= s.h) return;
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        for (int y = yDeck + 1; y <= yDeck + 3; y++) v.set(x, y, z, B.id(p.fence()));
        v.set(x, yDeck + 4, z, B.with(p.bed() + "_banner", "rotation", "8"));
    }

    private void drum() {
        double rd = R - 1.5;
        for (int y = yDeck + 1; y <= yDeck + 6; y++)
            for (int x = lo(cx, rd); x <= hi(cx, rd); x++)
                for (int z = lo(cz, rd); z <= hi(cz, rd); z++)
                    if (in(x, z, rd) && !in(x, z, rd - 1)) v.set(x, y, z, y == yDeck + 6 ? B.id(trimF.block()) : stone(y));
        // Tür zum Wehrgang (vorne) und Fenster ringsum
        int[] xs = tangent(Dir.NORTH, 1);
        int[] c = outerCell(Dir.NORTH, xs[0], yDeck + 1);
        if (c != null) {
            v.set(c[0], yDeck + 1, c[1], B.door(p.door(), Dir.SOUTH, false));
            v.set(c[0], yDeck + 2, c[1], B.AIR);
            s.reserve(c[0], yDeck + 1, c[1] + 1);
            s.reserve(c[0], yDeck + 2, c[1] + 1);
            int[] lc = outerCell(Dir.NORTH, xs[0] + 1, yDeck + 2);
            if (lc != null && v.isEmpty(lc[0], yDeck + 2, lc[1] - 1)) v.set(lc[0], yDeck + 2, lc[1] - 1, B.wallTorch(Dir.NORTH));
        }
        for (Dir o : new Dir[] {Dir.EAST, Dir.SOUTH, Dir.WEST}) {
            for (int u : tangent(o, n % 2 == 1 ? 1 : 2)) {
                for (int y = yDeck + 3; y <= yDeck + 4; y++) {
                    int[] w = outerCell(o, u, y);
                    if (w != null) v.set(w[0], y, w[1], B.id(window));
                }
            }
        }
    }

    private void eaveBrackets(int y) {
        for (int x = lo(cx, R + 1); x <= hi(cx, R + 1); x++)
            for (int z = lo(cz, R + 1); z <= hi(cz, R + 1); z++) {
                if (!shell(x, z, R) || isRib(x, z)) continue;
                if (Math.floorMod(x + z, 2) == 0) v.set(x, y, z, B.stairs(trimF, inward(x, z), true));
            }
    }

    private void jetty() {
        double rj = R + 1;
        // Kragsteine (Holz) tragen das auskragende Fachwerkgeschoss
        for (int x = lo(cx, rj); x <= hi(cx, rj); x++)
            for (int z = lo(cz, rj); z <= hi(cz, rj); z++)
                if (in(x, z, rj) && !in(x, z, R)) v.set(x, yJ, z, B.stairs(frameF, inward(x, z), true));
        timberRing(rj, R, yJ + 1, yDeck + 1);
    }

    /** Fachwerkring zwischen den Radien rIn und rOut: Ständer, Schwelle/Rähm und Gefache. */
    private void timberRing(double rOut, double rIn, int yFrom, int yTo) {
        List<int[]> cells = new ArrayList<>();
        for (int x = lo(cx, rOut); x <= hi(cx, rOut); x++)
            for (int z = lo(cz, rOut); z <= hi(cz, rOut); z++)
                if (in(x, z, rOut) && !in(x, z, rIn)) cells.add(new int[] {x, z});
        java.util.Set<Long> posts = new java.util.HashSet<>();
        if (round) {
            int count = n >= 11 ? 16 : 8;
            for (int k = 0; k < count; k++) {
                double target = 360.0 / count * (k + 0.5);
                int[] best = null;
                double bd = 999;
                for (int[] c : cells) {
                    double d = angDiff(angle(c[0], c[1]), target);
                    if (d < bd) { bd = d; best = c; }
                }
                if (best != null) posts.add(key(best[0], best[1]));
            }
        } else {
            for (int[] c : cells) {
                double ax = Math.abs(c[0] - cx), az = Math.abs(c[1] - cz);
                double m = Math.max(ax, az);
                double along = ax >= az ? az : ax;
                if (Math.abs(ax - az) < 0.01 || Math.floorMod((int) Math.round(m - along), 3) == 0) posts.add(key(c[0], c[1]));
            }
        }
        String infill = B.name(p.wall()).equals(shaft) && style != Palette.Style.FANTASY ? "white_terracotta" : B.name(p.wall());
        for (int[] c : cells) {
            boolean post = posts.contains(key(c[0], c[1]));
            for (int y = yFrom; y <= yTo; y++) {
                String b;
                if (post) b = B.axis(frame, 'y');
                else if (y == yFrom || y == yTo) b = B.axis(frame, tangentAxis(c[0], c[1]));
                else b = B.id(infill);
                v.set(c[0], y, c[1], b);
            }
        }
    }

    // ------------------------------------------------------------------- Kegeldach

    private double[] coneRadii(double re, double steep) {
        List<Double> rs = new ArrayList<>();
        rs.add(re);
        double min = round ? (n % 2 == 1 ? 0.0 : 0.708) : (n % 2 == 1 ? 0.5 : 1.0);
        double step = 1.0 / steep;
        double r = re - 1;
        // Spitze: die kleinste Scheibe (1 bzw. 2x2 Zellen) nur einmal, damit kein Klotz entsteht
        double apexMax = round ? (n % 2 == 1 ? 0.99 : 1.58) : (n % 2 == 1 ? 1.49 : 1.99);
        while (r >= min - 1e-9 && rs.size() < 70) {
            rs.add(r);
            if (r <= apexMax) break;
            r -= step;
        }
        double[] out = new double[rs.size()];
        for (int i = 0; i < out.length; i++) out[i] = rs.get(i);
        return out;
    }

    private boolean roofCell(int x, int z, double[] rs, int i) {
        if (i >= rs.length || !in(x, z, rs[i])) return false;
        if (i + 1 >= rs.length) return true;
        return !in(x, z, rs[i + 1]) || edge(x, z, rs[i]);
    }

    /** Kegel- bzw. Pyramidendach aus Treppenringen; liefert die Höhe der Spitze. */
    private int cone(int y0, double re, Materials.Family fam) {
        double[] rs = coneRadii(re, lighthouse ? 1.0 : steep());
        for (int i = 0; i < rs.length; i++) {
            int y = y0 + i;
            for (int x = lo(cx, rs[i]); x <= hi(cx, rs[i]); x++)
                for (int z = lo(cz, rs[i]); z <= hi(cz, rs[i]); z++) {
                    if (!roofCell(x, z, rs, i)) continue;
                    boolean above = roofCell(x, z, rs, i + 1);
                    boolean center = Math.abs(x - cx) < 0.6 && Math.abs(z - cz) < 0.6;
                    v.set(x, y, z, above || center ? B.id(fam.block()) : B.stairs(fam, inward(x, z), false));
                }
        }
        int apex = y0 + rs.length - 1;
        // Spitze
        if (n % 2 == 1) {
            int x = (int) Math.round(cx), z = (int) Math.round(cz);
            if (apex + 1 < s.h) v.set(x, apex + 1, z, B.id(lighthouse ? "iron_bars" : p.fence()));
            if (apex + 2 < s.h) {
                v.set(x, apex + 2, z, lighthouse ? B.id("iron_bars")
                    : B.with(p.bed() + "_banner", "rotation", "4"));
            }
        }
        return apex;
    }

    // ------------------------------------------------------------------- Wendeltreppe / Leiter

    private void spiral(int topY) {
        if (topY < 1) return;
        if (ringSide == 0) {
            ladder(topY);
            return;
        }
        int L = ring.size();
        double h = (ringSide - 1) / 2.0;
        // Spindel
        for (int x = lo(cx, h); x <= hi(cx, h); x++)
            for (int z = lo(cz, h); z <= hi(cz, h); z++)
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) < h - 0.01)
                    for (int y = 1; y <= topY; y++) v.set(x, y, z, y % 6 == 0 ? B.id(trimF.block()) : B.axis(newel(), 'y'));
        for (int j = 0; j < topY; j++) {
            int[] c = ring.get((1 + j) % L);
            int y = 1 + j;
            v.set(c[0], y, c[1], B.stairs(woodF, Dir.values()[c[2]], false));
            for (int k = 1; k <= 3; k++) v.set(c[0], y + k, c[1], B.AIR);
        }
        // Zugänge freihalten: Antritt unten und Austritt je Ebene
        List<Integer> levels = new ArrayList<>();
        levels.add(0);
        for (int y = fh; y <= topY; y += fh) levels.add(y);
        if (!levels.contains(topY)) levels.add(topY);
        for (int yf : levels) {
            int j = yf == 0 ? 0 : yf - 1;
            if (j >= topY) continue;
            int[] c = ring.get((1 + j) % L);
            for (Dir d : Dir.values()) {
                int nx = c[0] + d.dx, nz = c[1] + d.dz;
                if (inSpiral(nx, nz)) continue;
                s.reserve(nx, yf + 1, nz);
                s.reserve(nx, yf + 2, nz);
            }
        }
    }

    private String newel() {
        return woody(frame) ? frame : B.name(trimF.block());
    }

    private void ladder(int topY) {
        double ri = R - t;
        int x = (int) Math.round(cx + ri - 0.5);
        int z = (int) Math.round(cz + ri - 0.5);
        if (round) {
            // innerste Zelle an der Rückwand neben der Mitte
            x = (int) Math.floor(cx) + 1;
            z = (int) Math.floor(cz);
            while (in(x, z + 1, ri)) z++;
        }
        if (!v.isSolid(x, 1, z + 1)) return;
        for (int y = 1; y <= topY; y++) {
            v.set(x, y, z, B.ladder(Dir.NORTH));
            s.reserve(x, y, z);
            s.reserve(x, y, z - 1);
        }
        s.reserve(x, topY + 1, z);
        s.reserve(x, topY + 2, z);
    }

    // ------------------------------------------------------------------- Fenster

    /** Tangentiale Positionen (x bei Nord/Süd, z bei Ost/West) für eine Öffnung der Breite w. */
    private int[] tangent(Dir o, int w) {
        double c = o.dx == 0 ? cx : cz;
        int[] out = new int[w];
        double start = c - (w - 1) / 2.0;
        for (int i = 0; i < w; i++) out[i] = (int) Math.floor(start + i + 0.5);
        return out;
    }

    /** Äußerste Wandzelle in Richtung o an der Tangentialposition u auf Höhe y. */
    private int[] outerCell(Dir o, int u, int y) {
        double r = wallR(y);
        int x = o.dx == 0 ? u : (o.dx > 0 ? (int) Math.ceil(cx + r) + 1 : (int) Math.floor(cx - r) - 1);
        int z = o.dz == 0 ? u : (o.dz > 0 ? (int) Math.ceil(cz + r) + 1 : (int) Math.floor(cz - r) - 1);
        for (int i = 0; i < 2 * r + 6; i++) {
            if (in(x, z, r)) return new int[] {x, z};
            x -= o.dx;
            z -= o.dz;
        }
        return null;
    }

    private Dir tanLow(Dir o) {
        return o.dx == 0 ? Dir.WEST : Dir.NORTH;
    }

    private void windowsTower() {
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            boolean jettyFloor = (top == Top.JETTY || flushTimber()) && f == floors - 1;
            boolean topFloor = f == floors - 1;
            boolean defensive = top == Top.BATTLEMENT || top == Top.DRUM;
            for (Dir o : Dir.values()) {
                if (f == 0 && o == Dir.NORTH) continue;
                if (style == Palette.Style.MODERN && !round && f > 0) {
                    glassStrip(o, yb);
                    continue;
                }
                if (f == 0 && annexDir != 0 && o == (annexDir > 0 ? Dir.EAST : Dir.WEST)) continue;
                boolean ns = o == Dir.NORTH || o == Dir.SOUTH;
                if (!topFloor && f > 0 && ns != (f % 2 == 1)) continue;
                if (style == Palette.Style.MODERN && !round) {
                    if (f == 0 && o == Dir.NORTH) continue;
                    glassStrip(o, yb);
                } else if (jettyFloor) {
                    timberWindow(o, yb);
                } else if (f == 0 || (defensive && f < floors / 2 && !topFloor)) {
                    slit(o, yb);
                } else {
                    int w = n >= 11 && t >= 2 ? (n % 2 == 1 ? 3 : 2) : (n % 2 == 1 ? 1 : 2);
                    if (!topFloor && w == 3) w = 1;
                    archWindow(o, yb, w, yb + 2, yb + (fh >= 6 || topFloor ? Math.min(4, fh - 2) : 3));
                }
            }
        }
    }

    /** Raumhohes Fensterband (moderner Turm). */
    private void glassStrip(Dir o, int yb) {
        int w = Math.max(1, n - 4);
        for (int u : tangent(o, w)) {
            for (int y = yb + 1; y <= yb + fh - 1; y++) {
                int[] c = outerCell(o, u, y);
                if (c != null) v.set(c[0], y, c[1], B.id(window));
            }
        }
    }

    private void slit(Dir o, int yb) {
        int y0 = yb + 2, y1 = yb + (fh >= 6 ? 4 : 3);
        for (int u : tangent(o, n % 2 == 1 ? 1 : 2)) {
            for (int y = y0; y <= y1; y++) {
                int[] c = outerCell(o, u, y);
                if (c == null) continue;
                v.set(c[0], y, c[1], B.id(window));
                if (t >= 2) {
                    // Nische mit Schräge nach innen
                    for (int off = -1; off <= 1; off++) {
                        int[] c2 = outerCell(o, u + off, y);
                        if (c2 == null) continue;
                        int ix = c2[0] - o.dx, iz = c2[1] - o.dz;
                        if (!in(ix, iz, R - t)) v.set(ix, y, iz, B.AIR);
                    }
                }
            }
            int[] c = outerCell(o, u, y1 + 1);
            if (c != null && t == 1) setOut(c, o, y1 + 1, B.stairs(trimF, o.opposite(), false));
        }
    }

    /** Setzt einen Block vor die Wand (außen), nur wenn dort Luft ist. */
    private void setOut(int[] c, Dir o, int y, String state) {
        int x = c[0] + o.dx, z = c[1] + o.dz;
        if (v.isEmpty(x, y, z)) v.set(x, y, z, state);
    }

    private void archWindow(Dir o, int yb, int width, int y0, int y1) {
        int[] us = tangent(o, width);
        Dir tl = tanLow(o);
        boolean arch = t >= 2 && width >= 2;
        int archTop = y1;
        for (int i = 0; i < us.length; i++) {
            int u = us[i];
            boolean side = width >= 3 && (i == 0 || i == us.length - 1);
            int yTop = side ? y1 - 1 : y1;
            for (int y = y0; y <= yTop; y++) {
                int[] c = outerCell(o, u, y);
                if (c == null) continue;
                if (t >= 2) {
                    v.set(c[0], y, c[1], B.AIR);
                    v.set(c[0] - o.dx, y, c[1] - o.dz, B.id(window));
                } else {
                    v.set(c[0], y, c[1], B.id(window));
                }
            }
            int[] c = outerCell(o, u, y1);
            if (c == null) continue;
            if (arch && width >= 3 && side) {
                v.set(c[0], y1, c[1], B.stairs(trimF, i == 0 ? tl : tl.opposite(), true));
            } else if (arch && width == 2 && y1 + 1 < yb + fh) {
                v.set(c[0], y1 + 1, c[1], B.stairs(trimF, i == 0 ? tl : tl.opposite(), true));
                archTop = y1 + 1;
            }
            // Sims unter dem Fenster
            if (t == 1) setOut(c, o, y0 - 1, B.slab(trimF, true));
        }
        // Verdachung
        int hy = archTop + 1;
        if (hy < yb + fh && t == 1) {
            for (int u : us) {
                int[] c = outerCell(o, u, hy);
                if (c != null) setOut(c, o, hy, B.stairs(trimF, o.opposite(), false));
            }
        }
        if (spec.has(BuildingSpec.Feature.SHUTTERS) && t == 1) shutters(o, us, y0, y1);
    }

    private void shutters(Dir o, int[] us, int y0, int y1) {
        for (int u : new int[] {us[0] - 1, us[us.length - 1] + 1}) {
            for (int y = y0; y <= y1; y++) {
                int[] c = outerCell(o, u, y);
                if (c == null || !v.isSolid(c[0], y, c[1])) continue;
                setOut(c, o, y, B.trapdoor(p.trapdoor(), o, y == y1, true));
            }
        }
    }

    private void timberWindow(Dir o, int yb) {
        int[] us = tangent(o, n % 2 == 1 ? (n >= 11 ? 3 : 1) : 2);
        int y0 = yb + 2, y1 = yb + 3;
        for (int u : us) {
            for (int y = y0; y <= y1; y++) {
                int[] c = outerCell(o, u, y);
                if (c != null) v.set(c[0], y, c[1], B.id(window));
            }
            int[] c = outerCell(o, u, y0 - 1);
            if (c == null) continue;
            setOut(c, o, y0 - 1, B.trapdoor(p.trapdoor(), o, true, false));
            if (spec.has(BuildingSpec.Feature.FLOWER_BOXES)) {
                int[] c2 = outerCell(o, u, y0);
                if (c2 != null) setOut(c2, o, y0, B.id(rng.pick(List.of("potted_red_tulip", "potted_poppy",
                    "potted_dandelion", "potted_blue_orchid", "potted_azure_bluet"))));
            }
        }
        shutters(o, us, y0, y1);
    }

    // ------------------------------------------------------------------- Eingang

    private void doorFront() {
        doorW = n % 2 == 0 ? 2 : 1;
        int[] xs = tangent(Dir.NORTH, doorW);
        boolean portal = t >= 2;
        if (portal) {
            int[] ps = tangent(Dir.NORTH, doorW + 2);
            for (int i = 0; i < ps.length; i++) {
                int[] c = outerCell(Dir.NORTH, ps[i], 1);
                if (c == null) continue;
                v.set(c[0], 1, c[1], B.AIR);
                v.set(c[0], 2, c[1], B.AIR);
                if (i == 0) v.set(c[0], 3, c[1], B.stairs(trimF, Dir.WEST, true));
                else if (i == ps.length - 1) v.set(c[0], 3, c[1], B.stairs(trimF, Dir.EAST, true));
                else v.set(c[0], 3, c[1], B.AIR);
                for (int y = 1; y <= plinthH + 1; y++) v.set(c[0], y, c[1] - 1, B.AIR);
            }
        }
        for (int i = 0; i < doorW; i++) {
            int x = xs[i];
            int[] c = outerCell(Dir.NORTH, x, 1);
            if (c == null) continue;
            int tt = lighthouse ? 1 : t;
            for (int k = 0; k < tt; k++) {
                int z = c[1] + k;
                if (k == tt - 1) {
                    v.set(x, 1, z, B.door(p.door(), Dir.SOUTH, i == 1));
                } else {
                    v.set(x, 1, z, B.AIR);
                }
                v.set(x, 2, z, B.AIR);
            }
            int zi = c[1] + tt;
            for (int y = 1; y <= 2; y++) {
                s.reserve(x, y, zi);
                s.reserve(x, y, zi + 1);
                s.reserve(x, y, c[1] - 1);
                s.reserve(x, y, c[1] - 2);
            }
            for (int y = 1; y <= plinthH + 1; y++) v.set(x, y, c[1] - 1, B.AIR);
            v.set(x, 0, c[1] - 1, B.id(p.path()));
        }
        // Wandfackeln neben dem Eingang (ohne Vordach)
        if (!porch) {
            int ty = plinthH + 2;
            int extra = portal ? 1 : 0;
            for (int x : new int[] {xs[0] - 1 - extra, xs[doorW - 1] + 1 + extra}) {
                int[] c = outerCell(Dir.NORTH, x, ty);
                if (c == null || !v.isSolid(c[0], ty, c[1])) continue;
                if (v.isEmpty(c[0], ty, c[1] - 1)) v.set(c[0], ty, c[1] - 1, B.wallTorch(Dir.NORTH));
            }
            if (!portal) {
                // kleines Vordach über der Tür
                for (int x = xs[0] - 1; x <= xs[doorW - 1] + 1; x++) {
                    int[] c = outerCell(Dir.NORTH, x, 3);
                    if (c != null && v.isSolid(c[0], 3, c[1])) setOut(c, Dir.NORTH, 3, B.stairs(trimF, Dir.SOUTH, false));
                }
            }
        }
    }

    private void porch() {
        int[] xs = tangent(Dir.NORTH, doorW);
        int[] c = outerCell(Dir.NORTH, xs[0], 1);
        if (c == null) return;
        int zw = c[1];
        int px0 = xs[0] - 2, px1 = xs[doorW - 1] + 2;
        int pz0 = zw - 3;
        if (pz0 < 1) return;
        int pw = px1 - px0 + 1;
        // Boden und Stützen
        for (int x = px0; x <= px1; x++)
            for (int z = pz0; z < zw; z++) {
                if (in(x, z, R)) continue;
                v.set(x, 0, z, x == px0 || x == px1 ? s.vary(base) : B.id(p.path()));
                for (int y = 1; y <= 2; y++) {
                    String st = v.get(x, y, z);
                    if (st != null && B.name(st).endsWith("_stairs") && x > px0 && x < px1) v.set(x, y, z, B.AIR);
                }
                s.reserve(x, 1, z);
                s.reserve(x, 2, z);
            }
        for (int x : new int[] {px0, px1}) {
            for (int y = 1; y <= 3; y++) v.set(x, y, pz0, B.axis(frame, 'y'));
            v.set(x, 0, pz0, s.vary(base));
            for (int z = pz0 + 1; z < zw; z++) if (!in(x, z, R) && v.isEmpty(x, 3, z)) v.set(x, 3, z, B.axis(frame, 'z'));
        }
        // Satteldach (First in z-Richtung)
        int half = (pw - 1) / 2;
        for (int z = pz0 - 1; z <= zw + 2; z++) {
            for (int i = 0; i < pw; i++) {
                int x = px0 + i;
                if (in(x, z, R)) continue;
                int k = Math.min(i, pw - 1 - i);
                if (pw % 2 == 1 && i == half) {
                    setIfFree(x, 3 + half, z, B.id(roofF.block()));
                    setIfFree(x, 4 + half, z, B.slab(roofF, false));
                } else {
                    setIfFree(x, 4 + k, z, B.stairs(roofF, i < half + (pw % 2) ? Dir.EAST : Dir.WEST, false));
                }
            }
        }
        // Giebelfeld vorne
        for (int i = 1; i < pw - 1; i++) {
            int x = px0 + i;
            int k = Math.min(i, pw - 1 - i);
            int roofY = pw % 2 == 1 && i == half ? 3 + half : 4 + k;
            for (int y = 4; y < roofY; y++) v.set(x, y, pz0, y == 4 ? B.axis(frame, 'x') : B.id(woodF.block()));
        }
        // Laterne unter dem First
        int lx = px0 + half;
        int ly = pw % 2 == 1 ? 3 + half - 1 : 3 + half;
        if (v.isEmpty(lx, ly, pz0 + 1) && v.isSolid(lx, ly + 1, pz0 + 1)) v.set(lx, ly, pz0 + 1, B.lantern(lantern, true));
    }

    private void setIfFree(int x, int y, int z, String state) {
        if (v.isEmpty(x, y, z)) v.set(x, y, z, state);
    }

    // ------------------------------------------------------------------- Anbau

    private void annexTower() {
        int zr = (int) Math.floor(cz);
        int za = (int) Math.round(cz - (annexD - 1) / 2.0), zb = za + annexD - 1;
        int xNear;
        int[] wc = outerCell(annexDir > 0 ? Dir.EAST : Dir.WEST, zr, 1);
        if (wc == null) return;
        xNear = wc[0];
        int xFar = xNear + annexDir * (annexW - 1);
        List<String> types = List.of(annexFloors == 2 ? "kitchen" : "dining", "bedroom");
        annex(xNear, xFar, za, zb, annexFloors, types);
        // Durchgang in den Turm
        for (int k = 0; k < t; k++) {
            int x = xNear - annexDir * k;
            v.set(x, 1, zr, B.AIR);
            v.set(x, 2, zr, B.AIR);
            s.reserve(x - annexDir, 1, zr);
            s.reserve(x - annexDir, 2, zr);
            s.reserve(x + annexDir, 1, zr);
            s.reserve(x + annexDir, 2, zr);
        }
    }

    /** Angebautes Satteldachhaus; xNear liegt an der Turmwand, xFar ist die Giebelseite. */
    private void annex(int xNear, int xFar, int za, int zb, int floorsA, List<String> types) {
        int sg = xFar > xNear ? 1 : -1;
        int fhA = 4;
        int topA = floorsA * fhA;
        int ridgeH = (zb - za + 2) / 2 + 2;
        int xMin = Math.min(xNear, xFar), xMax = Math.max(xNear, xFar);
        boolean timber = p.timber() && !B.name(p.wall()).equals(shaft);
        // Bauraum freiräumen (Sockel, Bänder usw. des Turms an dieser Stelle)
        int clearTop = Math.min(s.h - 1, topA + ridgeH + 1);
        if (!lighthouse) clearTop = Math.min(clearTop, (top == Top.JETTY ? yJ : yDeck) - 3);
        for (int y = 1; y <= clearTop; y++)
            for (int x = xMin - 1; x <= xMax + 1; x++)
                for (int z = za - 1; z <= zb + 1; z++)
                    if (!in(x, z, wallR(y)) && !(x == xNear - sg)) v.set(x, y, z, B.AIR);
        for (int x = xMin; x <= xMax; x++)
            for (int z = za; z <= zb; z++) {
                if (in(x, z, wallR(0))) continue;
                boolean ringCell = x == xMin || x == xMax || z == za || z == zb;
                v.set(x, 0, z, ringCell ? s.vary(base) : B.id(floorBlock));
            }
        // Wände
        for (int y = 1; y <= topA; y++)
            for (int x = xMin; x <= xMax; x++)
                for (int z = za; z <= zb; z++) {
                    boolean ringCell = x == xMin || x == xMax || z == za || z == zb;
                    if (!ringCell || in(x, z, wallR(y))) continue;
                    boolean corner = (x == xMin || x == xMax) && (z == za || z == zb);
                    boolean alongX = z == za || z == zb;
                    String b;
                    boolean upper = timber && (floorsA == 1 ? y >= 2 : y > fhA);
                    if (y == 1 && timber) b = s.vary(base);
                    else if (corner) b = timber ? B.axis(frame, 'y') : s.vary(base);
                    else if (y % fhA == 0) b = timber ? B.axis(frame, alongX ? 'x' : 'z') : B.id(trimF.block());
                    else if (upper) b = (alongX ? Math.floorMod(x - xNear, 3) == 0 : Math.floorMod(z - za, 3) == 0)
                        ? B.axis(frame, 'y') : B.id(p.wall());
                    else b = stone(y);
                    v.set(x, y, z, b);
                }
        for (int k = 1; k <= floorsA; k++)
            for (int x = xMin + 1; x <= xMax - 1; x++)
                for (int z = za + 1; z <= zb - 1; z++)
                    if (!in(x, z, wallR(k * fhA))) v.set(x, k * fhA, z, B.id(floorBlock));
        // Dach (First in x-Richtung, Giebel an der Außenseite)
        int gx0 = xNear, gx1 = xFar + sg;
        for (int i = 0; ; i++) {
            int ca = za - 1 + i, cb = zb + 1 - i;
            int y = topA + i;
            if (ca > cb || y >= s.h) break;
            for (int x = Math.min(gx0, gx1); x <= Math.max(gx0, gx1); x++) {
                for (int z : ca == cb ? new int[] {ca} : new int[] {ca, cb}) {
                    if (in(x, z, wallR(y))) continue;
                    if (ca == cb) {
                        v.set(x, y, z, B.id(roofF.block()));
                        if (y + 1 < s.h && !in(x, z, wallR(y + 1))) v.set(x, y + 1, z, B.slab(roofF, false));
                    } else {
                        v.set(x, y, z, B.stairs(roofF, z == ca ? Dir.SOUTH : Dir.NORTH, false));
                    }
                }
            }
            if (y > topA) {
                for (int gx : new int[] {xNear, xFar}) {
                    for (int z = ca + 1; z <= cb - 1; z++) {
                        if (in(gx, z, wallR(y))) continue;
                        v.set(gx, y, z, timber || woody(p.wall()) ? B.id(p.wall()) : stone(y));
                    }
                }
            }
        }
        int zm = (za + zb) / 2;
        if (topA + 2 < s.h && !B.isAir(v.get(xFar, topA + 2, zm))) v.set(xFar, topA + 2, zm, B.id(window));
        // Tür vorne
        int dx = (xNear + xFar) / 2 + sg;
        if (Math.abs(dx - xNear) < 2) dx = xNear + 2 * sg;
        v.set(dx, 1, za, B.door(p.door(), Dir.SOUTH, false));
        v.set(dx, 2, za, B.AIR);
        for (int y = 1; y <= 2; y++) {
            s.reserve(dx, y, za + 1);
            s.reserve(dx, y, za - 1);
        }
        for (int x = dx - 1; x <= dx + 1; x++) setIfFree(x, 3, za - 1, B.stairs(woodF, Dir.SOUTH, false));
        if (v.isEmpty(dx + sg, 2, za - 1)) v.set(dx + sg, 2, za - 1, B.lantern(lantern, true));
        for (int z = 0; z < za; z++) if (ground(dx, z) || z == za - 1) v.set(dx, 0, z, B.id(p.path()));
        // Fenster vorne/hinten und am Giebel
        for (int k = 0; k < floorsA; k++) {
            int yb = k * fhA;
            for (int x = xNear + 2 * sg; sg > 0 ? x < xFar : x > xFar; x += 2 * sg) {
                for (int z : new int[] {za, zb}) {
                    if (k == 0 && z == za && Math.abs(x - dx) <= 1) continue;
                    if (timber && (floorsA == 1 || k > 0) && Math.floorMod(x - xNear, 3) == 0) continue;
                    Dir o = z == za ? Dir.NORTH : Dir.SOUTH;
                    v.set(x, yb + 2, z, B.id(window));
                    v.set(x, yb + 3, z, B.id(window));
                    setIfFree(x, yb + 1, z + o.dz, B.trapdoor(p.trapdoor(), o, true, false));
                    if (p.shutters() || spec.has(BuildingSpec.Feature.SHUTTERS)) {
                        for (int sx : new int[] {x - 1, x + 1}) {
                            if (!v.isSolid(sx, yb + 2, z)) continue;
                            for (int y = yb + 2; y <= yb + 3; y++)
                                setIfFree(sx, y, z + o.dz, B.trapdoor(p.trapdoor(), o, y == yb + 3, true));
                        }
                    }
                }
            }
            Dir o = sg > 0 ? Dir.EAST : Dir.WEST;
            v.set(xFar, yb + 2, zm, B.id(window));
            v.set(xFar, yb + 3, zm, B.id(window));
            setIfFree(xFar + sg, yb + 1, zm, B.trapdoor(p.trapdoor(), o, true, false));
        }
        // Schornstein in der hinteren Ecke
        boolean chimney = spec.has(BuildingSpec.Feature.CHIMNEY) || switch (style) {
            case MEDIEVAL, RUSTIC, NORDIC, VICTORIAN -> true;
            default -> false;
        };
        int chx = xFar - sg, chz = zb - 1;
        if (chimney) {
            int roofAt = topA;
            for (int y = s.h - 1; y > topA; y--) if (!v.isEmpty(chx, y, chz)) { roofAt = y; break; }
            int topC = Math.min(roofAt + 2, s.h - 2);
            for (int y = 1; y <= topC; y++) {
                v.set(chx, y, chz, s.vary(p.chimney()));
                s.reserve(chx, y, chz);
            }
            if (topC + 1 < s.h) v.set(chx, topC + 1, chz, B.campfire());
            v.set(chx, 1, chz - 1, B.with("furnace", "facing", "north"));
        }
        // Leiter ins Obergeschoss
        if (floorsA >= 2) {
            int lx = xFar - sg, lz = za + 1;
            for (int y = 1; y <= fhA; y++) {
                v.set(lx, y, lz, B.ladder(sg > 0 ? Dir.WEST : Dir.EAST));
                s.reserve(lx, y, lz);
                s.reserve(lx - sg, y, lz);
            }
            s.reserve(lx, fhA + 1, lz);
            s.reserve(lx, fhA + 2, lz);
        }
        // Einrichtung
        for (int k = 0; k < floorsA; k++) {
            int yb = k * fhA;
            int ix0 = xMin + 1, ix1 = xMax - 1;
            String type = types.get(Math.min(k, types.size() - 1));
            if (floorsA == 1 && ix1 - ix0 >= 6) {
                int mid = (ix0 + ix1) / 2;
                s.furnish(new Site.Room(ix0, za + 1, mid, zb - 1, yb + 1, yb + fhA, sg > 0 ? "kitchen" : "bedroom"));
                s.furnish(new Site.Room(mid + 1, za + 1, ix1, zb - 1, yb + 1, yb + fhA, sg > 0 ? "bedroom" : "kitchen"));
            } else {
                s.furnish(new Site.Room(ix0, za + 1, ix1, zb - 1, yb + 1, yb + fhA, type));
            }
        }
    }

    // ------------------------------------------------------------------- Einrichtung

    private void interiorTower() {
        List<String> types = roomPlan();
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            boolean ceiling = f < floors - 1 || top == Top.BATTLEMENT || top == Top.DRUM;
            String type = types.get(Math.min(f, types.size() - 1));
            furnishRound(yb + 1, yb + fh, inR(yb + 1), type, ceiling);
        }
        if (top == Top.DRUM) furnishRound(yDeck + 1, yDeck + 6, R - 2.5, "office", false);
    }

    private List<String> roomPlan() {
        List<String> out = new ArrayList<>(spec.rooms());
        List<String> defaults;
        if (lighthouse) defaults = List.of("storage", annexDir == 0 ? "bedroom" : "storage", "workshop", "storage", "office");
        else if (style == Palette.Style.FANTASY) defaults = List.of("storage", "library", "workshop", "bedroom", "library", "library");
        else defaults = List.of("storage", "living", "library", "bedroom", "workshop", "library", "bedroom", "storage");
        int i = 0;
        while (out.size() < Math.max(floors, 8)) {
            out.add(defaults.get(Math.min(out.size(), defaults.size() - 1)));
            if (++i > 20) break;
        }
        if (lighthouse) out.set(Math.max(0, floors - 1), "office");
        return out;
    }

    private List<String> items(String type) {
        boolean magic = style == Palette.Style.FANTASY;
        return switch (type) {
            case "kitchen" -> List.of("furnace", "smoker", "crafting_table", "barrel", "#cauldron", "chest", "barrel", "#plant");
            case "bedroom" -> List.of("#bed", "chest", "#plant", "bookshelf", "barrel", "#chair", "#plant");
            case "library", "office" -> List.of("bookshelf", "bookshelf", "#lectern", "bookshelf", "bookshelf",
                magic ? "#enchant" : "cartography_table", "bookshelf", "bookshelf", "#chair", "bookshelf", "bookshelf", "bookshelf");
            case "storage" -> List.of("barrel", "chest", "barrel", "barrel", "chest", "#hay", "barrel", "chest", "barrel");
            case "workshop" -> magic
                ? List.of("#brew", "#cauldron", "bookshelf", "crafting_table", "#brew", "barrel", "bookshelf", "#plant")
                : List.of("crafting_table", "smithing_table", "#anvil", "barrel", "grindstone[face=floor,facing=north]",
                    "stonecutter[facing=north]", "chest", "barrel");
            case "dining", "bar" -> List.of("barrel", "#chair", "#plant", "chest", "#chair", "barrel");
            case "chapel" -> List.of("#plant", "bookshelf", "#lectern", "#plant", "bookshelf");
            default -> List.of("bookshelf", "#chair", "#plant", "chest", "#chair", "bookshelf", "barrel", "#plant");
        };
    }

    private boolean nearReserved(int x, int y, int z) {
        if (s.isReserved(x, y, z)) return true;
        for (Dir d : Dir.values()) if (s.isReserved(x + d.dx, y, z + d.dz)) return true;
        return false;
    }

    /** Rund- (oder Rechteck-)raum entlang der Wand einrichten und beleuchten. */
    private void furnishRound(int y, int ceil, double ri, String type, boolean ceiling) {
        if (ri < 1) return;
        List<int[]> spots = new ArrayList<>();
        for (int x = lo(cx, ri); x <= hi(cx, ri); x++)
            for (int z = lo(cz, ri); z <= hi(cz, ri); z++) {
                if (!in(x, z, ri) || inSpiral(x, z) || !s.free(x, y, z) || !v.isSolid(x, y - 1, z)) continue;
                Dir wall = null;
                double best = -1e9;
                for (Dir d : Dir.values()) {
                    int nx = x + d.dx, nz = z + d.dz;
                    if (in(nx, nz, ri) || !v.isSolid(nx, y, nz)) continue;
                    double dot = d.dx * (x - cx) + d.dz * (z - cz);
                    if (dot > best) { best = dot; wall = d; }
                }
                if (wall == null || nearReserved(x, y, z)) continue;
                spots.add(new int[] {x, z, wall.ordinal()});
            }
        spots.sort(Comparator.comparingDouble(a -> angle(a[0], a[1])));
        if (!spots.isEmpty() && spec.has(BuildingSpec.Feature.INTERIOR)) {
            int start = rng.nextInt(spots.size());
            boolean dense = type.equals("library") || type.equals("storage") || type.equals("office");
            int step = dense ? 1 : 2;
            int i = 0;
            for (String item : items(type)) {
                while (i < spots.size()) {
                    int[] sp = spots.get((start + i) % spots.size());
                    i += step;
                    if (!s.free(sp[0], y, sp[1]) || nearReserved(sp[0], y, sp[1])) continue;
                    if (place(item, sp[0], y, sp[1], Dir.values()[sp[2]], ri, ceil)) break;
                }
            }
            // Teppich im Schlaf-/Wohnraum
            if (type.equals("bedroom") || type.equals("living") || type.equals("library")) {
                for (int x = lo(cx, ri); x <= hi(cx, ri); x++)
                    for (int z = lo(cz, ri); z <= hi(cz, ri); z++) {
                        if (!in(x, z, ri - 1) || inSpiral(x, z) || !s.free(x, y, z) || !v.isSolid(x, y - 1, z)) continue;
                        if (nearReserved(x, y, z)) continue;
                        v.set(x, y, z, B.id(p.carpet()));
                    }
            }
        }
        // Licht
        double rl = ringSide > 0 ? ((ringSide - 1) / 2.0 + 1 + ri) / 2.0 : ri * 0.5;
        int placed = 0;
        for (double a : new double[] {45, 135, -135, -45, 0, 90, 180, -90}) {
            if (placed >= (ri >= 4 ? 4 : 2)) break;
            int x = (int) Math.round(cx + rl * Math.cos(Math.toRadians(a)));
            int z = (int) Math.round(cz + rl * Math.sin(Math.toRadians(a)));
            if (!in(x, z, ri) || inSpiral(x, z)) continue;
            if (ceiling) {
                int ly = ceil - 1;
                if (ly > y && s.free(x, ly, z) && v.isSolid(x, ceil, z) && v.isEmpty(x, ly - 1, z)) {
                    v.set(x, ly, z, B.lantern(lantern, true));
                    placed++;
                }
            } else {
                // Wandfackel an der nächsten Wand
                Dir d = inward(x, z).opposite();
                int wx = x, wz = z;
                for (int k = 0; k < 8 && in(wx + d.dx, wz + d.dz, ri); k++) { wx += d.dx; wz += d.dz; }
                int ty = y + 2;
                if (v.isSolid(wx + d.dx, ty, wz + d.dz) && v.isEmpty(wx, ty, wz) && !inSpiral(wx, wz)) {
                    v.set(wx, ty, wz, B.wallTorch(d.opposite()));
                    placed++;
                }
            }
        }
        if (!ceiling) chandelier(y);
    }

    /** Leuchter an Ketten unter der Dachspitze bzw. Laterne auf der Spindel. */
    private void chandelier(int y) {
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        if (ringSide > 0 && v.isEmpty(x, y, z) && v.isSolid(x, y - 1, z) && inSpiral(x, z)) {
            v.set(x, y, z, B.lantern(lantern, false));
        }
        int topY = -1;
        for (int yy = y + 4; yy < s.h - 1; yy++) {
            if (v.isEmpty(x, yy, z) && v.isSolid(x, yy + 1, z)) { topY = yy; break; }
            if (!v.isEmpty(x, yy, z)) break;
        }
        if (topY < 0) return;
        int bottom = Math.max(y + 4, topY - 4);
        for (int yy = topY; yy > bottom; yy--) v.set(x, yy, z, B.with("iron_chain", "axis", "y"));
        v.set(x, bottom, z, B.lantern(lantern, true));
    }

    private boolean place(String item, int x, int y, int z, Dir toWall, double ri, int ceil) {
        Dir away = toWall.opposite();
        switch (item) {
            case "#bed" -> {
                int fx = x + away.dx, fz = z + away.dz;
                if (!in(fx, fz, ri) || inSpiral(fx, fz) || !s.free(fx, y, fz) || !v.isSolid(fx, y - 1, fz)
                    || nearReserved(fx, y, fz)) return false;
                v.set(fx, y, fz, B.bed(p.bed(), toWall));
                v.set(x, y, z, B.AIR);
                s.reserve(x, y, z);
                s.reserve(fx, y, fz);
                return true;
            }
            case "#plant" -> v.set(x, y, z, B.id(rng.pick(List.of("potted_fern", "potted_azalea_bush", "potted_red_tulip",
                "potted_oak_sapling", "potted_cornflower"))));
            case "#lectern" -> v.set(x, y, z, B.with("lectern", "facing", away.id()));
            case "#anvil" -> v.set(x, y, z, B.with("anvil", "facing", toWall.clockwise().id()));
            case "#chair" -> v.set(x, y, z, B.stairs(woodF, toWall, false));
            case "#cauldron" -> v.set(x, y, z, B.with("water_cauldron", "level", "3"));
            case "#brew" -> v.set(x, y, z, B.id("brewing_stand"));
            case "#enchant" -> v.set(x, y, z, B.id("enchanting_table"));
            case "#hay" -> {
                v.set(x, y, z, B.axis("hay_block", 'y'));
                if (y + 1 < ceil - 1 && s.free(x, y + 1, z)) v.set(x, y + 1, z, B.axis("hay_block", 'y'));
            }
            case "bookshelf" -> {
                v.set(x, y, z, B.id("bookshelf"));
                if (y + 1 < ceil && s.free(x, y + 1, z) && v.isSolid(x + toWall.dx, y + 1, z + toWall.dz))
                    v.set(x, y + 1, z, B.id("bookshelf"));
            }
            case "barrel", "chest", "furnace", "smoker" -> {
                v.set(x, y, z, B.with(item, "facing", away.id()));
                if (item.equals("barrel") && rng.chance(0.4) && y + 1 < ceil - 1 && s.free(x, y + 1, z)) {
                    v.set(x, y + 1, z, B.with("barrel", "facing", "up"));
                }
            }
            default -> {
                String st = item.contains("[") ? B.id(item.substring(0, item.indexOf('['))) + item.substring(item.indexOf('['))
                    : B.id(item);
                v.set(x, y, z, st);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------- Außenanlagen

    private void groundTexture() {
        if (style.sandy()) return;
        double rmax = (lighthouse ? Math.max(R + 1, rTop + 2) : R) + 3;
        for (int x = lo(cx, rmax); x <= hi(cx, rmax); x++)
            for (int z = lo(cz, rmax); z <= hi(cz, rmax); z++) {
                if (!v.in(x, 0, z) || in(x, z, R + 1) || !in(x, z, rmax) || !ground(x, z)) continue;
                double r = rng.nextDouble();
                if (r < 0.10) v.set(x, 0, z, B.id("coarse_dirt"));
                else if (r < 0.16) v.set(x, 0, z, B.id("moss_block"));
                else if (r < 0.20) v.set(x, 0, z, B.id("gravel"));
            }
    }

    private void exterior() {
        int W = s.w, D = s.d;
        // Weg zur Tür
        int[] xs = tangent(Dir.NORTH, doorW);
        int[] c = outerCell(Dir.NORTH, xs[0], 1);
        int zEnd = c == null ? 0 : c[1] - 1;
        int widen = n >= 11 ? 1 : 0;
        for (int z = 0; z <= zEnd; z++)
            for (int x = xs[0] - widen; x <= xs[doorW - 1] + widen; x++) {
                String g = v.get(x, 0, z);
                if (g == null || !(ground(x, z) || B.name(g).equals(B.name(p.path())))) continue;
                boolean edgeCell = x < xs[0] || x > xs[doorW - 1];
                v.set(x, 0, z, B.id(edgeCell && rng.chance(0.5) ? "gravel" : p.path()));
                if (z < zEnd - 3) {
                    s.reserve(x, 1, z);
                }
            }
        for (int z = 0; z <= zEnd; z++) for (int x = xs[0]; x <= xs[doorW - 1]; x++) s.reserve(x, 1, z);
        if (spec.has(BuildingSpec.Feature.LANTERNS) && zEnd >= 3) {
            s.lanternPosts(xs[0] - widen, 1, doorW + 2 * widen);
        }
        // Felsbrocken
        int rocks = 2 + (int) (R / 2);
        double rr = lighthouse ? R + 4 : R + 2.5;
        for (int i = 0; i < rocks * 3 && rocks > 0; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int x = (int) Math.round(cx + rr * Math.cos(a)), z = (int) Math.round(cz + rr * Math.sin(a));
            if (!s.free(x, 1, z) || !ground(x, z) || nearReserved(x, 1, z)) continue;
            v.set(x, 1, z, B.id(rng.pick(List.of("cobblestone", "mossy_cobblestone", "andesite", "stone"))));
            if (rng.chance(0.5)) {
                Dir d = rng.pick(List.of(Dir.values()));
                if (s.free(x + d.dx, 1, z + d.dz) && ground(x + d.dx, z + d.dz)) {
                    v.set(x + d.dx, 1, z + d.dz, B.id(rng.pick(List.of("cobblestone", "mossy_cobblestone", "andesite"))));
                }
            }
            rocks--;
        }
        // Büsche dicht an der Mauer
        double rb = R + 1;
        for (int x = lo(cx, rb + 1); x <= hi(cx, rb + 1); x++)
            for (int z = lo(cz, rb + 1); z <= hi(cz, rb + 1); z++) {
                if (in(x, z, rb) || !in(x, z, rb + 1) || !s.free(x, 1, z) || !ground(x, z) || nearReserved(x, 1, z)) continue;
                if (rng.chance(0.18)) v.set(x, 1, z, B.leaves(p.leaves()));
            }
        if (spec.has(BuildingSpec.Feature.TREES)) {
            int[][] corners = {{2, 2}, {W - 3, 2}, {2, D - 3}, {W - 3, D - 3}};
            for (int[] k : corners) {
                double dist = Math.hypot(k[0] - cx, k[1] - cz);
                if (dist < R + 5 || !s.free(k[0], 1, k[1]) || !ground(k[0], k[1])) continue;
                if (annexDir != 0 && Math.abs(k[1] - cz) < annexD / 2.0 + 3 && Math.signum(k[0] - cx) == annexDir) continue;
                s.tree(k[0], k[1], 3 + rng.nextInt(2));
            }
        }
        boolean fence = spec.has(BuildingSpec.Feature.FENCE) && fenceFits();
        if (fence) s.fence(xs[0] - widen, doorW + 2 * widen);
        boolean garden = spec.has(BuildingSpec.Feature.GARDEN) || fence;
        if (spec.has(BuildingSpec.Feature.GARDEN)) s.garden(0.6);
        s.flowersAndBushes(0, 0, W - 1, D - 1, garden ? 0.08 : 0.04, garden ? 0.025 : 0.012);
    }

    /** Ringmauer mit Ecktürmchen und Tor auf großen Bauplätzen (Burghof um den Turm). */
    private void curtainWall() {
        int W = s.w, D = s.d, H = s.h;
        if (W < 25 || D < 25 || H < 10) return;
        for (int y = 1; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++)
                    if ((x < 4 || z < 4 || x > W - 5 || z > D - 5) && !v.isEmpty(x, y, z)) return;
        int[] xs = tangent(Dir.NORTH, doorW);
        int widen = n >= 11 ? 1 : 0;
        int gx0 = xs[0] - widen, gx1 = xs[doorW - 1] + widen;
        if (gx1 - gx0 < 2) { gx0--; gx1++; }
        int x0 = 1, x1 = W - 2, z0 = 1, z1 = D - 2;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                if (z == z0 && x >= gx0 - 1 && x <= gx1 + 1) continue;
                v.set(x, 0, z, s.vary(base));
                for (int y = 1; y <= 3; y++) v.set(x, y, z, stone(y));
                if (Math.floorMod(x + z, 2) == 0) v.set(x, 4, z, stone(4));
            }
        // Ecktürmchen
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            for (int x = c[0] - 1; x <= c[0] + 1; x++)
                for (int z = c[1] - 1; z <= c[1] + 1; z++) {
                    v.set(x, 0, z, s.vary(base));
                    for (int y = 1; y <= 5; y++) v.set(x, y, z, y == 5 ? B.id(trimF.block()) : stone(y));
                    boolean outer = x != c[0] || z != c[1];
                    if (outer && Math.floorMod(x + z, 2) == 0) v.set(x, 6, z, stone(6));
                }
            v.set(c[0], 6, c[1], B.lantern(lantern, false));
        }
        // Tor mit Rundbogen und Pfeilern
        for (int x = gx0; x <= gx1; x++) {
            v.set(x, 1, z0, B.AIR);
            v.set(x, 2, z0, B.AIR);
            v.set(x, 3, z0, x == gx0 ? B.stairs(trimF, Dir.WEST, true) : x == gx1 ? B.stairs(trimF, Dir.EAST, true) : B.AIR);
            v.set(x, 4, z0, stone(4));
            if (Math.floorMod(x + z0, 2) == 0) v.set(x, 5, z0, stone(5));
            v.set(x, 0, z0, B.id(p.path()));
        }
        for (int x : new int[] {gx0 - 1, gx1 + 1}) {
            v.set(x, 0, z0, s.vary(base));
            for (int y = 1; y <= 5; y++) v.set(x, y, z0, stone(y));
            v.set(x, 6, z0, B.lantern(lantern, false));
            if (v.isEmpty(x, 2, z0 - 1)) v.set(x, 3, z0 - 1, B.wallTorch(Dir.NORTH));
        }
        for (int z = 0; z <= z0 + 1; z++) for (int x = gx0; x <= gx1; x++) s.reserve(x, 1, z);
        well();
        yardProps(x0 + 2, z0 + 2, x1 - 2, z1 - 2);
    }

    /** Bäume, Heu, Fässer und Bänke im Burghof. */
    private void yardProps(int ax, int az, int bx, int bz) {
        int area = (bx - ax) * (bz - az);
        int trees = Math.min(10, area / 120);
        for (int i = 0; i < trees * 6 && trees > 0; i++) {
            int x = ax + 1 + rng.nextInt(Math.max(1, bx - ax - 2)), z = az + 1 + rng.nextInt(Math.max(1, bz - az - 2));
            if (Math.hypot(x - cx, z - cz) < R + 6 || !clearArea(x, z, 2, 6)) continue;
            s.tree(x, z, 3 + rng.nextInt(2));
            trees--;
        }
        // Heuballen und Fässer an der Mauer
        int[][] spots = {{ax, bz}, {bx, bz}, {bx, az + 2}, {ax, az + 2}};
        int k = 0;
        for (int[] sp : spots) {
            if (!clearArea(sp[0], sp[1], 1, 3)) continue;
            if (k++ % 2 == 0) {
                for (int x = sp[0] - 1; x <= sp[0]; x++)
                    for (int z = sp[1] - 1; z <= sp[1]; z++) v.set(x, 1, z, B.axis("hay_block", rng.chance(0.5) ? 'x' : 'z'));
                v.set(sp[0], 2, sp[1], B.axis("hay_block", 'y'));
            } else {
                v.set(sp[0], 1, sp[1], B.with("barrel", "facing", "up"));
                v.set(sp[0] - 1, 1, sp[1], B.with("barrel", "facing", "up"));
                v.set(sp[0], 2, sp[1], B.with("barrel", "facing", "up"));
                v.set(sp[0], 1, sp[1] - 1, B.with("chest", "facing", "north"));
            }
        }
        // Laternenpfosten entlang des Hauptwegs
        int[] xs = tangent(Dir.NORTH, doorW);
        int[] c = outerCell(Dir.NORTH, xs[0], 1);
        int zEnd = c == null ? az : c[1] - 4;
        int widen = n >= 11 ? 1 : 0;
        for (int z = az + 2; z < zEnd; z += 5) s.lanternPosts(xs[0] - widen, z, doorW + 2 * widen);
    }

    private boolean clearArea(int x0, int z0, int r, int hgt) {
        for (int x = x0 - r; x <= x0 + r; x++)
            for (int z = z0 - r; z <= z0 + r; z++) {
                if (!v.in(x, 1, z) || !ground(x, z) || s.isReserved(x, 1, z)) return false;
                for (int y = 1; y <= hgt; y++) if (!v.isEmpty(x, y, z)) return false;
            }
        return true;
    }

    /** Brunnen im Hof. */
    private void well() {
        int[][] cand = {
            {(int) Math.round(cx - R - 5), (int) Math.round(cz - R / 2)},
            {(int) Math.round(cx + R + 5), (int) Math.round(cz - R / 2)},
            {(int) Math.round(cx - R - 5), (int) Math.round(cz + R / 2)},
            {(int) Math.round(cx + R + 5), (int) Math.round(cz + R / 2)}};
        for (int[] c : cand) {
            boolean ok = true;
            for (int x = c[0] - 2; x <= c[0] + 2 && ok; x++)
                for (int z = c[1] - 2; z <= c[1] + 2 && ok; z++)
                    for (int y = 1; y <= 5 && ok; y++)
                        if (!v.in(x, y, z) || !v.isEmpty(x, y, z) || s.isReserved(x, 1, z) || !ground(x, z)) ok = false;
            if (!ok) continue;
            for (int x = c[0] - 1; x <= c[0] + 1; x++)
                for (int z = c[1] - 1; z <= c[1] + 1; z++) {
                    boolean mid = x == c[0] && z == c[1];
                    v.set(x, 0, z, mid ? B.id("water") : s.vary(base));
                    if (!mid) v.set(x, 1, z, s.vary("cobblestone"));
                    boolean corner = x != c[0] && z != c[1];
                    if (corner) {
                        v.set(x, 2, z, B.id(p.fence()));
                        v.set(x, 3, z, B.id(p.fence()));
                    }
                    v.set(x, 4, z, B.slab(woodF, false));
                    s.reserve(x, 1, z);
                }
            v.set(c[0], 3, c[1], B.lantern(lantern, true));
            v.set(c[0], 4, c[1], B.id(woodF.block()));
            return;
        }
    }

    private boolean fenceFits() {
        int W = s.w, D = s.d;
        for (int y = 1; y < Math.min(s.h, 12); y++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (x > 0 && x < W - 1 && z > 0 && z < D - 1) continue;
                    String st = v.get(x, y, z);
                    if (!B.isAir(st)) return false;
                }
        return true;
    }

    // =================================================================== Leuchtturm

    private double lhR(int y) {
        if (y >= yDeck) return rTop;
        if (y <= 2) return R;
        double f = (y - 2) / (double) Math.max(1, yDeck - 3);
        return R - (R - rTop) * Math.min(1, f);
    }

    private void planLighthouse() {
        int W = s.w, H = s.h, D = s.d;
        int minWD = Math.min(W, D);
        round = true;
        t = 1;
        fh = 6;
        for (n = 13; n >= 7; n -= 2) {
            R = n / 2.0;
            rTop = Math.max(3.5, R - (n >= 11 ? 2 : 1));
            double E = Math.max(R + 1, rTop + 2);
            if (footprint(E) > minWD) continue;
            int capL = coneRadii(rTop + 1, 1.0).length;
            int yMax = H - 1 - (3 + capL + 1);
            int want = Math.max(spec.floors() * fh, (int) Math.round(3.4 * n));
            yDeck = Math.min(Math.min(yMax, want), 50);
            if (yDeck < Math.max(10, 2 * n - 2)) continue;
            floors = 1 + Math.max(0, (yDeck - 4) / fh);
            ringSide = 3;
            spiralTopY = yDeck;
            stripeH = yDeck >= 30 ? 4 : 3;
            plinthH = 1;
            int fw = footprint(E);
            int off = (fw - n) / 2;
            int maxAnnex = W - 1 - (off + n - 1);
            annexDir = 0;
            int x0;
            if (maxAnnex >= 7 && D >= 9) {
                annexW = Math.min(9, maxAnnex);
                annexDir = rng.chance(0.5) ? 1 : -1;
                int slack = maxAnnex - annexW;
                x0 = annexDir > 0 ? off + slack / 2 : W - 1 - off - (n - 1) - slack / 2;
                annexD = Math.min(7, n - 2);
                annexFloors = 1;
            } else {
                x0 = off + (W - fw) / 2;
            }
            int z0 = off + (D - fw) / 2 + (D - fw) / 4;
            cx = x0 + (n - 1) / 2.0;
            cz = z0 + (n - 1) / 2.0;
            buildRing();
            return;
        }
        mini = true;
    }

    private void buildLighthouse() {
        groundTexture();
        rocks();
        lhShaft();
        lhTop();
        spiral(yDeck);
        lhWindows();
        doorFront();
        if (annexDir != 0) cottage();
        interiorLighthouse();
        exterior();
    }

    private String stripe(int y) {
        if (y <= 2) return s.vary(base);
        return B.id(((y - 3) / stripeH) % 2 == 0 ? red : white);
    }

    private void rocks() {
        double ph1 = rng.nextDouble() * 6.283, ph2 = rng.nextDouble() * 6.283;
        boolean desert = style.sandy();
        double border = Math.min(Math.min(cx, s.w - 1 - cx), Math.min(cz, s.d - 1 - cz));
        double reach = Math.min(R + 4.5, border + 0.4);
        for (int x = lo(cx, reach); x <= hi(cx, reach); x++)
            for (int z = lo(cz, reach); z <= hi(cz, reach); z++) {
                if (!v.in(x, 0, z)) continue;
                double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
                if (d <= R - 0.01 || d > reach) continue;
                if (Math.abs(dx) <= 1.5 && dz < 0) continue; // Weg zur Tür
                double a = Math.atan2(dz, dx);
                double hgt = 2.4 - (d - R) * 0.6 + 0.8 * Math.sin(3 * a + ph1) + 0.5 * Math.sin(7 * a + ph2)
                    + (rng.nextDouble() - 0.5) * 0.7;
                hgt = Math.min(hgt, (reach - d) * 1.5);
                int hh = (int) Math.floor(hgt);
                if (d <= R + 1.2) hh = Math.min(hh, 1);
                hh = Math.max(0, Math.min(3, hh));
                if (d <= R + 3.5 || hh > 0) v.set(x, 0, z, rock(desert, false));
                for (int y = 1; y <= hh; y++) v.set(x, y, z, rock(desert, y == hh));
            }
    }

    private String rock(boolean desert, boolean topLayer) {
        double r = rng.nextDouble();
        if (desert) return B.id(r < 0.5 ? "sandstone" : r < 0.8 ? "smooth_sandstone" : "terracotta");
        if (topLayer && r < 0.10) return B.id("moss_block");
        if (r < 0.35) return B.id("stone");
        if (r < 0.60) return B.id("andesite");
        if (r < 0.78) return B.id("cobblestone");
        if (r < 0.90) return B.id("mossy_cobblestone");
        return B.id("tuff");
    }

    private void lhShaft() {
        double r1 = R + 1;
        for (int x = lo(cx, r1); x <= hi(cx, r1); x++)
            for (int z = lo(cz, r1); z <= hi(cz, r1); z++) {
                if (in(x, z, R)) v.set(x, 0, z, in(x, z, R - 1) ? B.id(groundFloor) : B.id(base));
                else if (shell(x, z, R)) {
                    v.set(x, 1, z, s.vary(base));
                    v.set(x, 2, z, B.stairs(baseF, inward(x, z), false));
                }
            }
        for (int y = 1; y < yDeck; y++) {
            double r = lhR(y);
            for (int x = lo(cx, r); x <= hi(cx, r); x++)
                for (int z = lo(cz, r); z <= hi(cz, r); z++) {
                    if (!in(x, z, r)) continue;
                    v.set(x, y, z, in(x, z, r - 1) ? B.AIR : stripe(y));
                }
        }
        for (int f = 1; f < floors; f++) {
            int y = f * fh;
            fillDisk(y, lhR(y) - 1, B.id(floorBlock));
        }
    }

    private void lhTop() {
        int yG = yDeck;
        double rg = rTop + 2;
        // Kragsteine unter der Galerie
        for (int k = 1; k <= 2; k++) {
            int y = yG - k;
            double rw = lhR(y), ro = rg - k;
            for (int x = lo(cx, ro); x <= hi(cx, ro); x++)
                for (int z = lo(cz, ro); z <= hi(cz, ro); z++)
                    if (in(x, z, ro) && !in(x, z, rw)) v.set(x, y, z, B.stairs(darkF, inward(x, z), true));
        }
        // Galerieboden und Geländer
        for (int x = lo(cx, rg); x <= hi(cx, rg); x++)
            for (int z = lo(cz, rg); z <= hi(cz, rg); z++) {
                if (!in(x, z, rg)) continue;
                v.set(x, yG, z, in(x, z, rg - 1) ? B.id(darkF.block()) : B.slab(darkF, true));
                if (edge(x, z, rg) && yG + 1 < s.h) v.set(x, yG + 1, z, B.id("iron_bars"));
            }
        // Laternenkammer
        for (int y = yG + 1; y <= yG + 3; y++)
            for (int x = lo(cx, rTop); x <= hi(cx, rTop); x++)
                for (int z = lo(cz, rTop); z <= hi(cz, rTop); z++) {
                    if (!in(x, z, rTop) || in(x, z, rTop - 1)) continue;
                    double a = angle(x, z);
                    boolean mullion = false;
                    for (double m : new double[] {45, 135, -45, -135, 0, 180, 90}) if (angDiff(a, m) < 9) mullion = true;
                    v.set(x, y, z, mullion ? B.id(darkF.block()) : B.id("glass"));
                }
        for (int x = lo(cx, rTop); x <= hi(cx, rTop); x++)
            for (int z = lo(cz, rTop); z <= hi(cz, rTop); z++)
                if (in(x, z, rTop) && !in(x, z, rTop - 1)) v.set(x, yG + 4, z, B.id(darkF.block()));
        // Tür zur Galerie
        int xm = (int) Math.round(cx);
        int[] c = outerCell(Dir.NORTH, xm, yG + 1);
        if (c != null) {
            v.set(c[0], yG + 1, c[1], B.door(p.door(), Dir.SOUTH, false));
            v.set(c[0], yG + 2, c[1], B.AIR);
        }
        // Haube
        Materials.Family cap = spec.roofMaterial() != null && !woody(roofF.block()) ? roofF
            : Materials.family("waxed_oxidized_cut_copper");
        cone(yG + 4, rTop + 1, cap);
        // Leuchtfeuer auf der Spindel
        int zm = (int) Math.round(cz);
        v.set(xm, yG + 1, zm, B.id("sea_lantern"));
        v.set(xm, yG + 2, zm, B.id("sea_lantern"));
        v.set(xm, yG + 3, zm, B.id("glowstone"));
    }

    private void lhWindows() {
        Dir[] cycle = {Dir.EAST, Dir.SOUTH, Dir.WEST, Dir.NORTH};
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            int next = f + 1 < floors ? (f + 1) * fh : yDeck;
            List<Dir> dirs = new ArrayList<>();
            if (f == 0) {
                dirs.add(Dir.SOUTH);
                if (annexDir <= 0) dirs.add(Dir.EAST);
                if (annexDir >= 0) dirs.add(Dir.WEST);
            } else if (f == floors - 1) {
                dirs.addAll(List.of(Dir.values()));
            } else {
                dirs.add(cycle[f % 4]);
                dirs.add(cycle[(f + 2) % 4]);
            }
            int y0 = yb + 2, y1 = Math.min(yb + 3, next - 2);
            if (y1 < y0) continue;
            for (Dir o : dirs) {
                int u = (int) Math.round(o.dx == 0 ? cx : cz);
                for (int y = y0; y <= y1; y++) {
                    int[] c = outerCell(o, u, y);
                    if (c != null) v.set(c[0], y, c[1], B.id(window));
                }
                int[] c = outerCell(o, u, y0 - 1);
                if (c != null && f == floors - 1) setOut(c, o, y0 - 1, B.slab(darkF, true));
            }
        }
    }

    private void cottage() {
        int zr = (int) Math.round(cz);
        int za = zr - (annexD - 1) / 2, zb = za + annexD - 1;
        int[] wc = outerCell(annexDir > 0 ? Dir.EAST : Dir.WEST, zr, 1);
        if (wc == null) return;
        int xNear = wc[0];
        int xFar = xNear + annexDir * (annexW - 1);
        annex(xNear, xFar, za, zb, 1, List.of("bedroom"));
        v.set(xNear, 1, zr, B.AIR);
        v.set(xNear, 2, zr, B.AIR);
        for (int y = 1; y <= 2; y++) {
            s.reserve(xNear - annexDir, y, zr);
            s.reserve(xNear + annexDir, y, zr);
        }
    }

    private void interiorLighthouse() {
        List<String> types = roomPlan();
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            int next = f + 1 < floors ? (f + 1) * fh : yDeck;
            furnishRound(yb + 1, next, lhR(yb + 1) - 1, types.get(Math.min(f, types.size() - 1)), true);
        }
    }

    // =================================================================== Mini-Turm (sehr kleine Bauplätze)

    private void miniTower() {
        int W = s.w, D = s.d;
        round = false;
        int m = Math.max(1, Math.min(Math.min(W, D), 5));
        int H = Math.min(s.h, 6 * m + 4);
        int x0 = (W - m) / 2, z0 = (D - m) / 2, x1 = x0 + m - 1, z1 = z0 + m - 1;
        cx = (x0 + x1) / 2.0;
        cz = (z0 + z1) / 2.0;
        n = m;
        Palette.Roof rr = spec.roof();
        boolean cone = !lighthouse && m >= 3 && H >= 8 && (rr == null
            ? switch (style) {
                case STONE, DESERT, CLASSICAL, MODERN -> false;
                default -> true;
            }
            : rr != Palette.Roof.FLAT);
        boolean lamp = lighthouse && m >= 3 && H >= 8;
        boolean crenel = !cone && !lamp && H >= 6 && m >= 3;
        int roofH = cone ? (m + 1) / 2 + 1 : 0;
        int wallTop = cone ? H - 1 - roofH : lamp ? H - 4 : crenel ? H - 3 : H - 1;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean ringCell = x == x0 || x == x1 || z == z0 || z == z1;
                v.set(x, 0, z, B.id(ringCell ? base : groundFloor));
                for (int y = 1; y <= wallTop; y++) {
                    if (!ringCell && y < wallTop) continue;
                    if (!ringCell) {
                        v.set(x, y, z, crenel || lamp ? B.id(groundFloor) : B.slab(trimF, true));
                        continue;
                    }
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    v.set(x, y, z, lighthouse ? stripeMini(y) : corner ? s.vary(base) : stone(y));
                }
                if (crenel && ringCell) {
                    v.set(x, H - 2, z, stone(H - 2));
                    if (Math.floorMod(x + z, 2) == 0) v.set(x, H - 1, z, stone(H - 1));
                }
                if (lamp) {
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    for (int y = wallTop + 1; y <= wallTop + 2; y++) {
                        if (ringCell) v.set(x, y, z, corner ? B.id(darkF.block()) : B.id("glass"));
                    }
                    v.set(x, wallTop + 3, z, ringCell ? B.slab(darkF, false) : B.id(darkF.block()));
                }
            }
        int mx = x0 + m / 2, mz = z0 + m / 2;
        if (cone) {
            // kleines Zeltdach
            for (int k = 0; k < roofH; k++) {
                int y = wallTop + 1 + k;
                int a = x0 + k, b = x1 - k, c = z0 + k, d = z1 - k;
                if (a > b || c > d) break;
                for (int x = a; x <= b; x++)
                    for (int z = c; z <= d; z++) {
                        boolean ring = x == a || x == b || z == c || z == d;
                        if (a == b && c == d) v.set(x, y, z, B.id(roofF.block()));
                        else if (ring) v.set(x, y, z, B.stairs(roofF, inward(x, z), false));
                    }
                if (a == b && c == d && y + 1 < s.h) v.set(a, y + 1, c, B.id(p.fence()));
            }
        }
        if (lamp) {
            v.set(mx, wallTop + 1, mz, B.id("sea_lantern"));
            v.set(mx, wallTop + 2, mz, B.id("glowstone"));
        }
        if (m >= 3 && H >= 3) {
            v.set(mx, 1, z0, B.door(p.door(), Dir.SOUTH, false));
            v.set(mx, 2, z0, B.AIR);
            if (z0 > 0) v.set(mx, 0, z0 - 1, B.id(p.path()));
        }
        boolean ladder = (crenel || lamp) && m >= 3;
        int lx = x1 - 1, lz = z1 - 1;
        if (m >= 3 && wallTop >= 4) {
            for (int y = 3; y < wallTop - 1; y += 4)
                for (int[] w : new int[][] {{x0, mz}, {x1, mz}, {mx, z1}}) {
                    if (ladder && w[0] == lx && w[1] == lz + 1) continue;
                    v.set(w[0], y, w[1], B.id(window));
                }
        }
        if (ladder) {
            for (int y = 1; y <= wallTop; y++) v.set(lx, y, lz, B.ladder(Dir.NORTH));
        }
        if (m >= 4 && H >= 4 && v.isSolid(x0, 2, mz)) v.set(x0 + 1, 2, mz, B.wallTorch(Dir.EAST));
        if (z0 > 0 && m >= 3 && H >= 4 && v.isSolid(mx - 1, 3, z0)) v.set(mx - 1, 3, z0 - 1, B.wallTorch(Dir.NORTH));
    }

    private String stripeMini(int y) {
        if (y <= 1) return s.vary(base);
        return B.id(((y - 2) / 2) % 2 == 0 ? red : white);
    }
}
