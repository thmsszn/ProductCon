package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.List;

/**
 * Burg-Generator. Große Bauplätze: Ringmauer mit Wehrgang, vorgekragter Brustwehr, Zinnen, Strebepfeilern und
 * Sockelschräge; Ecktürme (rund oder eckig) mit Kegel-/Zeltdach oder Zinnenkranz; Torhaus mit Spitzbogen,
 * Fallgitter, Wachstube und Flankentürmen; Innenhof mit Weg, Brunnen, Stall, Schmiede, Marktstand und Garten;
 * Palas/Bergfried mit Rittersaal (Thron, Tafeln, Säulen, Kamin), Treppen, Schlafgemächern und Ecktürmchen.
 * Kleine Bauplätze: Wohnturm mit angebautem Rundturm (optional mit Zwinger); sehr kleine: einfacher Wehrturm.
 */
final class CastleGenerator {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final Rng rng;
    private final int W, H, D;

    private String wallB, baseB, accentB, pillarB, hallA, hallB, logB, doorB, fenceB, glass, bannerColor, light;
    private Materials.Family wallF, baseF, roofF, woodF, throneF;
    private boolean round;
    private Palette.Roof roofKind;

    // Palas / Bergfried
    private int kx0, kz0, kx1, kz1, hh, fh, nUp, yDeck;
    private boolean keepRoofed, keepStairs, keepOverhang, keepViaTower;
    private int keepDoorX, keepDoorW, doorMaxX = Integer.MAX_VALUE;

    CastleGenerator(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p != null ? site.p : Palette.of(Palette.Style.STONE);
        this.spec = site.spec;
        this.rng = site.rng;
        this.W = site.w;
        this.H = site.h;
        this.D = site.d;
    }

    Voxels build() {
        materials();
        s.clearSite(spec.style().sandy() ? "sand" : "grass_block");
        if (W >= 25 && D >= 25 && H >= 14) fullCastle();
        else if (W >= 8 && D >= 8 && H >= 9) smallCastle();
        else tinyTower();
        return v;
    }

    // ------------------------------------------------------------------ Material

    private void materials() {
        wallB = "stone_bricks";
        wallF = fam("stone_brick");
        baseB = "cobblestone";
        baseF = fam("cobblestone");
        accentB = "chiseled_stone_bricks";
        pillarB = "polished_andesite";
        hallA = "polished_andesite";
        hallB = "stone_bricks";
        woodF = fam("spruce");
        throneF = fam("dark_oak");
        logB = "dark_oak_log";
        doorB = "dark_oak_door";
        fenceB = "dark_oak_fence";
        glass = "glass_pane";
        bannerColor = "red";
        light = "lantern";
        switch (spec.style()) {
            case NORDIC -> {
                wallB = "deepslate_bricks"; wallF = fam("deepslate_brick"); baseB = "cobbled_deepslate";
                baseF = fam("cobbled_deepslate"); accentB = "chiseled_deepslate"; pillarB = "polished_deepslate";
                hallA = "polished_deepslate"; hallB = "deepslate_tiles"; woodF = fam("dark_oak"); logB = "spruce_log";
                doorB = "spruce_door"; fenceB = "spruce_fence"; bannerColor = "blue";
            }
            case DESERT -> {
                wallB = "sandstone"; wallF = fam("sandstone"); baseB = "smooth_red_sandstone"; baseF = fam("smooth_red_sandstone");
                accentB = "chiseled_sandstone"; pillarB = "cut_sandstone"; hallA = "smooth_sandstone"; hallB = "cut_sandstone";
                woodF = fam("acacia"); throneF = fam("acacia"); logB = "stripped_acacia_log"; doorB = "acacia_door";
                fenceB = "acacia_fence"; bannerColor = "orange";
            }
            case FANTASY -> {
                wallB = "polished_diorite"; wallF = fam("polished_diorite"); baseB = "stone_bricks"; baseF = fam("stone_brick");
                pillarB = "quartz_pillar"; hallA = "smooth_quartz"; hallB = "polished_diorite"; woodF = fam("dark_oak");
                logB = "stripped_dark_oak_log"; glass = "light_blue_stained_glass_pane"; bannerColor = "light_blue";
                if (B.name(p.light()).endsWith("lantern")) light = B.name(p.light());
            }
            case VICTORIAN -> {
                wallB = "bricks"; wallF = fam("brick"); baseB = "stone_bricks"; baseF = fam("stone_brick");
            }
            case VIKING -> {
                wallB = "mossy_stone_bricks"; wallF = fam("mossy_stone_brick"); baseB = "mossy_cobblestone";
                baseF = fam("mossy_cobblestone"); woodF = fam("dark_oak"); logB = "spruce_log"; doorB = "spruce_door";
                fenceB = "spruce_fence"; bannerColor = "red";
            }
            case GOTHIC -> {
                wallB = "polished_blackstone_bricks"; wallF = fam("polished_blackstone_brick"); baseB = "blackstone";
                baseF = fam("blackstone"); accentB = "chiseled_polished_blackstone"; pillarB = "polished_blackstone";
                hallA = "polished_blackstone"; hallB = "polished_deepslate"; glass = "purple_stained_glass_pane";
                bannerColor = "purple"; light = "soul_lantern";
            }
            case STEAMPUNK -> {
                wallB = "bricks"; wallF = fam("brick"); baseB = "polished_andesite"; baseF = fam("polished_andesite");
                accentB = "waxed_cut_copper"; pillarB = "polished_basalt"; hallA = "polished_andesite";
                hallB = "smooth_stone"; glass = "orange_stained_glass_pane"; bannerColor = "orange";
            }
            case ORIENTAL -> {
                wallB = "smooth_sandstone"; wallF = fam("smooth_sandstone"); baseB = "sandstone"; baseF = fam("sandstone");
                accentB = "chiseled_sandstone"; pillarB = "cut_sandstone"; hallA = "smooth_sandstone"; hallB = "cut_sandstone";
                woodF = fam("acacia"); throneF = fam("acacia"); logB = "stripped_acacia_log"; doorB = "acacia_door";
                fenceB = "acacia_fence"; glass = "light_blue_stained_glass_pane"; bannerColor = "cyan";
            }
            default -> { }
        }
        String chosen = spec.wall();
        if (chosen != null && B.name(B.id(chosen)).equals(B.name(p.wall())) && masonry(B.name(chosen))) {
            wallB = B.name(chosen);
            Materials.Family f = Materials.family(wallB, null);
            if (f != null) wallF = f;
        }
        roofF = p.roof() != null ? p.roof() : fam("deepslate_tile");
        roofKind = spec.roof() != null ? spec.roof()
            : spec.style() == Palette.Style.DESERT ? Palette.Roof.FLAT
            : spec.style() == Palette.Style.NORDIC || spec.style() == Palette.Style.VIKING ? Palette.Roof.HIP : Palette.Roof.CONE;
        round = roofKind == Palette.Roof.CONE || roofKind == Palette.Roof.FLAT;
    }

    private static boolean masonry(String n) {
        if (n.contains("planks") || n.contains("log") || n.contains("concrete") || n.contains("terracotta")) return false;
        for (String k : List.of("brick", "stone", "cobble", "andesite", "diorite", "granite", "deepslate", "tuff",
            "blackstone", "quartz", "calcite", "prismarine", "purpur")) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    private static Materials.Family fam(String key) {
        Materials.Family f = Materials.family(key);
        return f != null ? f : Materials.family("stone_brick");
    }

    // ------------------------------------------------------------------ kleine Helfer

    private static int clamp(int x, int lo, int hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /** Gealtertes Mauerwerk, in Bodennähe mehr Moos. */
    private String wallAt(int y) {
        if (y <= 2 && rng.chance(0.12)) return mossy(wallB);
        return s.vary(wallB);
    }

    private String mossy(String b) {
        return switch (B.name(b)) {
            case "stone_bricks" -> B.id("mossy_stone_bricks");
            case "cobblestone" -> B.id("mossy_cobblestone");
            default -> s.vary(b);
        };
    }

    private String base() {
        return s.vary(baseB);
    }

    private static String stairs(Materials.Family f, Dir facing) {
        return B.stairs(f, facing, false);
    }

    /** Umgedrehte Treppe (Konsole/Kragstein): der untere Teil liegt auf der Seite {@code toward}. */
    private static String corbel(Materials.Family f, Dir toward) {
        return B.stairs(f, toward, true);
    }

    private void put(int x, int y, int z, String state) {
        if (v.isEmpty(x, y, z)) v.set(x, y, z, state);
    }

    private static boolean merlon(int a) {
        return Math.floorMod(a, 2) == 0;
    }

    private boolean ground(int x, int z) {
        String g = v.get(x, 0, z);
        if (g == null) return false;
        String n = B.name(g);
        return n.equals("grass_block") || n.equals("sand") || n.equals("coarse_dirt") || n.equals("gravel")
            || n.equals("dirt") || n.equals("dirt_path");
    }

    /** Rechteck frei (leer, nicht reserviert, natürlicher Boden) für y = 1..yTop. */
    private boolean areaFree(int x0, int z0, int x1, int z1, int yTop) {
        if (x0 > x1 || z0 > z1) return false;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (!ground(x, z)) return false;
                for (int y = 1; y <= yTop; y++) if (!s.free(x, y, z)) return false;
            }
        return true;
    }

    private void reserveArea(int x0, int z0, int x1, int z1, int y0, int y1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) s.reserve(x, y, z);
    }

    private static int[] posts(int len, int gap) {
        int segments = Math.max(1, (int) Math.round((len - 1) / (double) gap));
        int[] out = new int[segments + 1];
        for (int i = 0; i <= segments; i++) out[i] = (int) Math.round(i * (len - 1) / (double) segments);
        return out;
    }

    private static boolean contains(int[] arr, int value) {
        for (int a : arr) if (a == value) return true;
        return false;
    }

    /** Richtung von einer Zelle (Versatz dx,dz zur Mitte) zur Mitte hin. */
    private static Dir toCenter(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Dir.WEST : Dir.EAST;
        return dz > 0 ? Dir.NORTH : Dir.SOUTH;
    }

    // ------------------------------------------------------------------ Grundformen (rund / eckig)

    private static boolean in(boolean rnd, int dx, int dz, double r) {
        if (r < 0) return false;
        if (rnd) return dx * dx + dz * dz <= r * r + r * 0.8 + 0.001;
        double q = Math.floor(r + 0.001);
        return Math.abs(dx) <= q && Math.abs(dz) <= q;
    }

    private static boolean edge(boolean rnd, int dx, int dz, double r) {
        return in(rnd, dx, dz, r) && (!in(rnd, dx + 1, dz, r) || !in(rnd, dx - 1, dz, r)
            || !in(rnd, dx, dz + 1, r) || !in(rnd, dx, dz - 1, r));
    }

    /**
     * Radien der Dachschichten: Traufe mit r0, danach jeder ganzzahlige Radius doppelt (steil, ca. 63°) bzw. einfach
     * (flach, 45°); zuletzt die Spitze (Radius 0).
     */
    private static List<Integer> radii(double r0, double step) {
        List<Integer> out = new ArrayList<>();
        int r = (int) Math.round(r0);
        out.add(r);
        boolean steep = step < 0.7;
        for (int q = r - 1; q >= 1; q--) {
            out.add(q);
            if (steep) out.add(q);
        }
        out.add(0);
        return out;
    }

    /** Anzahl der Dachschichten eines Kegel-/Zeltdachs (inklusive Spitze). */
    private static int spireLayers(boolean rnd, double r0, double step) {
        return radii(r0, step).size();
    }

    /** Kegeldach (rund) bzw. Zeltdach (eckig) über (cx,cz) ab y0; Treppen zeigen zur Mitte. Liefert die Spitzenhöhe. */
    private int spire(int cx, int cz, boolean rnd, double r0, int y0, double step, boolean flag) {
        List<Integer> rs = radii(r0, step);
        for (int i = 0; i < rs.size(); i++) {
            int y = y0 + i;
            if (y >= H) return H - 1;
            int r = rs.get(i);
            if (r == 0) {
                v.set(cx, y, cz, B.id(roofF.block()));
                finial(cx, y + 1, cz, flag);
                return y;
            }
            int rn = rs.get(i + 1);
            boolean last = y + 1 >= H;
            for (int dx = -r - 1; dx <= r + 1; dx++)
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (!in(rnd, dx, dz, r)) continue;
                    boolean e = edge(rnd, dx, dz, r);
                    boolean covered = !last && (rn == 0 ? dx == 0 && dz == 0 : in(rnd, dx, dz, rn));
                    if (e && !covered) v.set(cx + dx, y, cz + dz, stairs(roofF, toCenter(dx, dz)));
                    else if (e || !covered) v.set(cx + dx, y, cz + dz, B.id(roofF.block()));
                }
        }
        return H - 1;
    }

    /** Spitze: Zaunpfosten, bei {@code flag} mit Fahne. */
    private void finial(int x, int y, int z, boolean flag) {
        if (y >= H) return;
        v.set(x, y, z, B.id(fenceB));
        if (flag && y + 2 < H) {
            v.set(x, y + 1, z, B.id(fenceB));
            v.set(x, y + 2, z, B.id(fenceB));
            int d = x + 2 < W ? 1 : -1;
            String wool = B.id(bannerColor + "_wool");
            v.set(x + d, y + 2, z, wool);
            v.set(x + 2 * d, y + 2, z, wool);
            v.set(x + d, y + 1, z, wool);
        }
    }

    // ------------------------------------------------------------------ Turm

    /**
     * Turm um (cx,cz) mit Radius r. Unter {@code yHollow} massiv, darüber hohl mit Holzböden auf {@code floors},
     * Deck auf {@code top}. cap: 0 = Zinnenkranz auf Kragsteinen, 1 = Kegel-/Zeltdach. clip = {x0,z0,x1,z1,yMax}:
     * dort keine Außenwand (der Turm öffnet sich in einen Saal). ladder: Wandseite für die Leiter (oder null).
     */
    private void tower(int cx, int cz, int r, boolean rnd, int yHollow, int top, int[] floors, int cap, double step,
                       boolean flag, int[] clip, boolean glassSlits, Dir ladder) {
        int ext = r + 1;
        for (int dx = -ext; dx <= ext; dx++)
            for (int dz = -ext; dz <= ext; dz++) {
                int x = cx + dx, z = cz + dz;
                if (in(rnd, dx, dz, r)) {
                    v.set(x, 0, z, base());
                } else if (in(rnd, dx, dz, ext) && v.in(x, 1, z)) {
                    String at = v.get(x, 1, z);
                    if ((B.isAir(at) && ground(x, z)) || B.name(at).endsWith("_stairs") && !at.contains("half=top")) {
                        v.set(x, 1, z, stairs(baseF, toCenter(dx, dz)));
                        v.set(x, 0, z, base());
                    }
                }
            }
        for (int y = 1; y <= top && y < H; y++) {
            boolean isFloor = contains(floors, y);
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (!in(rnd, dx, dz, r)) continue;
                    int x = cx + dx, z = cz + dz;
                    boolean e = edge(rnd, dx, dz, r);
                    boolean clipped = clip != null && y <= clip[4] && x >= clip[0] && x <= clip[2] && z >= clip[1] && z <= clip[3];
                    String st;
                    if (e && !clipped) st = y == 1 ? base() : wallAt(y);
                    else if (y < yHollow) st = wallAt(y);
                    else if (y == top) st = B.id(woodF.block());
                    else if (isFloor) st = B.id(woodF.block());
                    else st = B.AIR;
                    v.set(x, y, z, st);
                }
        }
        if (ladder != null && r >= 1) {
            int lx = cx + ladder.dx * (r - 1), lz = cz + ladder.dz * (r - 1);
            for (int y = Math.max(1, yHollow); y <= top && y < H; y++) {
                v.set(lx, y, lz, B.ladder(ladder.opposite()));
                s.reserve(lx, y, lz);
                s.reserve(lx - ladder.dx, y, lz - ladder.dz);
            }
        }
        slits(cx, cz, r, rnd, yHollow, top, floors, clip, glassSlits);
        // Licht im Turm
        for (int f : floors) {
            if (f >= yHollow - 1 && f < top) s.hangingLight(cx, Math.min(top, nextLevel(floors, f, top)) - 1, cz);
        }
        if (yHollow <= 1) s.hangingLight(cx, Math.min(top, nextLevel(floors, 0, top)) - 1, cz);
        if (top + 1 >= H) return;
        if (cap == 0) {
            for (int dx = -ext; dx <= ext; dx++)
                for (int dz = -ext; dz <= ext; dz++) {
                    if (!in(rnd, dx, dz, ext) || in(rnd, dx, dz, r)) continue;
                    int x = cx + dx, z = cz + dz;
                    put(x, top - 1, z, corbel(wallF, toCenter(dx, dz)));
                    v.set(x, top, z, wallAt(top));
                    v.set(x, top + 1, z, wallAt(top + 1));
                    if (merlon(x + z)) v.set(x, top + 2, z, wallAt(top + 2));
                }
            if (r >= 2) put(cx, top + 1, cz, B.lantern(light, false));
            if (flag) finial(cx, top + 1, cz, true);
        } else {
            for (int dx = -ext; dx <= ext; dx++)
                for (int dz = -ext; dz <= ext; dz++) {
                    if (!in(rnd, dx, dz, ext) || in(rnd, dx, dz, r)) continue;
                    if (merlon(cx + dx + cz + dz)) put(cx + dx, top, cz + dz, corbel(wallF, toCenter(dx, dz)));
                }
            spire(cx, cz, rnd, ext, top + 1, step, flag);
        }
    }

    /** Gurtgesims: umlaufende Treppenkante um den Turmschaft. */
    private void towerBand(int cx, int cz, int r, boolean rnd, int y) {
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                if (!in(rnd, dx, dz, r + 1) || in(rnd, dx, dz, r)) continue;
                put(cx + dx, y, cz + dz, stairs(wallF, toCenter(dx, dz)));
            }
    }

    /** Fässer/Truhen an der Turmwand eines Geschosses. */
    private void towerStuff(int cx, int cz, int r, boolean rnd, int y) {
        int placed = 0;
        List<String> items = List.of("barrel", "chest", "hay_block", "barrel");
        for (int dx = -r + 1; dx <= r - 1 && placed < 3; dx++)
            for (int dz = -r + 1; dz <= r - 1 && placed < 3; dz++) {
                if (!in(rnd, dx, dz, r) || edge(rnd, dx, dz, r)) continue;
                int x = cx + dx, z = cz + dz;
                if (!s.free(x, y, z) || !v.isSolid(x, y - 1, z) || !v.isEmpty(x, y + 1, z)) continue;
                boolean nearShell = false, nearOpen = false;
                for (Dir d : Dir.values()) {
                    if (edge(rnd, dx + d.dx, dz + d.dz, r) && v.isSolid(x + d.dx, y, z + d.dz)) nearShell = true;
                    if (s.isReserved(x + d.dx, y, z + d.dz) || (edge(rnd, dx + d.dx, dz + d.dz, r) && !v.isSolid(x + d.dx, y, z + d.dz))) nearOpen = true;
                }
                if (!nearShell || nearOpen || (dx == 0 && dz == 0)) continue;
                String it = items.get((placed + Math.abs(cx + cz)) % items.size());
                v.set(x, y, z, it.equals("hay_block") ? B.axis(it, 'y') : B.with(it, "facing", toCenter(-dx, -dz).id()));
                if (it.equals("barrel")) v.set(x, y, z, B.with("barrel", "facing", "up"));
                s.reserve(x, y, z);
                placed++;
            }
    }

    private static int nextLevel(int[] floors, int f, int top) {
        int best = top;
        for (int g : floors) if (g > f && g < best) best = g;
        return best;
    }

    /** Schießscharten (Luft) oder schmale Fenster in jedem Geschoss, nur wo außen frei ist. */
    private void slits(int cx, int cz, int r, boolean rnd, int yHollow, int top, int[] floors, int[] clip, boolean glassSlits) {
        List<Integer> lv = new ArrayList<>();
        lv.add(Math.max(0, yHollow - 1));
        for (int f : floors) if (f >= yHollow && f < top) lv.add(f);
        lv.add(top);
        lv.sort(Integer::compare);
        int dd = 0;
        while (in(rnd, dd + 1, dd + 1, r)) dd++;
        for (int i = 0; i + 1 < lv.size(); i++) {
            int a = lv.get(i), b = lv.get(i + 1);
            if (b - a < 3) continue;
            int y0 = a + 2, y1 = b - a >= 4 ? a + 3 : a + 2;
            List<int[]> cells = new ArrayList<>();
            for (Dir d : Dir.values()) cells.add(new int[] {d.dx * r, d.dz * r, d.dx, d.dz, 0});
            if (rnd && r >= 3 && dd > 0) {
                for (int sx : new int[] {-1, 1})
                    for (int sz : new int[] {-1, 1}) cells.add(new int[] {sx * dd, sz * dd, sx, sz, 1});
            }
            for (int[] c : cells) {
                int x = cx + c[0], z = cz + c[1];
                if (clip != null && x >= clip[0] && x <= clip[2] && z >= clip[1] && z <= clip[3]) continue;
                boolean ok = true;
                for (int y = y0; y <= y1 && ok; y++) {
                    if (!v.isSolid(x, y, z)) ok = false;
                    if (c[4] == 0) {
                        if (!v.isEmpty(x + c[2], y, z + c[3])) ok = false;
                        if (!v.isEmpty(x - c[2], y, z - c[3])) ok = false;
                    } else {
                        if (!v.isEmpty(x + c[2], y, z) || !v.isEmpty(x, y, z + c[3])) ok = false;
                        if (!v.isEmpty(x - c[2], y, z - c[3])) ok = false;
                    }
                }
                if (!ok) continue;
                for (int y = y0; y <= y1; y++) v.set(x, y, z, glassSlits ? B.id(glass) : B.AIR);
            }
        }
    }

    /** Tür in die Turmwand Richtung {@code out}; bevorzugt Zellen weit in Richtung {@code perp}. */
    private boolean towerDoor(int cx, int cz, int r, boolean rnd, Dir out, Dir perp) {
        int best = Integer.MIN_VALUE, bx = 0, bz = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (!in(rnd, dx, dz, r) || in(rnd, dx + out.dx, dz + out.dz, r)) continue;
                int ix = dx - out.dx, iz = dz - out.dz;
                if (!in(rnd, ix, iz, r) || edge(rnd, ix, iz, r)) continue;
                int x = cx + dx, z = cz + dz, ox = x + out.dx, oz = z + out.dz;
                if (!plinthOrFree(ox, 1, oz) || !s.free(ox, 2, oz)) continue;
                if (v.in(ox + out.dx, 1, oz + out.dz) && !plinthOrFree(ox + out.dx, 1, oz + out.dz)) continue;
                if (!v.isEmpty(cx + ix, 1, cz + iz) || !v.isEmpty(cx + ix, 2, cz + iz)) continue;
                String lintel = v.get(x, 3, z);
                if (lintel == null || !(v.isSolid(x, 3, z) || B.isAir(lintel))) continue;
                int score = dx * perp.dx + dz * perp.dz;
                if (score > best) { best = score; bx = x; bz = z; }
            }
        if (best == Integer.MIN_VALUE) return false;
        for (int k = 1; k <= 2; k++) {
            int ox = bx + out.dx * k, oz = bz + out.dz * k;
            if (B.name(String.valueOf(v.get(ox, 1, oz))).endsWith("_stairs")) v.set(ox, 1, oz, B.AIR);
        }
        door(bx, 1, bz, out, false);
        if (v.isEmpty(bx, 3, bz)) v.set(bx, 3, bz, wallAt(3));
        return true;
    }

    private boolean plinthOrFree(int x, int y, int z) {
        if (s.free(x, y, z)) return true;
        String at = v.get(x, y, z);
        return at != null && !s.isReserved(x, y, z) && B.name(at).endsWith("_stairs") && at.contains("half=bottom")
            && B.name(at).startsWith(B.name(baseF.stairs()).replace("_stairs", ""));
    }

    /** Tür (untere Hälfte) in einer Wand mit Außenrichtung {@code out}; Vor- und Innenfeld bleiben frei. */
    private void door(int x, int y, int z, Dir out, boolean hingeRight) {
        v.set(x, y, z, B.door(doorB, out.opposite(), hingeRight));
        v.set(x, y + 1, z, B.AIR);
        for (int yy = y; yy <= y + 1; yy++) {
            s.reserve(x + out.dx, yy, z + out.dz);
            s.reserve(x - out.dx, yy, z - out.dz);
        }
        if (y == 1 && ground(x + out.dx, z + out.dz)) v.set(x + out.dx, 0, z + out.dz, B.id(p.path()));
    }

    /** Durchgang (2 hoch) in einer Turm- oder Mauerwand. */
    private void doorway(int x, int y, int z) {
        v.set(x, y, z, B.AIR);
        v.set(x, y + 1, z, B.AIR);
        s.reserve(x, y, z);
        s.reserve(x, y + 1, z);
    }

    // ------------------------------------------------------------------ Ringmauer

    private void setW(boolean alongX, int t, int q, int y, String st) {
        if (alongX) v.set(t, y, q, st); else v.set(q, y, t, st);
    }

    private boolean emptyW(boolean alongX, int t, int q, int y) {
        return alongX ? v.isEmpty(t, y, q) : v.isEmpty(q, y, t);
    }

    /**
     * Gerade Mauer von t0 bis t1 (Längsachse), äußere Flucht qOut, Außenrichtung out, Dicke tw, Wehrgang auf wh.
     * Außen: Sockelschräge, Strebepfeiler, Kragsteine und vorgekragte Brustwehr mit Zinnen.
     */
    private void curtain(int t0, int t1, int qOut, Dir out, int tw, int wh, boolean buttress, boolean torches) {
        boolean alongX = out == Dir.NORTH || out == Dir.SOUTH;
        int os = alongX ? out.dz : out.dx;
        Dir in = out.opposite();
        int qo = qOut + os; // Zelle außen vor der Mauer
        for (int t = t0 - 1; t <= t1 + 1; t++) {
            boolean body = t >= t0 && t <= t1;
            if (body) {
                for (int k = -1; k < tw; k++) setW(alongX, t, qOut - k * os, 0, base());
                for (int y = 1; y < wh; y++)
                    for (int k = 0; k < tw; k++) setW(alongX, t, qOut - k * os, y, k == 0 && y == 1 ? base() : wallAt(y));
                for (int k = 0; k < tw; k++) setW(alongX, t, qOut - k * os, wh, s.vary(wallB));
                setW(alongX, t, qo, 1, stairs(baseF, in));
                if (buttress && wh >= 6 && Math.floorMod(t, 7) == 3) {
                    for (int y = 1; y <= wh - 3; y++) setW(alongX, t, qo, y, y == 1 ? base() : wallAt(y));
                    setW(alongX, t, qo, wh - 2, stairs(wallF, in));
                }
                if (merlon(t)) setW(alongX, t, qo, wh - 1, corbel(wallF, in));
                int qi = qOut - tw * os, qr = qi + os;
                int m6 = Math.floorMod(t, 6);
                if (buttress && wh >= 6 && tw >= 3 && m6 >= 2 && m6 <= 4) {
                    // Nischen/Kasematten auf der Hofseite
                    Dir lo = alongX ? Dir.WEST : Dir.NORTH;
                    setW(alongX, t, qr, 1, B.AIR);
                    setW(alongX, t, qr, 2, B.AIR);
                    setW(alongX, t, qr, 3, m6 == 2 ? corbel(wallF, lo) : m6 == 4 ? corbel(wallF, lo.opposite()) : B.AIR);
                    setW(alongX, t, qr, 4, m6 == 3 ? B.id(accentB) : wallAt(4));
                    if (m6 == 3 && rng.chance(0.5)) setW(alongX, t, qr, 1, rng.chance(0.5) ? B.with("barrel", "facing", "up") : B.axis("hay_block", 'y'));
                }
                boolean solidBehind = alongX ? v.isSolid(t, 3, qr) : v.isSolid(qr, 3, t);
                if (torches && Math.floorMod(t, 8) == 4 && solidBehind && emptyW(alongX, t, qi, 3)) setW(alongX, t, qi, 3, B.wallTorch(in));
            }
            setW(alongX, t, qo, wh, wallAt(wh));
            setW(alongX, t, qo, wh + 1, wallAt(wh + 1));
            if (merlon(t)) {
                setW(alongX, t, qo, wh + 2, wallAt(wh + 2));
                if (body && Math.floorMod(t, 12) == 6 && wh + 3 < H) setW(alongX, t, qo, wh + 3, B.lantern(light, false));
            }
        }
    }

    // ------------------------------------------------------------------ Große Burg

    private void fullCastle() {
        int R = Math.min(W, D) >= 41 ? 4 : 3;
        int c = R + 1;
        int tw = 3;
        int wh = Math.min(clamp(4 + Math.min(W, D) / 8, 6, 9), H - 8);
        int gw = W % 2 == 1 ? 3 : 4;
        int gx0 = (W - gw) / 2, gx1 = gx0 + gw - 1;
        int gTop = wh + 4;

        // Ecktürme: Höhe und Dachform passend zur Bauplatzhöhe
        double coneStep = round ? 0.55 : 0.5;
        int ty = wh + 4;
        int[] tFloors = {wh};
        int cap = roofKind == Palette.Roof.FLAT ? 0 : 1;
        if (cap == 1 && ty + 1 + spireLayers(round, R + 1, coneStep) + 3 > H - 1) {
            coneStep = 0.8;
            if (ty + 1 + spireLayers(round, R + 1, coneStep) + 1 > H - 1) cap = 0;
        }
        int need = cap == 1 ? 1 + spireLayers(round, R + 1, coneStep) + 3 : 3;
        if (ty + 5 + need <= H - 1) {
            ty = wh + 9;
            tFloors = new int[] {wh, wh + 5};
        }

        // Palas
        int cxL = c + 2, cxR = W - c - 3, czF = c + 2, czB = D - c - 3;
        int iwC = cxR - cxL + 1, idC = czB - czF + 1;
        int kw = Math.min((int) Math.round(iwC * 0.62), W - 2 * c - 2 * R - 3);
        kw = Math.max(7, Math.min(27, kw));
        if ((W - kw) % 2 != 0) kw--;
        int kd = clamp((int) Math.round(idC * 0.5), 7, 21);
        kd = Math.max(7, Math.min(kd, idC - 7));
        kx0 = (W - kw) / 2;
        kx1 = kx0 + kw - 1;
        kz1 = czB;
        kz0 = kz1 - kd + 1;
        hh = kw >= 13 ? 7 : 6;
        fh = 5;
        nUp = clamp(spec.floors() - 1, 1, 4);
        keepRoofed = roofKind != Palette.Roof.FLAT;
        fitKeep(wh + 3);

        // Mauern
        curtain(c, W - 1 - c, c - 1, Dir.NORTH, tw, wh, true, true);
        curtain(c, W - 1 - c, D - c, Dir.SOUTH, tw, wh, true, true);
        curtain(c, D - 1 - c, c - 1, Dir.WEST, tw, wh, true, true);
        curtain(c, D - 1 - c, W - c, Dir.EAST, tw, wh, true, true);

        // Zwischentürme an langen Mauern (eckig, Zinnen)
        midTowers(c, wh, gx0, gx1);

        // Ecktürme
        int[][] corners = {{c, c}, {W - 1 - c, c}, {c, D - 1 - c}, {W - 1 - c, D - 1 - c}};
        for (int i = 0; i < 4; i++) {
            int cx = corners[i][0], cz = corners[i][1];
            boolean front = i < 2;
            Dir ladderSide = front ? Dir.NORTH : Dir.SOUTH;
            tower(cx, cz, R, round, 1, ty, tFloors, cap, coneStep, true, null, false, ladderSide);
            // Durchgänge zum Wehrgang
            Dir alongX = i % 2 == 0 ? Dir.EAST : Dir.WEST;
            Dir alongZ = front ? Dir.SOUTH : Dir.NORTH;
            doorway(cx + alongX.dx * R, wh + 1, cz);
            doorway(cx, wh + 1, cz + alongZ.dz * R);
            // Tür zum Hof
            if (!towerDoor(cx, cz, R, round, alongZ, alongX)) towerDoor(cx, cz, R, round, alongX, alongZ);
            towerBand(cx, cz, R, round, wh);
            for (int f : new int[] {0, tFloors[0], tFloors[tFloors.length - 1]}) towerStuff(cx, cz, R, round, f + 1);
            // Banner an der Außenseite
            Dir outZ = alongZ.opposite();
            int bx = cx, bz = cz + outZ.dz * (R + 1), by = Math.min(wh + 1, ty - 3);
            if (v.isSolid(bx, by, bz - outZ.dz) && v.isEmpty(bx, by, bz) && v.isEmpty(bx, by - 1, bz)) {
                v.set(bx, by, bz, B.with(bannerColor + "_wall_banner", "facing", outZ.id()));
            }
        }

        gatehouse(c, wh, gx0, gx1, gTop);
        keep(true);
        courtyard(c, R, wh, gx0, gx1);
        wallStairs(c, wh, gx0 - 2, gx1 + 2);
        outside(gx0, gx1, c - 2);
    }

    /** Palas-Höhe an den Bauplatz anpassen (Geschosse, Dach, Saalhöhe). */
    private void fitKeep(int minDeck) {
        for (int guard = 0; guard < 40; guard++) {
            yDeck = hh + nUp * fh;
            int span = Math.min(kx1 - kx0 + 1, kz1 - kz0 + 1) - 2;
            int roofLayers = (span + 1) / 2 + 1;
            int needTop = keepRoofed ? yDeck + 1 + roofLayers + 3 : yDeck + 2;
            if (needTop <= H - 1) return;
            if (nUp > 1) nUp--;
            else if (keepRoofed) keepRoofed = false;
            else if (hh > 5) hh--;
            else if (fh > 4) fh--;
            else if (nUp > 0) nUp--;
            else if (hh > 3) hh--;
            else return;
        }
    }

    /** Eckige Zwischentürme auf langen Mauerabschnitten. */
    private void midTowers(int c, int wh, int gx0, int gx1) {
        int top = wh + 4;
        int cap = roofKind == Palette.Roof.FLAT || top + 1 + spireLayers(false, 3, 0.5) + 1 > H - 1 ? 0 : 1;
        if (round) cap = 0;
        int len = D - 2 * c;
        if (len > 28) {
            int n = len / 18;
            for (int i = 1; i <= n; i++) {
                int z = c + (int) Math.round(i * (D - 1 - 2 * c) / (double) (n + 1));
                for (int side = 0; side < 2; side++) {
                    int x = side == 0 ? c : W - 1 - c;
                    Dir inward = side == 0 ? Dir.EAST : Dir.WEST;
                    tower(x, z, 2, false, 1, top, new int[] {wh}, cap, 0.5, false, null, false, inward.opposite());
                    doorway(x, wh + 1, z - 2);
                    doorway(x, wh + 1, z + 2);
                    towerDoor(x, z, 2, false, inward, Dir.SOUTH);
                }
            }
        }
        // Vorderseite zwischen Eckturm und Torhaus
        int a = c + (c - 1) + 2, b = gx0 - 7;
        if (b - a > 10) {
            int[] xs = {(a + b) / 2, W - 1 - (a + b) / 2};
            for (int x : xs) {
                tower(x, c, 2, false, 1, top, new int[] {wh}, cap, 0.5, false, null, false, Dir.NORTH);
                doorway(x - 2, wh + 1, c);
                doorway(x + 2, wh + 1, c);
                towerDoor(x, c, 2, false, Dir.SOUTH, Dir.EAST);
            }
        }
    }

    // ------------------------------------------------------------------ Torhaus

    private void gatehouse(int c, int wh, int gx0, int gx1, int gTop) {
        int hx0 = gx0 - 2, hx1 = gx1 + 2, hz0 = c - 2, hz1 = c + 2;
        int gh = Math.max(2, Math.min(4, wh - 2));
        for (int x = hx0; x <= hx1; x++)
            for (int z = hz0; z <= hz1; z++) {
                v.set(x, 0, z, base());
                for (int y = 1; y < gTop; y++) v.set(x, y, z, y == 1 && z == hz0 ? base() : wallAt(y));
                v.set(x, gTop, z, s.vary(wallB));
            }
        // Wachstube über dem Tor
        for (int x = gx0; x <= gx1; x++)
            for (int z = hz0 + 1; z <= hz1 - 1; z++) {
                v.set(x, wh, z, B.id(woodF.block()));
                for (int y = wh + 1; y < gTop; y++) v.set(x, y, z, B.AIR);
            }
        // Durchfahrt mit Spitzbogen
        for (int x = gx0; x <= gx1; x++)
            for (int z = hz0; z <= hz1; z++) {
                for (int y = 1; y <= gh; y++) v.set(x, y, z, B.AIR);
                v.set(x, 0, z, (x + z) % 3 == 0 ? B.id(hallA) : s.vary(baseB));
                reserveArea(x, z, x, z, 1, 2);
            }
        for (int z : new int[] {hz0, hz1}) {
            v.set(gx0, gh, z, corbel(wallF, Dir.WEST));
            v.set(gx1, gh, z, corbel(wallF, Dir.EAST));
            for (int x = gx0 + 1; x <= gx1 - 1; x++) v.set(x, gh + 1, z, B.id(accentB));
        }
        // Fallgitter (halb hochgezogen)
        for (int x = gx0; x <= gx1; x++)
            for (int y = gh >= 4 ? gh - 1 : gh; y <= gh; y++) v.set(x, y, hz0 + 1, B.id("iron_bars"));
        s.hangingLight((gx0 + gx1) / 2, gh, hz1 - 1);
        // Wachstube: Licht, Fenster zum Hof, Ausstattung
        s.hangingLight(gx0, gTop - 1, c);
        v.set(gx1, wh + 1, hz1 - 1, B.with("barrel", "facing", "up"));
        v.set(gx0, wh + 1, hz1 - 1, B.with("grindstone", "face", "floor", "facing", "east"));
        for (int x = gx0 + 1; x <= gx1 - 1; x++) v.set(x, wh + 2, hz1, B.id(glass));
        for (int x : new int[] {gx0, gx1}) v.set(x, wh + 2, hz0, B.AIR);
        // vorgekragte Brustwehr vorne, sonst auf der Mauerflucht
        for (int x = hx0 - 1; x <= hx1 + 1; x++) {
            if (merlon(x)) put(x, gTop - 1, hz0 - 1, corbel(wallF, Dir.SOUTH));
            v.set(x, gTop, hz0 - 1, wallAt(gTop));
            v.set(x, gTop + 1, hz0 - 1, wallAt(gTop + 1));
            if (merlon(x)) v.set(x, gTop + 2, hz0 - 1, wallAt(gTop + 2));
        }
        for (int z = hz0; z <= hz1; z++)
            for (int x : new int[] {hx0, hx1}) {
                v.set(x, gTop + 1, z, wallAt(gTop + 1));
                if (merlon(z)) v.set(x, gTop + 2, z, wallAt(gTop + 2));
            }
        for (int x = hx0; x <= hx1; x++) {
            v.set(x, gTop + 1, hz1, wallAt(gTop + 1));
            if (merlon(x)) v.set(x, gTop + 2, hz1, wallAt(gTop + 2));
        }
        // Flankentürme
        int flankTop = Math.min(wh + 8, H - 3);
        int fcap = 0;
        double fstep = round ? 0.6 : 0.5;
        if (roofKind != Palette.Roof.FLAT && flankTop + 1 + spireLayers(round, 3, fstep) + 1 <= H - 1 && rng.chance(0.5)) fcap = 1;
        int[] fFloors = flankTop >= gTop + 4 ? new int[] {gTop} : new int[0];
        for (int side = 0; side < 2; side++) {
            int fx = side == 0 ? gx0 - 3 : gx1 + 3;
            int fz = c - 1;
            Dir toGate = side == 0 ? Dir.EAST : Dir.WEST;
            tower(fx, fz, 2, round, wh + 1, flankTop, fFloors, fcap, fstep, false, null, false, Dir.NORTH);
            doorway(fx - toGate.dx * 2, wh + 1, fz);
            doorway(fx + toGate.dx * 2, wh + 1, fz);
            if (fFloors.length > 0) doorway(fx + toGate.dx * 2, gTop + 1, fz);
            // Banner und Fackeln
            int by = Math.min(wh + 2, flankTop - 3);
            if (v.isSolid(fx, by, fz - 2) && v.isEmpty(fx, by, fz - 3) && v.isEmpty(fx, by - 1, fz - 3)) {
                v.set(fx, by, fz - 3, B.with(bannerColor + "_wall_banner", "facing", "north"));
            }
            int tx = side == 0 ? gx0 - 1 : gx1 + 1;
            if (v.isSolid(tx, 3, hz0) && v.isEmpty(tx, 3, hz0 - 1)) v.set(tx, 3, hz0 - 1, B.wallTorch(Dir.NORTH));
        }
        // großes Banner über dem Tor
        for (int x = gx0 + 1; x <= gx1 - 1; x++) {
            int by = gh + 3;
            if (by < gTop - 1 && v.isSolid(x, by, hz0) && v.isEmpty(x, by, hz0 - 1)) {
                v.set(x, by, hz0 - 1, B.with(bannerColor + "_wall_banner", "facing", "north"));
            }
        }
    }

    // ------------------------------------------------------------------ Palas (Bauwerk)

    private void keep(boolean bartizans) {
        yDeck = hh + nUp * fh;
        int ix0 = kx0 + 1, ix1 = kx1 - 1, iz0 = kz0 + 1, iz1 = kz1 - 1;
        int kw = kx1 - kx0 + 1, kd = kz1 - kz0 + 1;
        // Brustwehr vorkragen? (nur wenn rundherum Platz ist)
        keepOverhang = true;
        for (int x = kx0 - 1; x <= kx1 + 1; x++)
            for (int z = kz0 - 1; z <= kz1 + 1; z++) {
                boolean ring = x == kx0 - 1 || x == kx1 + 1 || z == kz0 - 1 || z == kz1 + 1;
                if (!ring) continue;
                for (int y = yDeck - 1; y <= yDeck + 2; y++) if (!v.isEmpty(x, y, z)) keepOverhang = false;
            }
        for (int x = kx0; x <= kx1; x++)
            for (int z = kz0; z <= kz1; z++) {
                boolean ring = x == kx0 || x == kx1 || z == kz0 || z == kz1;
                v.set(x, 0, z, ring ? base() : B.id(((x + z) & 1) == 0 ? hallA : hallB));
                for (int y = 1; y < yDeck; y++) v.set(x, y, z, ring ? (y == 1 ? base() : wallAt(y)) : B.AIR);
                v.set(x, yDeck, z, ring ? wallAt(yDeck) : keepRoofed ? B.id(woodF.block()) : s.vary(wallB));
            }
        for (int i = 0; i < nUp; i++) {
            int y = hh + i * fh;
            for (int x = ix0; x <= ix1; x++)
                for (int z = iz0; z <= iz1; z++) {
                    v.set(x, y, z, Math.floorMod(z - kz0, 3) == 0 ? B.axis(logB, 'x') : B.id(woodF.block()));
                }
        }
        // Portal
        keepDoorW = kw % 2 == 0 ? 2 : 1;
        keepDoorX = kx0 + (kw - keepDoorW) / 2;
        if (keepDoorX + keepDoorW - 1 > doorMaxX && doorMaxX - keepDoorW + 1 >= kx0 + 1) keepDoorX = doorMaxX - keepDoorW + 1;
        for (int i = 0; i < keepDoorW; i++) {
            door(keepDoorX + i, 1, kz0, Dir.NORTH, i == 1);
            v.set(keepDoorX + i, 3, kz0, B.id(accentB));
        }
        // Fenster
        int[] px = posts(kw, kw >= 15 ? 5 : 4), pz = posts(kd, kd >= 15 ? 5 : 4);
        for (Dir out : Dir.values()) {
            boolean alongX = out == Dir.NORTH || out == Dir.SOUTH;
            int[] ps = alongX ? px : pz;
            for (int fl = 0; fl <= nUp; fl++) {
                int yb = fl == 0 ? 0 : hh + (fl - 1) * fh;
                int next = fl == 0 ? hh : yb + fh;
                int y0 = yb + 2, y1 = fl == 0 ? hh - 2 : yb + 3;
                if (y1 >= next) y1 = next - 1;
                if (y1 < y0) continue;
                for (int i = 0; i + 1 < ps.length; i++) {
                    int a = ps[i], b = ps[i + 1], len = b - a - 1;
                    if (len < 1) continue;
                    int mid = a + 1 + (len - 1) / 2;
                    int[] cols = len >= 5 ? new int[] {mid - 1, mid + 1} : len == 4 ? new int[] {a + 2, a + 3} : new int[] {mid};
                    for (int t : cols) {
                        int x = alongX ? kx0 + t : (out == Dir.WEST ? kx0 : kx1);
                        int z = alongX ? (out == Dir.NORTH ? kz0 : kz1) : kz0 + t;
                        if (out == Dir.NORTH && fl == 0 && x >= keepDoorX - 2 && x <= keepDoorX + keepDoorW + 1) continue;
                        window(x, z, out, y0, y1, y1 + 1 < yDeck - 1 || !keepOverhang);
                    }
                }
            }
        }
        // Lisenen/Eckstreben, Gurtgesimse, Sockel
        int pilTop = yDeck - 3;
        for (Dir out : Dir.values()) {
            boolean alongX = out == Dir.NORTH || out == Dir.SOUTH;
            int[] ps = alongX ? px : pz;
            for (int t : ps) {
                int x = (alongX ? kx0 + t : (out == Dir.WEST ? kx0 : kx1)) + out.dx;
                int z = (alongX ? (out == Dir.NORTH ? kz0 : kz1) : kz0 + t) + out.dz;
                if (out == Dir.NORTH && x >= keepDoorX - 1 && x <= keepDoorX + keepDoorW) continue;
                if (!v.isEmpty(x, 2, z)) continue;
                for (int y = 1; y <= pilTop; y++) put(x, y, z, y == 1 ? base() : wallAt(y));
                put(x, pilTop + 1, z, stairs(wallF, out.opposite()));
            }
            for (int t = 0; t < (alongX ? kw : kd); t++) {
                int x = (alongX ? kx0 + t : (out == Dir.WEST ? kx0 : kx1)) + out.dx;
                int z = (alongX ? (out == Dir.NORTH ? kz0 : kz1) : kz0 + t) + out.dz;
                for (int i = 0; i < nUp; i++) put(x, hh + i * fh, z, stairs(wallF, out.opposite()));
                if (!s.isReserved(x, 1, z)) {
                    put(x, 1, z, stairs(baseF, out.opposite()));
                    if (v.in(x, 0, z) && ground(x, z)) v.set(x, 0, z, base());
                }
            }
        }
        portal();
        // Brustwehr mit Zinnen
        for (int x = kx0 - 1; x <= kx1 + 1; x++)
            for (int z = kz0 - 1; z <= kz1 + 1; z++) {
                boolean outerRing = x == kx0 - 1 || x == kx1 + 1 || z == kz0 - 1 || z == kz1 + 1;
                boolean wallRing = !outerRing && (x == kx0 || x == kx1 || z == kz0 || z == kz1);
                if (keepOverhang && outerRing) {
                    boolean corner = (x == kx0 - 1 || x == kx1 + 1) && (z == kz0 - 1 || z == kz1 + 1);
                    Dir toWall = x == kx0 - 1 ? Dir.EAST : x == kx1 + 1 ? Dir.WEST : z == kz0 - 1 ? Dir.SOUTH : Dir.NORTH;
                    if (!corner && merlon(x + z)) put(x, yDeck - 1, z, corbel(wallF, toWall));
                    v.set(x, yDeck, z, wallAt(yDeck));
                    v.set(x, yDeck + 1, z, wallAt(yDeck + 1));
                    if (merlon(x + z)) v.set(x, yDeck + 2, z, wallAt(yDeck + 2));
                } else if (!keepOverhang && wallRing) {
                    v.set(x, yDeck + 1, z, wallAt(yDeck + 1));
                    if (merlon(x + z)) v.set(x, yDeck + 2, z, wallAt(yDeck + 2));
                }
            }
        // Dach
        int inset = keepOverhang ? 1 : 2;
        int rx0 = kx0 + inset, rx1 = kx1 - inset, rz0 = kz0 + inset, rz1 = kz1 - inset;
        if (keepRoofed && rx0 <= rx1 && rz0 <= rz1) {
            int top = roofKind == Palette.Roof.GABLE ? gableRoof(rx0, rz0, rx1, rz1, yDeck + 1) : hipRoof(rx0, rz0, rx1, rz1, yDeck + 1);
            int fxc = (rx0 + rx1) / 2, fzc = (rz0 + rz1) / 2;
            if (top + 1 < H && v.isSolid(fxc, top, fzc) || top + 1 < H && B.name(String.valueOf(v.get(fxc, top, fzc))).endsWith("_slab")) {
                finial(fxc, top + 1, fzc, true);
            }
        } else if (yDeck + 4 < H) {
            finial((kx0 + kx1) / 2, yDeck + 1, (kz0 + kz1) / 2 + 1, true);
        }
        if (bartizans && keepOverhang && yDeck + 9 <= H - 1) bartizans();
        keepInterior();
    }

    /** Fenster mit Sims (Kragstein) und Verdachung; nur wenn außen frei ist. */
    private void window(int x, int z, Dir out, int y0, int y1, boolean hood) {
        int ox = x + out.dx, oz = z + out.dz;
        for (int y = y0; y <= y1; y++) if (!v.isEmpty(ox, y, oz) || !v.isSolid(x, y, z)) return;
        for (int y = y0; y <= y1; y++) v.set(x, y, z, B.id(glass));
        put(ox, y0 - 1, oz, corbel(wallF, out.opposite()));
        if (hood) put(ox, y1 + 1, oz, stairs(wallF, out.opposite()));
    }

    /** Vorgesetztes Portal mit Säulen, Bogen, Vordach, Laternen und Bannern. */
    private void portal() {
        int z = kz0 - 1, pl = keepDoorX - 1, pr = keepDoorX + keepDoorW;
        for (int x : new int[] {pl, pr}) {
            for (int y = 1; y <= 3; y++) v.set(x, y, z, y == 1 ? base() : B.axis(pillarB, 'y'));
            v.set(x, 4, z, B.id(accentB));
        }
        for (int x = keepDoorX; x < keepDoorX + keepDoorW; x++) {
            v.set(x, 3, z, keepDoorW == 1 ? B.slab(wallF, true) : corbel(wallF, x == keepDoorX ? Dir.WEST : Dir.EAST));
            v.set(x, 4, z, s.vary(wallB));
            v.set(x, 0, z, B.id(hallA));
        }
        for (int x = pl - 1; x <= pr + 1; x++) {
            String at = v.get(x, 5, z);
            if (B.isAir(at) || at.contains("_stairs")) v.set(x, 5, z, stairs(roofF, Dir.SOUTH));
        }
        for (int x : new int[] {pl - 1, pr + 1}) {
            if (v.isSolid(x, 3, kz0) && v.isEmpty(x, 3, z)) v.set(x, 3, z, B.wallTorch(Dir.NORTH));
        }
        for (int x : new int[] {pl - 2, pr + 2}) {
            int y = Math.min(hh, 6);
            if (v.isSolid(x, y, kz0) && v.isEmpty(x, y, z) && v.isEmpty(x, y - 1, z)) {
                v.set(x, y, z, B.with(bannerColor + "_wall_banner", "facing", "north"));
            }
        }
        if (5 < hh - 1 && v.isSolid((pl + pr) / 2, hh - 1, kz0) && v.isEmpty((pl + pr) / 2, hh - 1, z)
            && v.isEmpty((pl + pr) / 2, hh - 2, z)) {
            v.set((pl + pr) / 2, hh - 1, z, B.with(bannerColor + "_wall_banner", "facing", "north"));
        }
    }

    /** Ecktürmchen (Pfefferstreuer) auf Kragsteinen an den Palas-Ecken. */
    private void bartizans() {
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) {
                int cornerX = sx < 0 ? kx0 : kx1, cornerZ = sz < 0 ? kz0 : kz1;
                int bx = cornerX + sx, bz = cornerZ + sz;
                boolean ok = true;
                for (int dx = -1; dx <= 1 && ok; dx++)
                    for (int dz = -1; dz <= 1 && ok; dz++) {
                        int x = bx + dx, z = bz + dz;
                        boolean outer = x < kx0 - 1 || x > kx1 + 1 || z < kz0 - 1 || z > kz1 + 1;
                        if (!outer) continue;
                        for (int y = yDeck - 2; y <= yDeck + 4; y++) if (!v.isEmpty(x, y, z)) ok = false;
                    }
                if (!ok) continue;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = bx + dx, z = bz + dz;
                        boolean outer = x < kx0 - 1 || x > kx1 + 1 || z < kz0 - 1 || z > kz1 + 1;
                        if (outer) put(x, yDeck - 2, z, corbel(wallF, toCenter(x - cornerX, z - cornerZ)));
                        for (int y = yDeck - 1; y <= yDeck + 3; y++) v.set(x, y, z, wallAt(y));
                    }
                for (int y = yDeck + 1; y <= yDeck + 2; y++) {
                    v.set(bx, y, bz, B.AIR);
                    v.set(cornerX, y, cornerZ, B.AIR);
                    v.set(bx, y, cornerZ, B.AIR);
                    v.set(bx + sx, y, bz, B.AIR);
                    v.set(bx, y, bz + sz, B.AIR);
                }
                spire(bx, bz, round, 2, yDeck + 4, 0.5, false);
            }
    }

    private int hipRoof(int x0, int z0, int x1, int z1, int y0) {
        for (int i = 0; ; i++) {
            int ax = x0 + i, bx = x1 - i, az = z0 + i, bz = z1 - i, y = y0 + i;
            if (ax > bx || az > bz || y >= H) return y - 1;
            if (ax == bx || az == bz) {
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        v.set(x, y, z, B.id(roofF.block()));
                        if (y + 1 < H) v.set(x, y + 1, z, B.slab(roofF, false));
                    }
                return Math.min(H - 1, y + 1);
            }
            for (int x = ax; x <= bx; x++) {
                v.set(x, y, az, stairs(roofF, Dir.SOUTH));
                v.set(x, y, bz, stairs(roofF, Dir.NORTH));
            }
            for (int z = az + 1; z < bz; z++) {
                v.set(ax, y, z, stairs(roofF, Dir.EAST));
                v.set(bx, y, z, stairs(roofF, Dir.WEST));
            }
        }
    }

    private int gableRoof(int x0, int z0, int x1, int z1, int y0) {
        boolean ridgeX = (x1 - x0) >= (z1 - z0);
        int lo = ridgeX ? z0 : x0, hi = ridgeX ? z1 : x1, a0 = ridgeX ? x0 : z0, a1 = ridgeX ? x1 : z1;
        Dir upA = ridgeX ? Dir.SOUTH : Dir.EAST, upB = upA.opposite();
        int top = y0;
        for (int i = 0; ; i++) {
            int ca = lo + i, cb = hi - i, y = y0 + i;
            if (ca > cb || y >= H) break;
            for (int t = a0; t <= a1; t++) {
                if (ca == cb) {
                    setR(ridgeX, t, y, ca, B.id(roofF.block()));
                    if (y + 1 < H) { setR(ridgeX, t, y + 1, ca, B.slab(roofF, false)); top = y + 1; }
                } else {
                    setR(ridgeX, t, y, ca, stairs(roofF, upA));
                    setR(ridgeX, t, y, cb, stairs(roofF, upB));
                    top = y;
                }
            }
            for (int c = ca + 1; c <= cb - 1; c++) {
                setR(ridgeX, a0, y, c, wallAt(y));
                setR(ridgeX, a1, y, c, wallAt(y));
            }
        }
        return top;
    }

    private void setR(boolean ridgeX, int t, int y, int c, String st) {
        if (ridgeX) v.set(t, y, c, st); else v.set(c, y, t, st);
    }

    // ------------------------------------------------------------------ Palas (innen)

    private void keepInterior() {
        int ix0 = kx0 + 1, ix1 = kx1 - 1, iz0 = kz0 + 1, iz1 = kz1 - 1;
        int iw = ix1 - ix0 + 1, id = iz1 - iz0 + 1;
        if (iw < 1 || id < 1) return;
        keepStairs = nUp > 0 && !keepViaTower && id >= Math.max(hh, fh) + 2 && iw >= 7;
        if (keepStairs) flights();
        else if (nUp > 0 && !keepViaTower) keepLadder();
        if (!keepRoofed && !keepViaTower && yDeck < H - 2) {
            int from = nUp > 0 ? hh + (nUp - 1) * fh + 1 : 1;
            for (int y = from; y <= yDeck; y++) {
                v.set(ix1, y, iz0, B.ladder(Dir.WEST));
                if (y < yDeck) v.set(kx1, y, iz0, wallAt(y));
                s.reserve(ix1, y, iz0);
                s.reserve(ix1 - 1, y, iz0);
            }
        }
        greatHall(ix0, iz0, ix1, iz1);
        // Obergeschosse
        List<String> wanted = new ArrayList<>(spec.rooms());
        List<String> defaults = List.of("bedroom", "library", "bedroom", "bedroom", "office", "storage", "chapel", "bedroom");
        int next = 0;
        for (int f = 0; f < nUp; f++) {
            int yb = hh + f * fh, y = yb + 1, ceil = yb + fh;
            int rx0 = keepStairs ? ix0 + 2 : ix0;
            if (rx0 > ix1 || ceil - y < 2) continue;
            int rw = ix1 - rx0 + 1;
            List<int[]> rooms = new ArrayList<>();
            if (rw >= 9 && id >= 5) {
                int pxw = rx0 + rw / 2;
                int dz = (iz0 + iz1) / 2;
                for (int yy = y; yy < ceil; yy++)
                    for (int z = iz0; z <= iz1; z++) if (!s.isReserved(pxw, yy, z)) v.set(pxw, yy, z, s.vary(wallB));
                v.set(pxw, y, dz, B.AIR);
                v.set(pxw, y + 1, dz, B.AIR);
                reserveArea(pxw - 1, dz, pxw + 1, dz, y, y + 1);
                rooms.add(new int[] {rx0, pxw - 1});
                rooms.add(new int[] {pxw + 1, ix1});
            } else {
                rooms.add(new int[] {rx0, ix1});
            }
            for (int[] r : rooms) {
                String kind = next < wanted.size() ? wanted.get(next) : defaults.get(next % defaults.size());
                next++;
                if (r[1] - r[0] >= 1) s.furnish(new Site.Room(r[0], iz0, r[1], iz1, y, ceil, kind));
            }
        }
    }

    /** Gerade Treppenläufe an der Westwand, alle Richtung Süden, Podest an der Rückwand. */
    private void flights() {
        int X = kx0 + 1, iz1 = kz1 - 1;
        int yb = 0;
        for (int k = 0; k < nUp; k++) {
            int hk = k == 0 ? hh : fh;
            int zs = iz1 - hk;
            Materials.Family f = k == 0 ? wallF : woodF;
            for (int i = 0; i < hk; i++) {
                int z = zs + i, y = yb + 1 + i;
                v.set(X, y, z, stairs(f, Dir.SOUTH));
                if (k == 0) for (int yy = 1; yy < y; yy++) v.set(X, yy, z, s.vary(wallB));
                for (int hy = y; hy <= y + 3; hy++) s.reserve(X, hy, z);
            }
            int top = yb + hk;
            for (int i = Math.max(0, hk - 4); i <= hk - 2; i++) v.set(X, top, zs + i, B.AIR);
            for (int z = zs - 1; z <= iz1; z++) {
                s.reserve(X + 1, yb + 1, z);
                s.reserve(X + 1, yb + 2, z);
            }
            reserveArea(X, zs - 1, X, zs - 1, yb + 1, yb + 2);
            reserveArea(X, iz1, X + 1, iz1, top + 1, top + 2);
            yb = top;
        }
    }

    private void keepLadder() {
        int x = kx0 + 1, z = kz1 - 1;
        int topY = hh + (nUp - 1) * fh;
        for (int y = 1; y <= topY; y++) {
            v.set(x, y, z, B.ladder(Dir.EAST));
            v.set(kx0, y, z, y == 1 ? base() : wallAt(y));
            s.reserve(x, y, z);
            s.reserve(x + 1, y, z);
        }
    }

    /** Rittersaal: Thronpodest, Läufer, Säulen, lange Tafeln, Kamin, Banner, Leuchter. */
    private void greatHall(int ix0, int iz0, int ix1, int iz1) {
        int iw = ix1 - ix0 + 1, id = iz1 - iz0 + 1;
        int cxa = (kx0 + kx1) / 2, cxb = (kx0 + kx1 + 1) / 2;
        boolean dais = id >= 5;
        if (dais) {
            for (int x = cxa - 2; x <= cxb + 2; x++) {
                if (x < ix0 || x > ix1) continue;
                if (s.free(x, 1, iz1)) v.set(x, 1, iz1, B.id(hallA));
                if (s.free(x, 1, iz1 - 1)) v.set(x, 1, iz1 - 1, B.slab(wallF, false));
            }
            for (int x = cxa; x <= cxb; x++) {
                if (v.isSolid(x, 1, iz1) && s.free(x, 2, iz1)) {
                    v.set(x, 2, iz1, stairs(throneF, Dir.SOUTH));
                    s.reserve(x, 2, iz1);
                }
            }
            for (int x : new int[] {cxa - 2, cxb + 2}) {
                if (x >= ix0 && x <= ix1 && v.isSolid(x, 1, iz1) && s.free(x, 2, iz1)) v.set(x, 2, iz1, B.lantern(light, false));
            }
            int by = Math.min(hh - 1, 5);
            for (int x : new int[] {cxa - 1, cxb + 1}) wallBanner(x, by, iz1, Dir.NORTH);
            if (hh >= 7) for (int x = cxa; x <= cxb; x++) wallBanner(x, hh - 1, iz1, Dir.NORTH);
        }
        // Läufer vom Portal zum Thron
        for (int z = iz0; z <= iz1 - (dais ? 2 : 0); z++)
            for (int x = cxa; x <= cxb; x++) {
                if (s.free(x, 1, z) && v.isSolid(x, 0, z)) v.set(x, 1, z, B.id(bannerColor + "_carpet"));
                s.reserve(x, 1, z);
                s.reserve(x, 2, z);
            }
        // Säulenreihen
        if (iw >= 9 && id >= 6) {
            int po = iw >= 14 ? 6 : 3;
            for (int x : new int[] {cxa - po, cxb + po}) {
                if (x <= ix0 || x >= ix1) continue;
                for (int z = iz0 + 2; z <= iz1 - 3; z += 3) {
                    boolean clear = true;
                    for (int y = 1; y < hh; y++) if (!s.free(x, y, z)) clear = false;
                    if (!clear) continue;
                    for (int y = 1; y < hh; y++) {
                        v.set(x, y, z, y == 1 ? s.vary(wallB) : y == hh - 1 ? B.id(accentB) : B.axis(pillarB, 'y'));
                    }
                }
            }
        }
        // Tafeln mit Tischtuch und Stühlen
        for (int side = 0; side < 2; side++) {
            int to = iw >= 14 ? 3 : 2;
            int tx = side == 0 ? cxa - to : cxb + to;
            if (tx - 1 < ix0 + (keepStairs ? 2 : 0) || tx + 1 > ix1) continue;
            for (int z = iz0 + 2; z <= iz1 - 3; z++) {
                if (!s.free(tx, 1, z) || !s.free(tx, 2, z)) continue;
                v.set(tx, 1, z, B.id(fenceB));
                v.set(tx, 2, z, B.id("white_carpet"));
                if (Math.floorMod(z - iz0, 2) == 0) {
                    chair(tx - 1, z, Dir.WEST);
                    chair(tx + 1, z, Dir.EAST);
                }
            }
        }
        // Kamin an der Ost- oder Westwand (dort, wo die Wand geschlossen ist)
        if (id >= 5 && iw >= 5 && hh >= 5) {
            int mid = (iz0 + iz1) / 2;
            boolean done = false;
            for (int side = 0; side < 2 && !done; side++) {
                int fx = side == 0 ? ix1 : ix0, wx = side == 0 ? kx1 : kx0;
                Dir toWall = side == 0 ? Dir.EAST : Dir.WEST;
                for (int k = 0; k <= id && !done; k++) {
                    int fz = mid + ((k & 1) == 0 ? k / 2 : -(k + 1) / 2);
                    if (fz - 1 < iz0 || fz + 1 > iz1 - 2) continue;
                    boolean ok = s.free(fx - toWall.dx, 1, fz);
                    for (int dz = -1; dz <= 1 && ok; dz++) {
                        for (int y = 1; y < hh; y++) if (!s.free(fx, y, fz + dz) && (dz == 0 || y <= 3)) ok = false;
                        for (int y = 2; y <= 4; y++) if (!v.isSolid(wx, y, fz + dz)) ok = false;
                    }
                    if (!ok) continue;
                    v.set(fx, 1, fz, B.campfire());
                    for (int dz : new int[] {-1, 1}) {
                        v.set(fx, 1, fz + dz, s.vary(wallB));
                        v.set(fx, 2, fz + dz, s.vary(wallB));
                        v.set(fx, 3, fz + dz, corbel(wallF, toWall));
                    }
                    v.set(fx, 3, fz, B.id(accentB));
                    for (int y = 4; y < hh; y++) v.set(fx, y, fz, s.vary(wallB));
                    reserveArea(fx - toWall.dx, fz - 1, fx - toWall.dx, fz + 1, 1, 2);
                    chimney(fx, fz, wx);
                    done = true;
                }
            }
        }
        // Banner an den Seitenwänden, Leuchter
        for (int z = iz0 + 1; z <= iz1 - 2; z += 4) {
            wallBanner(ix0, hh - 2, z, Dir.EAST);
            wallBanner(ix1, hh - 2, z, Dir.WEST);
        }
        for (int z = iz0 + 1; z <= iz1 - 1; z += 3) {
            if (iw >= 9) {
                int to = iw >= 14 ? 3 : 2;
                s.hangingLight(cxa - to, hh - 1, z);
                s.hangingLight(cxb + to, hh - 1, z);
            } else {
                s.hangingLight(cxa, hh - 1, z);
            }
        }
    }

    /** Kaminzug vom Saal durch alle Geschosse und das Dach, oben ein rauchendes Lagerfeuer. */
    private void chimney(int x, int z, int wx) {
        if (z == kz0 + 1) return;
        int roofY = yDeck;
        for (int y = yDeck + 1; y < H; y++) if (!v.isEmpty(x, y, z)) roofY = y;
        int top = Math.max(yDeck + 2, roofY + 2);
        if (top + 1 >= H) return;
        for (int y = hh; y <= top; y++) {
            v.set(x, y, z, y >= top - 1 ? B.id(baseB) : s.vary(wallB));
            s.reserve(x, y, z);
            String back = v.get(wx, y, z);
            if (back != null && B.name(back).contains("glass")) v.set(wx, y, z, wallAt(y));
        }
        v.set(x, top + 1, z, B.campfire());
    }

    private void chair(int x, int z, Dir back) {
        if (s.free(x, 1, z) && v.isSolid(x, 0, z)) v.set(x, 1, z, stairs(woodF, back));
    }

    /** Wandbanner an (x,y,z), hängt an der Wand hinter sich (gegen {@code facing}). */
    private void wallBanner(int x, int y, int z, Dir facing) {
        if (y < 2) return;
        if (!v.isSolid(x - facing.dx, y, z - facing.dz) || !s.free(x, y, z) || !s.free(x, y - 1, z)) return;
        v.set(x, y, z, B.with(bannerColor + "_wall_banner", "facing", facing.id()));
    }

    // ------------------------------------------------------------------ Innenhof

    private String pave() {
        double r = rng.nextDouble();
        return r < 0.6 ? base() : r < 0.8 ? B.id(p.path()) : B.id(hallA);
    }

    /** Weg (Bodenschicht) – ersetzt nur natürlichen Boden. */
    private void pathRect(int x0, int z0, int x1, int z1, boolean paved) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (!ground(x, z)) continue;
                v.set(x, 0, z, paved ? pave() : B.id(rng.chance(0.75) ? "dirt_path" : "gravel"));
            }
    }

    private void courtyard(int c, int R, int wh, int gx0, int gx1) {
        int cxL = c + 2, cxR = W - c - 3, czF = c + 3, czB = kz0 - 2;
        for (int x = cxL; x <= cxR; x++)
            for (int z = c + 2; z < kz0; z++) {
                if (!B.name(String.valueOf(v.get(x, 0, z))).equals("grass_block")) continue;
                double r = rng.nextDouble();
                if (r < 0.14) v.set(x, 0, z, B.id("coarse_dirt"));
                else if (r < 0.20) v.set(x, 0, z, B.id("gravel"));
            }
        pathRect(gx0, c + 3, gx1, kz0 - 1, true);
        for (int z = czF + 1; z < kz0 - 2; z += 5) s.lanternPosts(gx0, z, gx1 - gx0 + 1);
        reserveArea(gx0, c + 3, gx1, kz0 - 1, 1, 2);
        int midZ = (czF + czB) / 2;
        // Brunnen links, Marktstand rechts vom Weg
        int[] w = findSpot(3, 3, 1, 5, (cxL + gx0) / 2, midZ, cxL, czF, gx0 - 2, czB);
        if (w == null) w = findSpot(3, 3, 1, 5, (gx1 + cxR) / 2, midZ, gx1 + 2, czF, cxR, czB);
        if (w != null) well(w[0], w[1]);
        // Stall an der linken, Schmiede an der rechten Mauer
        leanToAlongWall(cxL, Dir.WEST, czF, czB, "stable");
        leanToAlongWall(cxR, Dir.EAST, czF, czB, "smithy");
        int[] st = findSpot(2, 3, 1, 4, gx1 + 3, midZ, gx1 + 2, czF, cxR, czB);
        if (st != null) stall(st[0], st[1], Dir.WEST);
        int[] st2 = findSpot(2, 3, 1, 4, gx0 - 4, czF + 2, cxL, czF, gx0 - 2, czB);
        if (st2 != null && (W >= 33 || w == null)) stall(st2[0], st2[1], Dir.EAST);
        // Gärten vor dem Palas
        int[] g1 = findSpot(5, 4, 0, 6, kx0 + 1, kz0 - 4, cxL, czF, gx0 - 2, kz0 - 2);
        if (g1 != null) garden(g1[0], g1[1], 5, 4);
        int[] g2 = findSpot(5, 4, 0, 6, kx1 - 5, kz0 - 4, gx1 + 2, czF, cxR, kz0 - 2);
        if (g2 != null) garden(g2[0], g2[1], 5, 4);
        // Kleinkram: Heu, Fässer, Karren
        for (int i = 0; i < Math.max(2, (cxR - cxL) / 5); i++) {
            int[] sp = findSpot(1, 1, 0, 2, cxL + rng.nextInt(Math.max(1, cxR - cxL + 1)), czF + rng.nextInt(Math.max(1, czB - czF + 1)),
                cxL, czF, cxR, czB);
            if (sp == null) break;
            String item = rng.pick(List.of("hay_block", "barrel", "hay_block", "barrel", "chest"));
            v.set(sp[0], 1, sp[1], item.equals("hay_block") ? B.axis("hay_block", rng.chance(0.5) ? 'x' : 'z')
                : item.equals("chest") ? B.with("chest", "facing", "south") : B.with("barrel", "facing", "up"));
            s.reserve(sp[0], 1, sp[1]);
        }
    }

    /** Freie Fläche w×d (plus Rand) nahe (tx,tz) innerhalb des Rechtecks suchen. */
    private int[] findSpot(int w, int d, int margin, int yTop, int tx, int tz, int x0, int z0, int x1, int z1) {
        int[] best = null;
        int bd = Integer.MAX_VALUE;
        for (int x = x0; x + w - 1 <= x1; x++)
            for (int z = z0; z + d - 1 <= z1; z++) {
                int dist = Math.abs(x + w / 2 - tx) + Math.abs(z + d / 2 - tz);
                if (dist >= bd) continue;
                if (!areaFree(x - margin, z - margin, x + w - 1 + margin, z + d - 1 + margin, yTop)) continue;
                best = new int[] {x, z};
                bd = dist;
            }
        return best;
    }

    private void well(int x0, int z0) {
        for (int dx = 0; dx <= 2; dx++)
            for (int dz = 0; dz <= 2; dz++) {
                int x = x0 + dx, z = z0 + dz;
                boolean center = dx == 1 && dz == 1;
                v.set(x, 0, z, center ? B.id("water") : base());
                v.set(x, 1, z, center ? B.id("water") : base());
                if (dx != 1 && dz != 1) {
                    v.set(x, 2, z, B.id(fenceB));
                    v.set(x, 3, z, B.id(fenceB));
                }
                v.set(x, 4, z, dz == 0 ? stairs(woodF, Dir.SOUTH) : dz == 2 ? stairs(woodF, Dir.NORTH) : B.id(woodF.block()));
            }
        for (int dx = 0; dx <= 2; dx++) if (5 < H) v.set(x0 + dx, 5, z0 + 1, B.slab(woodF, false));
        v.set(x0 + 1, 3, z0 + 1, B.lantern(light, true));
        reserveArea(x0 - 1, z0 - 1, x0 + 3, z0 + 3, 1, 5);
    }

    /** Sucht entlang einer Hofmauer Platz für einen Schuppen (Pultdach) und baut ihn. */
    private void leanToAlongWall(int wallX, Dir toWall, int z0, int z1, String kind) {
        int sx = -toWall.dx;
        int xa = Math.min(wallX, wallX + sx * 4), xb = Math.max(wallX, wallX + sx * 4);
        for (int len = Math.min(13, (z1 - z0 + 1) - 2); len >= 5; len--) {
            for (int zs = z1 - len + 1; zs >= z0; zs--) {
                if (!areaFree(xa, zs, xb, zs + len - 1, 5)) continue;
                if (!v.isSolid(wallX + toWall.dx, 5, zs)) continue;
                leanTo(wallX, toWall, zs, zs + len - 1, kind);
                return;
            }
        }
    }

    private void leanTo(int wallX, Dir toWall, int z0, int z1, String kind) {
        int dp = 4, sx = -toWall.dx;
        Dir out = toWall.opposite();
        String floor = kind.equals("stable") ? "coarse_dirt" : "gravel";
        for (int z = z0; z <= z1; z++)
            for (int k = 0; k < dp; k++) {
                int x = wallX + sx * k, j = dp - 1 - k;
                v.set(x, 4 + j / 2, z, j % 2 == 0 ? stairs(woodF, toWall) : B.slab(woodF, true));
                if (k < dp - 1) v.set(x, 0, z, B.id(floor));
            }
        for (int z = z0; z <= z1; z++) {
            int x = wallX + sx * (dp - 1);
            boolean post = z == z0 || z == z1 || Math.floorMod(z - z0, 3) == 0;
            if (post) for (int y = 1; y <= 3; y++) v.set(x, y, z, B.axis(logB, 'y'));
            else if (kind.equals("stable")) v.set(x, 1, z, B.gate(fenceB.replace("_fence", "_fence_gate"), out));
            if (!post && Math.floorMod(z - z0, 3) == 1) v.set(x, 3, z, B.lantern(light, true));
        }
        for (int z : new int[] {z0, z1})
            for (int k = 0; k < dp - 1; k++) {
                int x = wallX + sx * k;
                for (int y = 1; y <= 3; y++) v.set(x, y, z, B.id(woodF.block()));
                if (k < 2) v.set(x, 4, z, B.id(woodF.block()));
            }
        if (kind.equals("stable")) {
            for (int z = z0 + 1; z < z1; z++) {
                if (Math.floorMod(z - z0, 3) == 0) {
                    for (int k = 0; k < dp - 1; k++) v.set(wallX + sx * k, 1, z, B.id(fenceB));
                } else if (Math.floorMod(z - z0, 3) == 1) {
                    v.set(wallX, 1, z, B.axis("hay_block", 'y'));
                } else {
                    v.set(wallX, 1, z, B.with("water_cauldron", "level", "3"));
                }
            }
        } else {
            List<String> items = List.of("anvil", "smithing_table", "blast_furnace", "grindstone", "water_cauldron",
                "barrel", "crafting_table", "chest", "barrel");
            int i = 0;
            for (int z = z0 + 1; z < z1 && i < items.size(); z++, i++) {
                String it = items.get(i);
                String st = switch (it) {
                    case "anvil" -> B.with("anvil", "facing", toWall.clockwise().id());
                    case "blast_furnace", "chest" -> B.with(it, "facing", out.id());
                    case "grindstone" -> B.with("grindstone", "face", "floor", "facing", out.id());
                    case "water_cauldron" -> B.with("water_cauldron", "level", "3");
                    case "barrel" -> B.with("barrel", "facing", "up");
                    default -> B.id(it);
                };
                v.set(wallX, 1, z, st);
            }
        }
        int xa = Math.min(wallX, wallX + sx * dp), xb = Math.max(wallX, wallX + sx * dp);
        reserveArea(xa, z0, xb, z1, 1, 5);
    }

    /** Marktstand 2×3 mit gestreiftem Wolldach; {@code face} = Verkaufsseite. */
    private void stall(int x0, int z0, Dir face) {
        int fx = face == Dir.WEST ? x0 : x0 + 1, bx = face == Dir.WEST ? x0 + 1 : x0;
        for (int z : new int[] {z0, z0 + 2})
            for (int x : new int[] {fx, bx})
                for (int y = 1; y <= 2; y++) v.set(x, y, z, B.id(fenceB));
        v.set(fx, 1, z0 + 1, B.slab(woodF, true));
        v.set(bx, 1, z0 + 1, B.with("barrel", "facing", "up"));
        for (int z = z0 - 1; z <= z0 + 3; z++)
            for (int x = Math.min(fx, bx) - (face == Dir.WEST ? 1 : 0); x <= Math.max(fx, bx) + (face == Dir.EAST ? 1 : 0); x++) {
                if (z < z0 || z > z0 + 2) continue;
                v.set(x, 3, z, B.id((Math.floorMod(z, 2) == 0 ? bannerColor : "white") + "_wool"));
            }
        v.set(bx, 2, z0 + 1, B.lantern(light, true));
        reserveArea(x0 - 1, z0 - 1, x0 + 2, z0 + 3, 1, 3);
    }

    /** Kleiner Garten: Hecke, Blumen, in der Mitte ein Baum oder Busch. */
    private void garden(int x0, int z0, int w, int d) {
        List<String> flowers = List.of("poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "red_tulip", "azure_bluet");
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                boolean border = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                boolean gate = z == z0 && x == x0 + w / 2;
                v.set(x, 0, z, B.id(spec.style().sandy() ? "sand" : "grass_block"));
                if (gate) continue;
                if (border) v.set(x, 1, z, B.leaves(p.leaves()));
                else v.set(x, 1, z, B.id(rng.pick(flowers)));
            }
        int cx = x0 + w / 2, cz = z0 + d / 2;
        v.set(cx, 1, cz, B.AIR);
        s.tree(cx, cz, 3);
        if (v.isEmpty(cx, 1, cz)) v.set(cx, 1, cz, B.leaves(p.leaves()));
        reserveArea(x0, z0, x0 + w - 1, z0 + d - 1, 1, 2);
    }

    /** Freitreppen zum Wehrgang an der Innenseite der Frontmauer (nur bei großen Burgen). */
    private void wallStairs(int c, int wh, int hx0, int hx1) {
        int z = c + 2;
        for (int side = 0; side < 2; side++) {
            Dir up = side == 0 ? Dir.WEST : Dir.EAST;
            int xs = side == 0 ? hx0 - 2 : hx1 + 2;
            int xe = xs + up.dx * (wh - 1);
            boolean ok = areaFree(Math.min(xs, xe) - 1, z + 1, Math.max(xs, xe) + 1, z + 1, 2);
            for (int i = 0; i < wh && ok; i++) {
                int x = xs + up.dx * i;
                if (!ground(x, z)) ok = false;
                for (int y = 1; y <= Math.min(wh + 2, 6) && ok; y++) {
                    String at = v.get(x, y, z);
                    if (!B.isAir(at) && !B.name(at).equals("wall_torch")) ok = false;
                    if (s.isReserved(x, y, z)) ok = false;
                }
            }
            if (!ok) continue;
            for (int i = 0; i < wh; i++) {
                int x = xs + up.dx * i;
                for (int y = 1; y <= i; y++) v.set(x, y, z, s.vary(wallB));
                v.set(x, i + 1, z, stairs(wallF, up));
                for (int y = i + 2; y <= i + 4 && y <= wh + 2; y++) if (B.name(String.valueOf(v.get(x, y, z))).equals("wall_torch")) v.set(x, y, z, B.AIR);
                reserveArea(x, z, x, z + 1, 1, 2);
            }
        }
    }

    private void outside(int gx0, int gx1, int zGate) {
        pathRect(gx0, 0, gx1, zGate - 1, false);
        if (zGate >= 3) s.lanternPosts(gx0, 0, gx1 - gx0 + 1);
        s.flowersAndBushes(0, 0, W - 1, D - 1, 0.05, 0.025);
    }

    // ------------------------------------------------------------------ Kleine Burg: Wohnturm + Rundturm

    private void smallCastle() {
        boolean yard = W >= 21 && D >= 22 && H >= 9;
        int tr = Math.min(W, D) >= 16 ? 3 : 2;
        int m = yard ? 4 : (W >= 10 ? 1 : 0);
        int avail = W - 2 * m;
        int kw = Math.min(21, avail - (tr + 1));
        if (kw < 7 && tr == 3) {
            tr = 2;
            kw = Math.min(21, avail - 3);
        }
        int mb = yard ? 4 : (D >= 10 ? 1 : 0);
        int kz0Min = yard ? Math.max(4 + tr + 1, 9) : tr + 1;
        int kd = Math.min(17, (D - 1 - mb) - kz0Min + 1);
        if (kw < 5 || kd < 5) {
            tinyTower();
            return;
        }
        kx0 = m + (avail - (kw + tr + 1)) / 2;
        kx1 = kx0 + kw - 1;
        kz1 = D - 1 - mb;
        kz0 = kz1 - kd + 1;
        hh = H >= 20 ? 6 : 5;
        fh = H >= 15 ? 5 : 4;
        nUp = clamp(spec.floors() - 1, 1, 4);
        keepRoofed = roofKind != Palette.Roof.FLAT && kw >= 7 && kd >= 7;
        int tTop = 0;
        for (int guard = 0; guard < 40; guard++) {
            yDeck = hh + nUp * fh;
            int span = Math.min(kw, kd) - 2;
            int keepNeed = keepRoofed ? yDeck + 1 + (span + 1) / 2 + 4 : yDeck + 2;
            tTop = Math.min(yDeck + (H - yDeck > 26 ? 8 : 4), H - 3);
            if (keepNeed <= H - 1 && tTop >= yDeck + 2) break;
            if (nUp > 1) nUp--;
            else if (keepRoofed) keepRoofed = false;
            else if (hh > 4) hh--;
            else if (fh > 4) fh--;
            else if (nUp > 0) nUp--;
            else break;
        }
        yDeck = hh + nUp * fh;
        int gx0 = 0, gx1 = 0;
        if (yard) {
            int wh2 = H >= 13 ? 4 : 3;
            curtain(1, W - 2, 1, Dir.NORTH, 2, wh2, false, true);
            curtain(1, W - 2, D - 2, Dir.SOUTH, 2, wh2, false, true);
            curtain(1, D - 2, 1, Dir.WEST, 2, wh2, false, true);
            curtain(1, D - 2, W - 2, Dir.EAST, 2, wh2, false, true);
            int ttop = wh2 + 3;
            int tcap = roofKind != Palette.Roof.FLAT && ttop + 1 + spireLayers(false, 2, 0.5) <= H - 1 ? 1 : 0;
            for (int[] cpos : new int[][] {{2, 2}, {W - 3, 2}, {2, D - 3}, {W - 3, D - 3}}) {
                tower(cpos[0], cpos[1], 1, false, 999, ttop, new int[0], tcap, 0.5, false, null, false, null);
            }
            int gw = W % 2 == 1 ? 3 : 4;
            gx0 = (W - gw) / 2;
            gx1 = gx0 + gw - 1;
            int gh = wh2 - 1;
            for (int x = gx0; x <= gx1; x++)
                for (int z = 1; z <= 2; z++) {
                    for (int y = 1; y <= gh; y++) v.set(x, y, z, B.AIR);
                    v.set(x, 0, z, pave());
                }
            if (gh >= 3) {
                for (int z = 1; z <= 2; z++) {
                    v.set(gx0, gh, z, corbel(wallF, Dir.WEST));
                    v.set(gx1, gh, z, corbel(wallF, Dir.EAST));
                }
                for (int x = gx0 + 1; x <= gx1 - 1; x++) v.set(x, gh, 2, B.id("iron_bars"));
            }
            for (int side = 0; side < 2; side++) {
                int px = side == 0 ? gx0 - 2 : gx1 + 2;
                tower(px, 2, 1, false, 999, wh2 + 3, new int[0], tcap, 0.5, false, null, false, null);
                int tx = side == 0 ? gx0 - 1 : gx1 + 1;
                put(tx, 2, 0, B.wallTorch(Dir.NORTH));
            }
            reserveArea(gx0, 0, gx1, 3, 1, 2);
        }
        // Palas und angebauter Turm
        keepViaTower = true;
        doorMaxX = kx1 - tr - 3;
        keep(false);
        int tcx = kx1, tcz = kz0;
        int cap = 0;
        double step = 0.55;
        if (roofKind != Palette.Roof.FLAT) {
            for (double st : new double[] {0.55, 0.8}) {
                int fit = H - 1 - 2 - spireLayers(round, tr + 1, st) - (st < 0.6 ? 2 : 0);
                if (fit >= yDeck + 3) {
                    cap = 1;
                    step = st;
                    tTop = Math.min(tTop, fit);
                    break;
                }
            }
        }
        List<Integer> fl = new ArrayList<>();
        for (int i = 0; i < nUp; i++) fl.add(hh + i * fh);
        fl.add(yDeck);
        if (tTop >= yDeck + 8) fl.add(yDeck + 4);
        int[] floors = fl.stream().mapToInt(Integer::intValue).toArray();
        int[] clip = {kx0 + 1, kz0 + 1, kx1 - 1, kz1 - 1, yDeck - 1};
        tower(tcx, tcz, tr, round, 1, tTop, floors, cap, step, true, clip, true, Dir.NORTH);
        // Falls der Turm das Portal überdeckt: Tür in den Turm
        if (!B.name(String.valueOf(v.get(keepDoorX, 1, kz0))).endsWith("_door")) {
            if (towerDoor(tcx, tcz, tr, round, Dir.NORTH, Dir.WEST)) {
                for (int x = tcx - tr; x <= tcx + tr; x++) {
                    if (B.name(String.valueOf(v.get(x, 1, tcz - tr))).endsWith("_door")
                        || B.name(String.valueOf(v.get(x, 1, tcz - tr + 1))).endsWith("_door")) { keepDoorX = x; keepDoorW = 1; }
                }
            } else if (!towerDoor(tcx, tcz, tr, round, Dir.EAST, Dir.NORTH)) {
                for (int x = kx0 + 1; x < kx1; x++) {
                    if (v.isSolid(x, 1, kz0) && v.isEmpty(x, 1, kz0 + 1) && v.isEmpty(x, 2, kz0 + 1) && s.free(x, 1, kz0 - 1)) {
                        door(x, 1, kz0, Dir.NORTH, false);
                        keepDoorX = x;
                        keepDoorW = 1;
                        break;
                    }
                }
            }
        }
        // Austritt vom Turm auf den Wehrgang des Palas
        int[][] exits = {{tcx - tr, tcz, -1, 0}, {tcx, tcz + tr, 0, 1}};
        for (int[] e : exits) {
            int ox = e[0] + e[2], oz = e[1] + e[3];
            if (v.isEmpty(ox, yDeck + 1, oz) && v.isEmpty(ox, yDeck + 2, oz) && v.isSolid(ox, yDeck, oz)) doorway(e[0], yDeck + 1, e[1]);
        }
        // Wege, Brunnen, Garten
        int dx0 = keepDoorX, dx1 = keepDoorX + keepDoorW - 1;
        if (yard) {
            int gc = (gx0 + gx1) / 2;
            int zTurn = Math.max(3, kz0 - 3);
            pathRect(gx0, 0, gx1, zTurn, true);
            pathRect(Math.min(gc, dx0), zTurn, Math.max(gc, dx1), zTurn + 1, true);
            pathRect(dx0, zTurn, dx1, kz0 - 1, true);
            reserveArea(gx0, 3, gx1, zTurn + 1, 1, 2);
            reserveArea(dx0, zTurn, dx1, kz0 - 1, 1, 2);
            for (int z = 4; z < zTurn; z += 4) s.lanternPosts(gx0, z, gx1 - gx0 + 1);
            int[] w = findSpot(3, 3, 1, 5, gx0 - 4, (3 + kz0) / 2, 3, 3, W - 4, kz0 - 2);
            if (w != null) well(w[0], w[1]);
            leanToAlongWall(3, Dir.WEST, 4, D - 5, "stable");
            if (W >= 27) leanToAlongWall(W - 4, Dir.EAST, 4, D - 5, "smithy");
            int[] st = findSpot(2, 3, 1, 4, gx1 + 3, (3 + kz0) / 2, gx1 + 2, 3, W - 4, kz0 - 2);
            if (st != null) stall(st[0], st[1], Dir.WEST);
            int[] g = findSpot(5, 4, 0, 6, W - 7, (3 + kz0) / 2, 3, 3, W - 4, D - 5);
            if (g != null) garden(g[0], g[1], 5, 4);
            for (int i = 0; i < (W * D) / 400; i++) {
                int[] t = findSpot(5, 5, 0, 7, 3 + rng.nextInt(Math.max(1, W - 6)), 3 + rng.nextInt(Math.max(1, D - 6)), 3, 3, W - 4, D - 5);
                if (t == null) break;
                s.tree(t[0] + 2, t[1] + 2, 3);
                reserveArea(t[0], t[1], t[0] + 4, t[1] + 4, 1, 2);
            }
            s.flowersAndBushes(0, 0, W - 1, D - 1, 0.03, 0.02);
        } else {
            pathRect(dx0, 0, dx1, kz0 - 1, false);
            reserveArea(dx0, 0, dx1, kz0 - 1, 1, 2);
            if (kz0 >= 4) s.lanternPosts(dx0, 1, dx1 - dx0 + 1);
            if (kz0 >= 7) {
                s.tree(Math.max(2, kx0), 2, 3);
            }
            s.flowersAndBushes(0, 0, W - 1, D - 1, 0.08, 0.04);
        }
    }

    // ------------------------------------------------------------------ Sehr klein: Wehrturm

    private void tinyTower() {
        int mx = W >= 7 ? 1 : 0, mz = D >= 7 ? 1 : 0;
        boolean walled = W >= 15 && D >= 15 && H >= 5;
        int fw = Math.min(W - 2 * mx, 9), fd = Math.min(D - 2 * mz, 9);
        int x0 = (W - fw) / 2, x1 = x0 + fw - 1;
        int z1 = walled ? D - 4 : (D - fd) / 2 + fd - 1, z0 = z1 - fd + 1;
        if (walled) {
            int wh = Math.max(2, Math.min(4, H - 3));
            curtain(1, W - 2, 1, Dir.NORTH, 2, wh, false, true);
            curtain(1, W - 2, D - 2, Dir.SOUTH, 2, wh, false, true);
            curtain(1, D - 2, 1, Dir.WEST, 2, wh, false, true);
            curtain(1, D - 2, W - 2, Dir.EAST, 2, wh, false, true);
            int gx0 = (W - 3) / 2;
            for (int x = gx0; x < gx0 + 3; x++)
                for (int z = 1; z <= 2; z++) {
                    for (int y = 1; y <= Math.max(2, wh - 1); y++) v.set(x, y, z, B.AIR);
                    v.set(x, 0, z, pave());
                }
            put(gx0 - 1, 2, 0, B.wallTorch(Dir.NORTH));
            put(gx0 + 3, 2, 0, B.wallTorch(Dir.NORTH));
            int xc = (x0 + x1) / 2;
            pathRect(gx0 + 1, 0, gx0 + 1, 4, false);
            pathRect(Math.min(gx0 + 1, xc), 4, Math.max(gx0 + 1, xc), 4, false);
            pathRect(xc, 4, xc, z0 - 1, false);
            leanToAlongWall(3, Dir.WEST, 4, D - 5, "stable");
            s.flowersAndBushes(3, 3, W - 4, D - 4, 0.03, 0.02);
        }
        int top = H >= 6 ? H - 3 : -1;
        int wallTop = top > 0 ? top - 1 : H - 2;
        int xc = (x0 + x1) / 2;
        boolean hollow = x1 - x0 >= 2 && z1 - z0 >= 2;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean ring = x == x0 || x == x1 || z == z0 || z == z1;
                v.set(x, 0, z, ring || !hollow ? base() : B.id(woodF.block()));
                for (int y = 1; y <= wallTop; y++) {
                    boolean floor = y % 4 == 0 && (top < 0 || y <= top - 3);
                    v.set(x, y, z, ring || !hollow ? wallAt(y) : floor ? B.id(woodF.block()) : B.AIR);
                }
                if (top > 0) {
                    v.set(x, top, z, ring || !hollow ? wallAt(top) : B.id(woodF.block()));
                    if (ring) {
                        v.set(x, top + 1, z, wallAt(top + 1));
                        if (merlon(x + z)) v.set(x, top + 2, z, wallAt(top + 2));
                    }
                } else if (ring && merlon(x + z)) {
                    v.set(x, H - 1, z, wallAt(H - 1));
                }
            }
        // Sockelschräge und vorgekragter Zinnenkranz, wenn rundherum Platz ist
        if (x0 >= 1 && z0 >= 1 && x1 + 1 < W && z1 + 1 < D) {
            for (int x = x0 - 1; x <= x1 + 1; x++)
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    boolean outer = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                    if (!outer) continue;
                    boolean corner = (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1);
                    Dir toWall = x == x0 - 1 ? Dir.EAST : x == x1 + 1 ? Dir.WEST : z == z0 - 1 ? Dir.SOUTH : Dir.NORTH;
                    if (!corner) put(x, 1, z, stairs(baseF, toWall));
                    if (top > 3) {
                        if (!corner && merlon(x + z)) put(x, top - 1, z, corbel(wallF, toWall));
                        v.set(x, top, z, wallAt(top));
                        v.set(x, top + 1, z, wallAt(top + 1));
                        if (merlon(x + z)) v.set(x, top + 2, z, wallAt(top + 2));
                        if (!(x == x0 || x == x1 || z == z0 || z == z1)) continue;
                    }
                }
            if (top > 3) {
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        boolean ring = x == x0 || x == x1 || z == z0 || z == z1;
                        if (ring) { v.set(x, top + 1, z, B.AIR); v.set(x, top + 2, z, B.AIR); }
                    }
            }
        }
        if (!hollow) return;
        door(xc, 1, z0, Dir.NORTH, false);
        int lz = z1 - 1;
        int lTop = top > 0 ? top : wallTop;
        if (lz > z0 || top > 0) {
            for (int y = 1; y <= lTop; y++) {
                if (y == 1 && lz == z0 + 1 && xc == xc) {
                    // Leiter direkt hinter der Tür ist in Ordnung
                }
                v.set(xc, y, lz, B.ladder(Dir.NORTH));
                v.set(xc, y, z1, wallAt(y));
            }
        }
        for (int y = 2; y + 1 <= wallTop; y += 4) {
            for (int z : new int[] {(z0 + z1) / 2}) {
                if (v.isSolid(x0, y, z) && v.isSolid(x0, y + 1, z)) { v.set(x0, y, z, B.AIR); v.set(x0, y + 1, z, B.AIR); }
                if (v.isSolid(x1, y, z) && v.isSolid(x1, y + 1, z)) { v.set(x1, y, z, B.AIR); v.set(x1, y + 1, z, B.AIR); }
            }
        }
        int ly = Math.min(wallTop, 3);
        if (x1 - x0 >= 3) s.hangingLight(x0 + 1, ly, z0 + 1);
        if (z0 >= 1 && 4 < H && v.isSolid(xc, 4, z0) && v.isEmpty(xc, 4, z0 - 1) && v.isEmpty(xc, 3, z0 - 1)) {
            v.set(xc, 4, z0 - 1, B.with(bannerColor + "_wall_banner", "facing", "north"));
        }
        if (z0 >= 1) put(xc - 1, 2, z0 - 1, B.wallTorch(Dir.NORTH));
        pathRect(xc, 0, xc, z0 - 1, false);
    }
}
