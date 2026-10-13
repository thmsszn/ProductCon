package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.List;

/**
 * Außenanlagen-Schicht für alle Generatoren (rein Java, deterministisch über {@link Site#rng}):
 * Baumarten je Stil, Büsche, Beete mit Zaun, Gemüsebeet, Teich mit Seerosen, Brunnen, Sitzgruppen,
 * Kisten-/Heuballen-Gruppen, Laternenpfosten, Hecken, gemischte Wege mit Trittsteinen und hohes Gras.
 * Alles bleibt auf freiem Boden und hält Abstand zum Gebäude (Belegungsraster aus dem Ist-Zustand).
 */
final class Garden {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final Rng rng;
    private final Palette.Style style;
    private final int w;
    private final int d;
    /** Spalte ist belegt (Gebäude, Zaun, bereits platzierte Gartenteile). */
    private final boolean[][] occ;
    private final boolean[][] isPath;
    private final int[][] dist;
    private final List<int[]> pathCells = new ArrayList<>();
    /** Zielzellen (Zugang) der Gartenteile, zu denen ein geschwungener Weg führen kann. */
    private final List<int[]> targets = new ArrayList<>();
    private int availCount;

    private static final List<String> SHORT_FLOWERS = List.of("poppy", "cornflower", "allium", "azure_bluet", "red_tulip",
        "pink_tulip", "oxeye_daisy", "lily_of_the_valley", "white_tulip", "orange_tulip", "dandelion", "blue_orchid");

    Garden(Site s) {
        this.s = s;
        this.v = s.v;
        this.p = s.p;
        this.rng = s.rng;
        this.style = s.spec.style();
        this.w = s.w;
        this.d = s.d;
        this.occ = new boolean[w][d];
        this.isPath = new boolean[w][d];
        this.dist = new int[w][d];
    }

    // ------------------------------------------------------------------ Stilfragen

    private boolean sandy() {
        return style.sandy();
    }

    private boolean rural() {
        return switch (style) {
            case MEDIEVAL, RUSTIC, NORDIC, VIKING, FANTASY, STONE -> true;
            default -> false;
        };
    }

    private boolean formal() {
        return switch (style) {
            case VICTORIAN, CLASSICAL, MODERN, STEAMPUNK -> true;
            default -> false;
        };
    }

    /** Beetrand: Holzzaun (ländlich) oder niedrige Platte aus der Zierfamilie (formell/Stein/Wüste). */
    private boolean fencedBeds() {
        return switch (style) {
            case MEDIEVAL, RUSTIC, NORDIC, VIKING, FANTASY, JAPANESE, VICTORIAN -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ Einstieg

    /** Gesamte Außenanlage; {@code richness} 0..1 skaliert die Menge (1 = volle Gartenausstattung). */
    void run(double richness) {
        snapshot();
        if (availCount < 12) return;
        if (pathCells.isEmpty()) seedPathAtDoor();
        double f = richness * Math.min(1.4, availCount / 260.0 + 0.35);
        retexturePaths();

        if (style == Palette.Style.JAPANESE && availCount >= 70) zenGarden();
        int beds = (int) Math.round(f * 2.4);
        for (int i = 0; i < beds && i < 4; i++) flowerBed(i == 0);
        if (f >= 0.45 && availCount >= 80) gardenTable();
        if (f >= 0.5 && availCount >= 100 && (!sandy() || rng.chance(0.8)) && rng.chance(0.85)) pond();
        if (rural() && f >= 0.6 && availCount >= 130 && rng.chance(0.65)) well();
        else if ((style == Palette.Style.CLASSICAL || style == Palette.Style.VICTORIAN || sandy()) && f >= 0.6
            && availCount >= 130 && rng.chance(0.4)) well();
        if (rural() && f >= 0.6 && availCount >= 200 && rng.chance(0.55)) veggiePatch();
        if (rural() || style == Palette.Style.STEAMPUNK || style == Palette.Style.VICTORIAN) {
            int crates = f >= 0.8 ? 2 : 1;
            for (int i = 0; i < crates; i++) crateCluster();
        }
        connectTargets();
        pathLights(Math.max(1, (int) Math.round(f * 3)));
        for (int i = 0; i < (f >= 0.7 ? 2 : 1); i++) benchByPath();
        trees(Math.min(9, (int) Math.round(1.5 + f * availCount / 200.0)));
        bushes((int) Math.round(f * availCount / 65.0));
        hedges();
        if (sandy()) cacti((int) Math.round(f * availCount / 45.0));
        meadow(f);
    }

    // ------------------------------------------------------------------ Belegung

    private static String nameOf(String b) {
        return b == null ? "" : B.name(b);
    }

    private static boolean groundName(String n) {
        return switch (n) {
            case "grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "sand", "red_sand" -> true;
            default -> false;
        };
    }

    private static boolean grassName(String n) {
        return switch (n) {
            case "grass_block", "dirt", "podzol", "moss_block", "coarse_dirt" -> true;
            default -> false;
        };
    }

    private void snapshot() {
        String pathId = B.id(p.path());
        // Ist der Weg derselbe Block wie der Boden (Sand), lässt er sich nicht erkennen
        if (pathId.equals(v.get(1, 0, 1)) || pathId.equals(v.get(w - 2, 0, d - 2))) pathId = "-";
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean o = false;
                for (int y = 1; y < v.h && !o; y++) {
                    String b = v.get(x, y, z);
                    if (b != null && !B.isAir(b)) o = true;
                }
                if (s.isReserved(x, 1, z) || s.isReserved(x, 2, z)) o = true;
                occ[x][z] = o;
                String g = v.get(x, 0, z);
                if (g != null && g.equals(pathId) && !o) {
                    isPath[x][z] = true;
                    pathCells.add(new int[] {x, z});
                }
            }
        recomputeDist();
    }

    private void recomputeDist() {
        int inf = 999;
        availCount = 0;
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) dist[x][z] = occ[x][z] ? 0 : inf;
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++)
                for (int[] n : new int[][] {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}}) relax(x, z, x + n[0], z + n[1]);
        for (int x = w - 1; x >= 0; x--)
            for (int z = d - 1; z >= 0; z--)
                for (int[] n : new int[][] {{1, 1}, {0, 1}, {-1, 1}, {1, 0}}) relax(x, z, x + n[0], z + n[1]);
        for (int x = 1; x < w - 1; x++)
            for (int z = 1; z < d - 1; z++) if (avail(x, z)) availCount++;
    }

    private void relax(int x, int z, int nx, int nz) {
        if (nx < 0 || nz < 0 || nx >= w || nz >= d) return;
        if (dist[nx][nz] + 1 < dist[x][z]) dist[x][z] = dist[nx][nz] + 1;
    }

    /** Freier, bodennaher Gartenboden (keine Wege, nichts darüber, nicht reserviert). */
    private boolean avail(int x, int z) {
        if (x < 1 || z < 1 || x >= w - 1 || z >= d - 1) return false;
        if (occ[x][z] || isPath[x][z]) return false;
        return groundName(nameOf(v.get(x, 0, z)));
    }

    private boolean grassy(int x, int z) {
        return grassName(nameOf(v.get(x, 0, z)));
    }

    private void mark(int x0, int z0, int fw, int fd) {
        for (int x = x0; x < x0 + fw; x++)
            for (int z = z0; z < z0 + fd; z++) {
                if (x < 0 || z < 0 || x >= w || z >= d) continue;
                occ[x][z] = true;
                s.reserve(x, 1, z);
                s.reserve(x, 2, z);
            }
        recomputeDist();
    }

    /** Sucht ein freies Rechteck {@code fw x fd} mit Abstand {@code margin} zu Gebäude/Bauteilen, auf Wunsch gebäudenah. */
    private int[] findSpot(int fw, int fd, int margin, boolean near) {
        if (fw > w - 2 || fd > d - 2) return null;
        for (int t = 0; t < 90; t++) {
            int x0 = 1 + rng.nextInt(w - 1 - fw), z0 = 1 + rng.nextInt(d - 1 - fd);
            boolean ok = true;
            int best = 999;
            for (int x = x0; x < x0 + fw && ok; x++)
                for (int z = z0; z < z0 + fd && ok; z++) {
                    if (!avail(x, z) || dist[x][z] <= margin) ok = false;
                    best = Math.min(best, dist[x][z]);
                }
            if (ok && (!near || t >= 50 || best <= 6)) return new int[] {x0, z0};
        }
        return null;
    }

    private void set(int x, int y, int z, String state) {
        v.set(x, y, z, state);
    }

    // ------------------------------------------------------------------ Wege

    private List<String> pathMix() {
        String n = B.name(p.path());
        return switch (n) {
            case "dirt_path" -> List.of("dirt_path", "dirt_path", "dirt_path", "dirt_path", "dirt_path", "gravel", "coarse_dirt", "cobblestone");
            case "gravel" -> List.of("gravel", "gravel", "gravel", "gravel", "cobblestone", "andesite", "coarse_dirt", "mossy_cobblestone");
            case "coarse_dirt" -> List.of("coarse_dirt", "coarse_dirt", "coarse_dirt", "dirt_path", "gravel", "cobblestone", "mossy_cobblestone");
            case "sand" -> List.of("sand", "sand", "sand", "smooth_sandstone", "cut_sandstone", "sandstone", "sand");
            case "smooth_stone" -> List.of("smooth_stone", "smooth_stone", "stone_bricks", "polished_andesite", "stone", "andesite", "smooth_stone");
            case "cobblestone" -> List.of("cobblestone", "cobblestone", "cobblestone", "mossy_cobblestone", "stone", "gravel", "andesite");
            case "cobbled_deepslate" -> List.of("cobbled_deepslate", "cobbled_deepslate", "gravel", "cobblestone", "tuff", "stone");
            case "stone_bricks" -> List.of("stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "stone", "gravel");
            case "bricks" -> List.of("bricks", "bricks", "stone_bricks", "gravel", "cobblestone");
            case "gray_concrete" -> List.of("gray_concrete", "gray_concrete", "stone", "smooth_stone", "light_gray_concrete");
            default -> List.of(p.path(), p.path(), p.path(), p.path(), p.path(), "gravel", "cobblestone");
        };
    }

    private List<String> stoneMix() {
        if (sandy()) return List.of("smooth_sandstone", "cut_sandstone", "sandstone", "smooth_red_sandstone");
        return switch (style) {
            case GOTHIC -> List.of("cracked_stone_bricks", "stone_bricks", "polished_andesite", "deepslate_bricks");
            case MODERN, CLASSICAL -> List.of("smooth_stone", "polished_andesite", "stone", "stone_bricks");
            case JAPANESE -> List.of("stone", "andesite", "mossy_cobblestone", "stone_bricks");
            default -> List.of("stone_bricks", "mossy_cobblestone", "cobblestone", "andesite", "mossy_stone_bricks", "stone");
        };
    }

    private String pickPath() {
        return B.id(rng.pick(pathMix()));
    }

    private boolean pathOk(String block, int x, int z) {
        // dirt_path braucht freie Luft darüber
        return !B.name(block).equals("dirt_path") || v.isEmpty(x, 1, z);
    }

    /** Kein erkennbarer Weg: vor der ersten Erdgeschosstür eine Wegzelle als Ausgangspunkt für geschwungene Wege setzen. */
    private void seedPathAtDoor() {
        int[] best = null; // x, z, dx, dz der bevorzugten Außentür
        for (int z = 0; z < d; z++)
            for (int x = 0; x < w; x++) {
                String b = null;
                for (int y = 1; y <= 3 && b == null; y++) {
                    String c = v.get(x, y, z);
                    if (c != null && B.name(c).endsWith("_door") && c.contains("half=lower")) b = c;
                }
                if (b == null) continue;
                int i = b.indexOf("facing=");
                if (i < 0) continue;
                int e = b.indexOf(',', i);
                if (e < 0) e = b.indexOf(']', i);
                String face = b.substring(i + 7, e);
                for (Dir dir : Dir.values()) {
                    if (!dir.id().equals(face)) continue;
                    // Tür zeigt nach innen: Außenzelle direkt vor der Tür muss frei sein (sonst Innentür)
                    if (!avail(x - dir.dx, z - dir.dz)) break;
                    if (best == null || z > best[1] || (z == best[1] && dist[x][z] < dist[best[0]][best[1]])) best = new int[] {x, z, dir.dx, dir.dz};
                }
            }
        if (best != null) {
            set(best[0] - best[2], 0, best[1] - best[3], pickPath());
            isPath[best[0] - best[2]][best[1] - best[3]] = true;
            pathCells.add(new int[] {best[0] - best[2], best[1] - best[3]});
            return;
        }
        // Vorplatz ohne freie Bodenzelle (Vorbau/Terrasse): erste Tür, nächste freie Zelle im Umkreis
        for (int z = d - 1; z >= 0; z--)
            for (int x = 0; x < w; x++) {
                String c = v.get(x, 1, z);
                if (c == null || !B.name(c).endsWith("_door") || !c.contains("half=lower")) continue;
                for (int r = 1; r <= 8; r++)
                    for (int dz = -r; dz <= r; dz++)
                        for (int dx = -r; dx <= r; dx++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !avail(x + dx, z + dz)) continue;
                            set(x + dx, 0, z + dz, pickPath());
                            isPath[x + dx][z + dz] = true;
                            pathCells.add(new int[] {x + dx, z + dz});
                            return;
                        }
                return;
            }
    }

    /** Bestehende Wege abwechslungsreich mischen (Kiesstreifen, Kopfsteinflicken) und mit Randausfransung. */
    private void retexturePaths() {
        for (int[] c : pathCells) {
            if (!v.isEmpty(c[0], 1, c[1])) continue;
            String b = pickPath();
            if (pathOk(b, c[0], c[1])) set(c[0], 0, c[1], b);
        }
        for (int[] c : new ArrayList<>(pathCells)) {
            for (Dir dir : Dir.values()) {
                int nx = c[0] + dir.dx, nz = c[1] + dir.dz;
                if (!avail(nx, nz) || !grassy(nx, nz) || !rng.chance(0.17)) continue;
                set(nx, 0, nz, rng.chance(0.6) ? B.id(sandy() ? "sand" : "coarse_dirt") : B.id("gravel"));
            }
        }
    }

    /** Geschwungener Weg (Bogen/S-Kurve) von (sx,sz) nach (tx,tz), wahlweise durchgehend oder als Trittsteine. */
    private void curvedPath(int sx, int sz, int tx, int tz) {
        int dx = tx - sx, dz = tz - sz;
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (len < 2 || len > 40) return;
        double px = -dz / len, pz = dx / len;
        double amp = Math.min(3.0, len * 0.22) * (rng.chance(0.5) ? 1 : -1);
        int waves = rng.chance(0.5) ? 1 : 2;
        boolean stones = rng.chance(0.4) || sandy();
        List<String> mix = stones ? stoneMix() : pathMix();
        int steps = (int) Math.ceil(len * 3);
        int lx = sx, lz = sz, n = 0;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double off = amp * Math.sin(Math.PI * t * waves) * Math.min(1.0, Math.min(t, 1 - t) * 4 + 0.15);
            int x = (int) Math.round(sx + dx * t + px * off), z = (int) Math.round(sz + dz * t + pz * off);
            if (x == lx && z == lz) continue;
            if (x != lx && z != lz) {
                // Ecke einfügen, damit der Weg zusammenhängt
                layPath(lx, z, mix, stones, n++);
            }
            layPath(x, z, mix, stones, n++);
            lx = x;
            lz = z;
        }
        layPath(tx, tz, mix, false, 0);
    }

    private void layPath(int x, int z, List<String> mix, boolean stones, int n) {
        if (!avail(x, z)) return;
        if (stones && n % 2 == 1 && !rng.chance(0.2)) return; // Lücken zwischen Trittsteinen
        String b = B.id(rng.pick(mix));
        if (!pathOk(b, x, z)) b = B.id("gravel");
        set(x, 0, z, b);
        isPath[x][z] = true;
        pathCells.add(new int[] {x, z});
        if (!stones && rng.chance(0.22)) {
            // Wegverbreiterung seitlich
            Dir dir = Dir.values()[rng.nextInt(4)];
            int ax = x + dir.dx, az = z + dir.dz;
            if (avail(ax, az) && grassy(ax, az)) {
                set(ax, 0, az, B.id(rng.pick(mix)));
                isPath[ax][az] = true;
            }
        }
    }

    /** Wege vom nächsten bestehenden Weg zu den wichtigsten Gartenteilen. */
    private void connectTargets() {
        if (pathCells.isEmpty()) return;
        int done = 0;
        for (int[] t : targets) {
            if (done >= 4) break;
            int[] best = null;
            int bd = 999;
            for (int[] c : pathCells) {
                int dd = Math.abs(c[0] - t[0]) + Math.abs(c[1] - t[1]);
                if (dd < bd) {
                    bd = dd;
                    best = c;
                }
            }
            if (best == null || bd < 2) continue;
            curvedPath(best[0], best[1], t[0], t[1]);
            done++;
        }
    }

    // ------------------------------------------------------------------ Beete

    private String fenceB() {
        return B.id(p.fence());
    }

    private String rimBlock(boolean fenced) {
        return fenced ? fenceB() : B.slab(p.trim(), false);
    }

    private void flowerBed(boolean first) {
        boolean fenced = fencedBeds();
        boolean along = rng.chance(0.5);
        int iw = 3 + rng.nextInt(4), id = first ? 2 : 1 + rng.nextInt(2);
        int fw = (along ? iw : id) + 2, fd = (along ? id : iw) + 2;
        int[] spot = findSpot(fw, fd, 0, true);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, fw, fd);
        boolean sand = sandy();
        String soil = sand ? "dirt" : rng.chance(0.5) ? "podzol" : "dirt";
        String rim = rimBlock(fenced);
        boolean tallDone = false;
        String a = rng.pick(SHORT_FLOWERS), b = rng.pick(SHORT_FLOWERS);
        for (int x = x0; x < x0 + fw; x++)
            for (int z = z0; z < z0 + fd; z++) {
                boolean edge = x == x0 || z == z0 || x == x0 + fw - 1 || z == z0 + fd - 1;
                if (edge) {
                    set(x, 1, z, rim);
                    continue;
                }
                set(x, 0, z, B.id(soil));
                int row = along ? z - z0 : x - x0;
                if (!tallDone && row == 1 && rng.chance(0.6) && v.isEmpty(x, 2, z)) {
                    // hohe Pflanzen (zweiteilig)
                    String tall = rng.pick(List.of("rose_bush", "peony", "lilac"));
                    set(x, 1, z, B.with(tall, "half", "lower"));
                    set(x, 2, z, B.with(tall, "half", "upper"));
                    continue;
                }
                if (rng.chance(0.08)) continue;
                set(x, 1, z, B.id(row % 2 == 0 ? a : b));
            }
        // Ecklaternen / Blumentöpfe auf den Zaunpfosten
        int[][] corners = {{x0, z0}, {x0 + fw - 1, z0}, {x0, z0 + fd - 1}, {x0 + fw - 1, z0 + fd - 1}};
        for (int[] c : corners) {
            if (fenced && rng.chance(0.3)) set(c[0], 2, c[1], B.lantern(p.light(), false));
            else if (rng.chance(0.2)) set(c[0], 2, c[1], B.id(rng.pick(List.of("potted_azalea_bush", "potted_fern", "potted_red_tulip", "potted_cornflower"))));
        }
        int[] acc = accessCell(x0, z0, fw, fd);
        if (acc != null) targets.add(acc);
    }

    /** Zugangszelle an der Seite eines Rechtecks, die dem Gebäude/Weg am nächsten liegt. */
    private int[] accessCell(int x0, int z0, int fw, int fd) {
        int[][] cand = {{x0 + fw / 2, z0 - 1}, {x0 + fw / 2, z0 + fd}, {x0 - 1, z0 + fd / 2}, {x0 + fw, z0 + fd / 2}};
        int[] best = null;
        int bd = 999;
        for (int[] c : cand) {
            if (!avail(c[0], c[1])) continue;
            if (best == null) best = c;
            for (int[] pc : pathCells) {
                int dd = Math.abs(pc[0] - c[0]) + Math.abs(pc[1] - c[1]);
                if (dd < bd) {
                    bd = dd;
                    best = c;
                }
            }
        }
        return best;
    }

    private void veggiePatch() {
        int iw = 4 + rng.nextInt(3);
        boolean along = rng.chance(0.5);
        int fw = (along ? iw : 3) + 2, fd = (along ? 3 : iw) + 2;
        int[] spot = findSpot(fw, fd, 1, false);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, fw, fd);
        String crop1 = rng.pick(List.of("wheat", "carrots", "potatoes")), crop2 = rng.pick(List.of("carrots", "potatoes", "beetroots", "wheat"));
        for (int x = x0; x < x0 + fw; x++)
            for (int z = z0; z < z0 + fd; z++) {
                boolean edge = x == x0 || z == z0 || x == x0 + fw - 1 || z == z0 + fd - 1;
                if (edge) {
                    set(x, 1, z, fenceB());
                    continue;
                }
                int row = along ? z - z0 : x - x0; // 1..3, Reihe 2 = Wasser
                if (row == 2) {
                    set(x, 0, z, B.id("water"));
                    continue;
                }
                set(x, 0, z, B.with("farmland", "moisture", "7"));
                String c = row == 1 ? crop1 : crop2;
                int age = c.equals("beetroots") ? 2 + rng.nextInt(2) : 4 + rng.nextInt(4);
                if (rng.chance(0.08)) continue;
                set(x, 1, z, B.with(c, "age", String.valueOf(age)));
            }
        // Vogelscheuche in der Wasserreihe
        int sx = along ? x0 + fw / 2 : x0 + 2, sz = along ? z0 + 2 : z0 + fd / 2;
        set(sx, 0, sz, B.id("dirt"));
        set(sx, 1, sz, fenceB());
        set(sx, 2, sz, B.axis("hay_block", 'y'));
        set(sx, 3, sz, B.with("carved_pumpkin", "facing", Dir.NORTH.id()));
        int ax = along ? 1 : 0, az = 1 - ax;
        for (int sgn = -1; sgn <= 1; sgn += 2) { // Arme mit Stütze darunter
            int ex = sx + sgn * ax, ez = sz + sgn * az;
            set(ex, 0, ez, B.id("dirt"));
            set(ex, 1, ez, fenceB());
            set(ex, 2, ez, fenceB());
        }
        int[] acc = accessCell(x0, z0, fw, fd);
        if (acc != null) targets.add(acc);
    }

    /** Zen-Garten: geharkter Kies mit Findlingen, Bambus und einer Steinlaterne. */
    private void zenGarden() {
        int[] spot = findSpot(6, 5, 1, true);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, 6, 5);
        for (int x = x0; x < x0 + 6; x++)
            for (int z = z0; z < z0 + 5; z++) {
                boolean edge = x == x0 || z == z0 || x == x0 + 5 || z == z0 + 4;
                set(x, 0, z, B.id(edge && rng.chance(0.4) ? "coarse_dirt" : "gravel"));
            }
        int stones = 2 + rng.nextInt(2);
        for (int i = 0; i < stones; i++) {
            int x = x0 + 1 + rng.nextInt(4), z = z0 + 1 + rng.nextInt(3);
            set(x, 1, z, B.id(rng.pick(List.of("mossy_cobblestone", "andesite", "stone", "mossy_cobblestone"))));
            if (rng.chance(0.4)) set(x, 2, z, B.with("mossy_cobblestone_slab", "type", "bottom"));
        }
        postLantern(x0 + 4, z0 + 2, true);
        bamboo(x0, z0);
        bamboo(x0 + 5, z0 + 4);
        int[] acc = accessCell(x0, z0, 6, 5);
        if (acc != null) targets.add(acc);
    }

    private void bamboo(int x, int z) {
        int n = 3 + rng.nextInt(3);
        for (int i = 0; i < n; i++) {
            int bx = x + (i == 0 ? 0 : rng.nextInt(3) - 1), bz = z + (i == 0 ? 0 : rng.nextInt(3) - 1);
            if (bx < 1 || bz < 1 || bx >= w - 1 || bz >= d - 1) continue;
            String g = nameOf(v.get(bx, 0, bz));
            if (!g.equals("grass_block") && !g.equals("gravel") && !g.equals("coarse_dirt") && !g.equals("dirt") && !g.equals("podzol")) continue;
            int h = 3 + rng.nextInt(3);
            boolean ok = !isPath[bx][bz];
            for (int y = 1; y <= h && ok; y++) if (!v.isEmpty(bx, y, bz) || s.isReserved(bx, y, bz)) ok = false;
            if (!ok) continue;
            for (int y = 1; y <= h; y++) set(bx, y, bz, B.id("bamboo"));
            occ[bx][bz] = true;
            s.reserve(bx, 1, bz);
        }
    }

    // ------------------------------------------------------------------ Teich

    private void pond() {
        int rx = 2 + rng.nextInt(3), rz = 2 + rng.nextInt(2);
        int fw = 2 * rx + 3, fd = 2 * rz + 3;
        boolean oasis = sandy();
        int[] spot = findSpot(fw, fd, 1, false);
        if (spot == null && rx > 2) {
            rx = 2;
            rz = 2;
            fw = 7;
            fd = 7;
            spot = findSpot(fw, fd, 1, false);
        }
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        double cx = x0 + (fw - 1) / 2.0, cz = z0 + (fd - 1) / 2.0;
        boolean[][] water = new boolean[fw][fd];
        for (int x = 0; x < fw; x++)
            for (int z = 0; z < fd; z++) {
                double dx = (x0 + x - cx) / (rx + 0.35 + rng.nextDouble() * 0.45), dz = (z0 + z - cz) / (rz + 0.35 + rng.nextDouble() * 0.45);
                water[x][z] = dx * dx + dz * dz <= 1.0 && x > 0 && z > 0 && x < fw - 1 && z < fd - 1;
            }
        if (oasis) { // Palmen vor mark(): der Stamm braucht noch unreservierte Zellen
            palm(x0 + 1, z0 + fd - 1, 5 + rng.nextInt(2));
            palm(x0 + fw - 2, z0 + 1, 5 + rng.nextInt(2));
        }
        mark(x0, z0, fw, fd);
        List<String> rimMix = oasis ? List.of("sand", "sand", "sandstone", "smooth_sandstone")
            : style == Palette.Style.JAPANESE ? List.of("stone", "andesite", "mossy_cobblestone", "gravel")
            : List.of("mossy_cobblestone", "stone", "gravel", "cobblestone", "clay", "mud");
        for (int x = 0; x < fw; x++)
            for (int z = 0; z < fd; z++) {
                int ax = x0 + x, az = z0 + z;
                if (!v.isEmpty(ax, 1, az)) continue; // Palmenstamm o. ä. nicht überschreiben
                if (water[x][z]) {
                    set(ax, 0, az, B.id("water"));
                    if (rng.chance(0.3)) set(ax, 1, az, B.id("lily_pad"));
                    continue;
                }
                boolean adj = false, orth = false;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= fw || nz >= fd || !water[nx][nz]) continue;
                        adj = true;
                        if (dx == 0 || dz == 0) orth = true;
                    }
                if (!adj) continue;
                double r = rng.nextDouble();
                if (r < 0.32) {
                    set(ax, 0, az, B.id(rng.pick(rimMix)));
                } else if (r < 0.50 && orth) {
                    int hgt = 1 + rng.nextInt(3);
                    for (int y = 1; y <= hgt; y++) set(ax, y, az, B.id("sugar_cane"));
                } else if (r < 0.62) {
                    set(ax, 1, az, B.with("mossy_cobblestone_slab", "type", "bottom"));
                } else if (r < 0.80 && grassy(ax, az)) {
                    set(ax, 1, az, B.id(rng.pick(List.of("fern", "short_grass", "blue_orchid", "short_grass"))));
                } else if (oasis && r < 0.82) {
                    set(ax, 1, az, B.id("dead_bush"));
                }
            }
        // Steg bei breiten Teichen in Holzstilen
        if (!oasis && rx >= 3 && (rural() || style == Palette.Style.JAPANESE)) {
            int bz = (int) Math.round(cz);
            for (int x = x0; x < x0 + fw; x++) {
                set(x, 1, bz, B.slab(p.floor(), false));
                for (int y = 2; y <= 4; y++) if (nameOf(v.get(x, y, bz)).equals("sugar_cane")) set(x, y, bz, B.AIR);
                for (int dz : new int[] {-1, 1}) {
                    int rz2 = bz + dz;
                    if (water[x - x0][rz2 - z0] && (x - x0) % 2 == 0) set(x, 1, rz2, fenceB());
                }
            }
        }
        // Bank oder Palme am Ufer
        if (!oasis) {
            int[] acc = accessCell(x0, z0, fw, fd);
            if (acc != null) targets.add(acc);
        }
    }

    // ------------------------------------------------------------------ Brunnen, Sitzgruppen, Kisten

    private void well() {
        if (v.h < 8) return;
        int[] spot = findSpot(3, 3, 1, false);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, 3, 3);
        String stone = B.id(rng.pick(List.of(p.foundation(), p.foundation(), "stone_bricks", "cobblestone")));
        if (sandy()) stone = B.id("sandstone");
        Materials.Family roof = p.roof();
        for (int x = x0; x < x0 + 3; x++)
            for (int z = z0; z < z0 + 3; z++) {
                boolean center = x == x0 + 1 && z == z0 + 1;
                if (center) {
                    set(x, 0, z, B.id("water"));
                    continue;
                }
                set(x, 0, z, stone);
                set(x, 1, z, rng.chance(0.2) ? B.id("mossy_cobblestone") : stone);
            }
        for (int[] c : new int[][] {{x0, z0}, {x0 + 2, z0}, {x0, z0 + 2}, {x0 + 2, z0 + 2}}) {
            set(c[0], 2, c[1], fenceB());
            set(c[0], 3, c[1], fenceB());
        }
        int cx = x0 + 1, cz = z0 + 1;
        for (int x = x0; x < x0 + 3; x++)
            for (int z = z0; z < z0 + 3; z++) {
                if (x == cx && z == cz) {
                    set(x, 4, z, B.id(roof.block()));
                    set(x, 5, z, B.slab(roof, false));
                    continue;
                }
                Dir in = x == x0 ? Dir.EAST : x == x0 + 2 ? Dir.WEST : z == z0 ? Dir.SOUTH : Dir.NORTH;
                set(x, 4, z, B.stairs(roof, in, false));
            }
        set(cx, 3, cz, B.lantern(p.light(), true));
        int[] acc = accessCell(x0, z0, 3, 3);
        if (acc != null) targets.add(acc);
    }

    private void gardenTable() {
        int[] spot = findSpot(4, 3, 1, true);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, 4, 3);
        String plate = B.id(B.name(p.fence()).replace("_fence", "_pressure_plate"));
        int tz = z0 + 1;
        for (int x = x0 + 1; x <= x0 + 2; x++) {
            set(x, 0, tz, B.id(sandy() ? "sandstone" : "coarse_dirt"));
            set(x, 1, tz, fenceB());
            double r = rng.nextDouble();
            set(x, 2, tz, r < 0.2 ? B.lantern(p.light(), false)
                : r < 0.4 ? B.id(rng.pick(List.of("potted_fern", "potted_azalea_bush", "potted_red_tulip", "potted_cornflower"))) : plate);
            set(x, 1, tz - 1, B.stairs(p.floor(), Dir.NORTH, false));
            set(x, 1, tz + 1, B.stairs(p.floor(), Dir.SOUTH, false));
        }
        set(x0, 1, tz, B.stairs(p.floor(), Dir.WEST, false));
        set(x0 + 3, 1, tz, B.stairs(p.floor(), Dir.EAST, false));
        // Rasen unter der Sitzgruppe als Kiesplatz
        for (int x = x0; x < x0 + 4; x++)
            for (int z = z0; z < z0 + 3; z++)
                if (grassy(x, z) && (x + z) % 2 == 0 && v.isEmpty(x, 1, z)) set(x, 0, z, B.id("gravel"));
        for (int x = x0 + 1; x <= x0 + 2; x++) set(x, 0, tz, B.id("coarse_dirt"));
        int[] acc = accessCell(x0, z0, 4, 3);
        if (acc != null) targets.add(acc);
    }

    private void crateCluster() {
        int[] spot = findSpot(2, 2, 1, true);
        if (spot == null) return;
        int x0 = spot[0], z0 = spot[1];
        mark(x0, z0, 2, 2);
        Dir[] hd = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
        for (int x = x0; x < x0 + 2; x++)
            for (int z = z0; z < z0 + 2; z++) {
                if (rng.chance(0.12)) continue;
                int stack = rng.chance(0.4) ? 2 : 1;
                for (int y = 1; y <= stack; y++) {
                    double r = rng.nextDouble();
                    String b;
                    if (r < 0.28) b = B.axis("hay_block", rng.pick(List.of('y', 'x', 'z')));
                    else if (r < 0.55) b = B.with("barrel", "facing", "up");
                    else if (r < 0.75) b = B.with("chest", "facing", rng.pick(List.of(hd)).id());
                    else if (r < 0.85) b = B.id("pumpkin");
                    else if (r < 0.93) b = B.with("composter", "level", String.valueOf(2 + rng.nextInt(6)));
                    else b = B.id("melon");
                    set(x, y, z, b);
                }
                if (rng.chance(0.18)) set(x, stack + 1, z, B.lantern(p.light(), false));
            }
    }

    private void benchByPath() {
        if (pathCells.isEmpty()) return;
        for (int t = 0; t < 40; t++) {
            int[] c = pathCells.get(rng.nextInt(pathCells.size()));
            Dir away = Dir.values()[rng.nextInt(4)];
            int bx = c[0] + away.dx, bz = c[1] + away.dz;
            int ax = away.wallAxis() == 'x' ? 1 : 0, az = 1 - ax; // Bank verläuft quer zur Wegrichtung
            boolean ok = true;
            for (int i = -1; i <= 1; i++) {
                int x = bx + i * ax, z = bz + i * az;
                if (!avail(x, z) || dist[x][z] <= 0) ok = false;
            }
            if (!ok) continue;
            for (int i = -1; i <= 1; i++) {
                int x = bx + i * ax, z = bz + i * az;
                set(x, 1, z, B.stairs(p.floor(), away, false));
                s.reserve(x, 1, z);
                occ[x][z] = true;
            }
            if (rng.chance(0.5)) {
                set(bx + 2 * ax, 1, bz + 2 * az, B.with("barrel", "facing", "up"));
                if (avail(bx + 2 * ax, bz + 2 * az)) occ[bx + 2 * ax][bz + 2 * az] = true;
                else set(bx + 2 * ax, 1, bz + 2 * az, B.AIR);
            }
            recomputeDist();
            return;
        }
    }

    // ------------------------------------------------------------------ Laternen, Hecken, Büsche

    private void postLantern(int x, int z, boolean stonePost) {
        if (!v.in(x, 3, z)) return;
        String post = stonePost ? B.id(stoneWall()) : fenceB();
        set(x, 1, z, post);
        set(x, 2, z, post);
        set(x, 3, z, B.lantern(p.light(), false));
        s.reserve(x, 1, z);
        occ[x][z] = true;
    }

    private String stoneWall() {
        if (sandy()) return "sandstone_wall";
        return switch (style) {
            case GOTHIC -> "polished_blackstone_wall";
            case JAPANESE, MODERN, CLASSICAL, VICTORIAN -> "stone_brick_wall";
            default -> "cobblestone_wall";
        };
    }

    private void pathLights(int count) {
        if (pathCells.isEmpty()) return;
        boolean stonePost = sandy() || style == Palette.Style.JAPANESE || style == Palette.Style.GOTHIC || formal();
        List<int[]> posts = new ArrayList<>();
        for (int t = 0; t < 120 && posts.size() < count; t++) {
            int[] c = pathCells.get(rng.nextInt(pathCells.size()));
            Dir dir = Dir.values()[rng.nextInt(4)];
            int x = c[0] + dir.dx, z = c[1] + dir.dz;
            if (!avail(x, z)) continue;
            boolean far = true;
            for (int[] o : posts) if (Math.abs(o[0] - x) + Math.abs(o[1] - z) < 6) far = false;
            if (!far) continue;
            postLantern(x, z, stonePost);
            posts.add(new int[] {x, z});
        }
    }

    private String leafForHedge() {
        double r = rng.nextDouble();
        String base = p.leaves();
        if (style == Palette.Style.JAPANESE) return B.leaves(r < 0.3 ? "flowering_azalea_leaves" : "azalea_leaves");
        if (B.name(base).startsWith("spruce") || B.name(base).startsWith("acacia") || B.name(base).startsWith("cherry")) return B.leaves(base);
        if (r < 0.10) return B.leaves("azalea_leaves");
        if (r < 0.18) return B.leaves("flowering_azalea_leaves");
        return B.leaves(B.name(base).startsWith("flowering") || B.name(base).startsWith("azalea") ? "oak_leaves" : base);
    }

    /** Hecken: dicht hinter dem Zaun am Grundstücksrand (nur dort, wo ein Zaun steht). */
    private void hedges() {
        if (sandy()) return;
        int tall = formal() || style == Palette.Style.JAPANESE ? 2 : 1;
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) continue;
                String f = v.get(x, 1, z);
                if (f == null || !B.name(f).endsWith("_fence")) continue;
                int nx = x == 0 ? 1 : x == w - 1 ? w - 2 : x, nz = z == 0 ? 1 : z == d - 1 ? d - 2 : z;
                if (!avail(nx, nz) || rng.chance(0.12)) continue;
                set(nx, 1, nz, leafForHedge());
                if (tall == 2 && rng.chance(0.55) && v.isEmpty(nx, 2, nz)) set(nx, 2, nz, leafForHedge());
                occ[nx][nz] = true;
                s.reserve(nx, 1, nz);
            }
        recomputeDist();
    }

    private void bushes(int count) {
        for (int i = 0; i < count; i++) {
            int[] spot = findSpot(1, 1, 0, true);
            if (spot == null) return;
            int x = spot[0], z = spot[1];
            Dir dir = Dir.values()[rng.nextInt(4)];
            boolean two = avail(x + dir.dx, z + dir.dz) && rng.chance(0.4);
            bush(x, z);
            occ[x][z] = true;
            s.reserve(x, 1, z);
            if (two) {
                bush(x + dir.dx, z + dir.dz);
                occ[x + dir.dx][z + dir.dz] = true;
                s.reserve(x + dir.dx, 1, z + dir.dz);
            }
        }
        recomputeDist();
    }

    private void bush(int x, int z) {
        if (!v.isEmpty(x, 1, z)) return;
        double r = rng.nextDouble();
        if (r < 0.22) {
            set(x, 1, z, B.with("sweet_berry_bush", "age", String.valueOf(2 + rng.nextInt(2))));
            return;
        }
        String leaf = r < 0.45 ? B.leaves("flowering_azalea_leaves") : r < 0.65 ? B.leaves("azalea_leaves") : leafForHedge();
        set(x, 1, z, leaf);
        if (rng.chance(0.4) && v.isEmpty(x, 2, z)) set(x, 2, z, leaf);
    }

    private void cacti(int count) {
        for (int i = 0; i < count; i++) {
            int[] spot = findSpot(1, 1, 0, false);
            if (spot == null) return;
            int x = spot[0], z = spot[1];
            if (!nameOf(v.get(x, 0, z)).endsWith("sand")) continue;
            boolean clear = true;
            for (Dir dir : Dir.values())
                for (int y = 1; y <= 4; y++) if (!v.isEmpty(x + dir.dx, y, z + dir.dz)) clear = false;
            if (!clear) continue;
            if (rng.chance(0.35)) {
                set(x, 1, z, B.id("dead_bush"));
            } else {
                int hgt = 1 + rng.nextInt(3);
                for (int y = 1; y <= hgt; y++) set(x, y, z, B.id("cactus"));
            }
            occ[x][z] = true;
            s.reserve(x, 1, z);
        }
    }

    /** Hohes Gras, Farne und kleine Pilzgruppen in Flecken. */
    private void meadow(double f) {
        if (sandy()) return;
        boolean wet = style == Palette.Style.NORDIC || style == Palette.Style.VIKING || style == Palette.Style.GOTHIC;
        int patches = (int) Math.round(Math.max(2, f * availCount / 28.0));
        for (int i = 0; i < patches; i++) {
            int[] spot = findSpot(1, 1, 0, false);
            if (spot == null) return;
            int cx = spot[0], cz = spot[1];
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cx + dx, z = cz + dz;
                    if (!avail(x, z) || !grassy(x, z) || !v.isEmpty(x, 1, z) || !rng.chance(dx == 0 && dz == 0 ? 1.0 : 0.55)) continue;
                    double r = rng.nextDouble();
                    if (r < 0.34 && v.isEmpty(x, 2, z)) {
                        String t = wet && rng.chance(0.5) ? "large_fern" : "tall_grass";
                        set(x, 1, z, B.with(t, "half", "lower"));
                        set(x, 2, z, B.with(t, "half", "upper"));
                    } else if (r < 0.34 + (wet ? 0.34 : 0.14)) {
                        set(x, 1, z, B.id("fern"));
                    } else if (r < 0.9) {
                        set(x, 1, z, B.id("short_grass"));
                    } else if (style == Palette.Style.FANTASY || style == Palette.Style.GOTHIC) {
                        set(x, 1, z, B.id(rng.chance(0.5) ? "red_mushroom" : "brown_mushroom"));
                    } else {
                        set(x, 1, z, B.id(rng.pick(SHORT_FLOWERS)));
                    }
                }
        }
    }

    // ------------------------------------------------------------------ Bäume

    private void trees(int count) {
        for (int i = 0; i < count; i++) {
            boolean big = rng.chance(0.55);
            int[] spot = findSpot(1, 1, big ? 3 : 2, false);
            if (spot == null) return;
            if (sandy() && !nameOf(v.get(spot[0], 0, spot[1])).endsWith("sand")) continue;
            tree(s, spot[0], spot[1], 3 + rng.nextInt(3));
            mark(spot[0] - 1, spot[1] - 1, 3, 3);
        }
    }

    private enum Kind { OAK, BIRCH, SPRUCE, CHERRY, ORCHARD, BLOSSOM, DARK_OAK, DEAD, PALM, ACACIA }

    private static Kind pickKind(Site s) {
        Rng rng = s.rng;
        List<Kind> k = new ArrayList<>();
        switch (s.spec.style()) {
            case NORDIC, VIKING -> add(k, Kind.SPRUCE, 6, Kind.BIRCH, 2, Kind.OAK, 1, Kind.DARK_OAK, 1);
            case JAPANESE -> add(k, Kind.CHERRY, 6, Kind.SPRUCE, 1, Kind.BLOSSOM, 1);
            case FANTASY -> add(k, Kind.BLOSSOM, 3, Kind.CHERRY, 3, Kind.BIRCH, 1, Kind.DARK_OAK, 1);
            case GOTHIC -> add(k, Kind.DEAD, 4, Kind.SPRUCE, 3, Kind.DARK_OAK, 1);
            case DESERT, ORIENTAL -> add(k, Kind.PALM, 6, Kind.ACACIA, 2);
            case MODERN -> add(k, Kind.BIRCH, 5, Kind.CHERRY, 1, Kind.OAK, 1);
            case CLASSICAL, VICTORIAN -> add(k, Kind.OAK, 4, Kind.BIRCH, 2, Kind.ORCHARD, 1, Kind.CHERRY, 1);
            case RUSTIC -> add(k, Kind.OAK, 4, Kind.ORCHARD, 2, Kind.BIRCH, 2, Kind.DARK_OAK, 1, Kind.SPRUCE, 1);
            default -> add(k, Kind.OAK, 4, Kind.BIRCH, 2, Kind.ORCHARD, 1, Kind.DARK_OAK, 1, Kind.SPRUCE, 1);
        }
        return rng.pick(k);
    }

    private static void add(List<Kind> out, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2)
            for (int n = 0; n < (Integer) pairs[i + 1]; n++) out.add((Kind) pairs[i]);
    }

    /** Baum je nach Stil in zufälliger Art (Eiche, Birke, Fichte, Kirsche, Obstbaum, Blütenbaum, Palme ...). */
    static void tree(Site s, int x, int z, int trunk) {
        Kind kind = pickKind(s);
        trunk = Math.max(3, Math.min(trunk, 5));
        switch (kind) {
            case OAK -> roundTree(s, x, z, trunk, "oak_log", "oak_leaves", null, 2);
            case ORCHARD -> roundTree(s, x, z, trunk - 1 < 3 ? 3 : trunk - 1, "oak_log", "oak_leaves", "flowering_azalea_leaves", 2);
            case BLOSSOM -> roundTree(s, x, z, trunk, "oak_log", "flowering_azalea_leaves", "azalea_leaves", 2);
            case BIRCH -> birch(s, x, z, trunk);
            case SPRUCE -> spruce(s, x, z, trunk);
            case CHERRY -> cherry(s, x, z, trunk);
            case DARK_OAK -> darkOak(s, x, z, trunk);
            case DEAD -> dead(s, x, z, trunk);
            case PALM -> palm(s, x, z, trunk + 2);
            case ACACIA -> acacia(s, x, z, trunk);
        }
    }

    private void palm(int x, int z, int height) {
        palm(s, x, z, height);
    }

    private static void leaf(Site s, int x, int y, int z, String leaf) {
        if (s.v.in(x, y, z) && s.free(x, y, z)) s.v.set(x, y, z, leaf);
    }

    private static boolean trunkFree(Site s, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) if (!s.free(x, y, z)) return false;
        return true;
    }

    /** Scheibe aus Laub mit Radius r (Ecken teilweise ausgespart). */
    private static void disc(Site s, int cx, int y, int cz, int r, String leaf, String alt, double cornerDrop) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                if (corner && r >= 1 && s.rng.chance(cornerDrop)) continue;
                String l = alt != null && s.rng.chance(0.15) ? B.leaves(alt) : leaf;
                leaf(s, cx + dx, y, cz + dz, l);
            }
    }

    private static void roundTree(Site s, int x, int z, int trunk, String log, String leaves, String alt, int r) {
        if (trunk + 3 >= s.h || !trunkFree(s, x, z, 1, trunk)) return;
        for (int y = 1; y <= trunk + 1; y++) if (s.free(x, y, z) || y > trunk) s.v.set(x, y, z, B.axis(log, 'y'));
        String l = B.leaves(leaves);
        disc(s, x, trunk - 1, z, 1, l, alt, 0.7);
        disc(s, x, trunk, z, r, l, alt, 0.55);
        disc(s, x, trunk + 1, z, r, l, alt, 0.4);
        disc(s, x, trunk + 2, z, r - 1, l, alt, 0.5);
        // Äste: seitliche Stämme mit Laubklumpen
        if (s.rng.chance(0.5)) {
            Dir dir = Dir.values()[s.rng.nextInt(4)];
            int bx = x + dir.dx, bz = z + dir.dz;
            if (s.free(bx, trunk - 1, bz)) s.v.set(bx, trunk - 1, bz, B.axis(log, dir.wallAxis() == 'x' ? 'z' : 'x'));
        }
    }

    private static void birch(Site s, int x, int z, int trunk) {
        int tr = trunk + 1;
        if (tr + 3 >= s.h || !trunkFree(s, x, z, 1, tr)) return;
        for (int y = 1; y <= tr + 1; y++) s.v.set(x, y, z, B.axis("birch_log", 'y'));
        String l = B.leaves("birch_leaves");
        disc(s, x, tr - 2, z, 1, l, null, 0.8);
        disc(s, x, tr - 1, z, 2, l, null, 0.9);
        disc(s, x, tr, z, 2, l, null, 0.75);
        disc(s, x, tr + 1, z, 1, l, null, 0.3);
        leaf(s, x, tr + 2, z, l);
    }

    private static void spruce(Site s, int x, int z, int trunk) {
        int tall = Math.min(s.h - 3, trunk + 3 + s.rng.nextInt(3));
        if (tall < 5 || !trunkFree(s, x, z, 1, tall)) return;
        for (int y = 1; y <= tall; y++) s.v.set(x, y, z, B.axis("spruce_log", 'y'));
        String l = B.leaves("spruce_leaves");
        leaf(s, x, tall + 1, z, l);
        for (int y = tall; y >= 3; y--) {
            int i = tall - y;
            int tier = Math.min(3, (i + 1) / 2);
            boolean wide = i % 2 == 1;
            int rad = i == 0 ? 0 : wide ? tier : Math.max(1, tier - 1);
            if (i == 0) {
                leaf(s, x, y, z, l);
                continue;
            }
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
                    if (corner && (rad >= 2 || !wide)) continue;
                    leaf(s, x + dx, y, z + dz, l);
                }
        }
    }

    private static void cherry(Site s, int x, int z, int trunk) {
        int tr = trunk + 1;
        if (tr + 4 >= s.h || !trunkFree(s, x, z, 1, tr)) return;
        for (int y = 1; y <= tr; y++) s.v.set(x, y, z, B.axis("cherry_log", 'y'));
        // Stamm neigt sich: ein Versatz nach oben
        Dir lean = Dir.values()[s.rng.nextInt(4)];
        int tx = x, tz = z;
        if (s.free(x + lean.dx, tr, z + lean.dz) && s.free(x + lean.dx, tr + 1, z + lean.dz)) {
            tx = x + lean.dx;
            tz = z + lean.dz;
            s.v.set(tx, tr, tz, B.axis("cherry_log", 'y'));
            s.v.set(tx, tr + 1, tz, B.axis("cherry_log", 'y'));
        } else {
            s.v.set(x, tr + 1, z, B.axis("cherry_log", 'y'));
        }
        String l = B.leaves("cherry_leaves");
        disc(s, tx, tr - 1, tz, 2, l, null, 0.8);
        disc(s, tx, tr, tz, 3, l, null, 0.8);
        disc(s, tx, tr + 1, tz, 3, l, null, 0.7);
        disc(s, tx, tr + 2, tz, 2, l, null, 0.5);
        disc(s, tx, tr + 3, tz, 1, l, null, 0.6);
        // Blütenblätter am Boden
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                int px = tx + dx, pz = tz + dz;
                if (s.rng.chance(0.18) && s.free(px, 1, pz) && "grass_block".equals(nameOfStatic(s.v.get(px, 0, pz)))) s.v.set(px, 1, pz, B.id("pink_petals"));
            }
    }

    private static String nameOfStatic(String b) {
        return b == null ? "" : B.name(b);
    }

    private static void darkOak(Site s, int x, int z, int trunk) {
        int tr = trunk + 1;
        if (tr + 4 >= s.h || x + 1 >= s.w || z + 1 >= s.d) {
            roundTree(s, x, z, trunk, "dark_oak_log", "dark_oak_leaves", null, 2);
            return;
        }
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++)
                if (!trunkFree(s, x + dx, z + dz, 1, tr)) {
                    roundTree(s, x, z, trunk, "dark_oak_log", "dark_oak_leaves", null, 2);
                    return;
                }
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++)
                for (int y = 1; y <= tr; y++) s.v.set(x + dx, y, z + dz, B.axis("dark_oak_log", 'y'));
        String l = B.leaves("dark_oak_leaves");
        for (int[] layer : new int[][] {{tr - 1, 2}, {tr, 3}, {tr + 1, 3}, {tr + 2, 2}}) {
            for (int dx = -layer[1]; dx <= layer[1] + 1; dx++)
                for (int dz = -layer[1]; dz <= layer[1] + 1; dz++) {
                    boolean corner = (dx == -layer[1] || dx == layer[1] + 1) && (dz == -layer[1] || dz == layer[1] + 1);
                    if (corner && s.rng.chance(0.75)) continue;
                    leaf(s, x + dx, layer[0], z + dz, l);
                }
        }
    }

    private static void dead(Site s, int x, int z, int trunk) {
        int tr = trunk + 1 + s.rng.nextInt(2);
        if (tr + 1 >= s.h || !trunkFree(s, x, z, 1, tr)) return;
        String log = s.rng.chance(0.5) ? "dark_oak_log" : "spruce_log";
        for (int y = 1; y <= tr; y++) s.v.set(x, y, z, B.axis(log, 'y'));
        int branches = 2 + s.rng.nextInt(2);
        for (int i = 0; i < branches; i++) {
            Dir dir = Dir.values()[s.rng.nextInt(4)];
            int y = 2 + s.rng.nextInt(Math.max(1, tr - 1));
            int len = 1 + s.rng.nextInt(2);
            for (int k = 1; k <= len; k++) {
                int bx = x + dir.dx * k, bz = z + dir.dz * k;
                if (s.free(bx, y, bz)) s.v.set(bx, y, bz, B.axis(log, dir.wallAxis() == 'x' ? 'z' : 'x'));
                if (k == len && s.free(bx, y + 1, bz) && s.rng.chance(0.6)) s.v.set(bx, y + 1, bz, B.axis(log, 'y'));
            }
        }
    }

    static void palm(Site s, int x, int z, int height) {
        int hgt = Math.max(5, Math.min(height, s.h - 3));
        if (!trunkFree(s, x, z, 1, hgt - 2)) return;
        Dir lean = Dir.values()[s.rng.nextInt(4)];
        int cx = x, cz = z;
        for (int y = 1; y <= hgt; y++) {
            if (y == hgt / 2 || y == hgt * 3 / 4) {
                int nx = cx + lean.dx, nz = cz + lean.dz;
                if (s.free(nx, y, nz)) {
                    cx = nx;
                    cz = nz;
                }
            }
            if (!s.free(cx, y, cz)) return;
            s.v.set(cx, y, cz, B.axis("jungle_log", 'y'));
        }
        String l = B.leaves("jungle_leaves");
        leaf(s, cx, hgt + 1, cz, l);
        for (Dir dir : Dir.values()) {
            leaf(s, cx + dir.dx, hgt + 1, cz + dir.dz, l);
            leaf(s, cx + dir.dx * 2, hgt, cz + dir.dz * 2, l);
            leaf(s, cx + dir.dx * 3, hgt - 1, cz + dir.dz * 3, l);
        }
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) {
                leaf(s, cx + dx, hgt, cz + dz, l);
                leaf(s, cx + dx * 2, hgt - 1, cz + dz * 2, l);
            }
    }

    private static void acacia(Site s, int x, int z, int trunk) {
        int tr = trunk + 1;
        if (tr + 4 >= s.h || !trunkFree(s, x, z, 1, tr)) return;
        for (int y = 1; y <= tr; y++) s.v.set(x, y, z, B.axis("acacia_log", 'y'));
        Dir dir = Dir.values()[s.rng.nextInt(4)];
        int tx = x + dir.dx, tz = z + dir.dz;
        if (s.free(tx, tr + 1, tz)) s.v.set(tx, tr + 1, tz, B.axis("acacia_log", 'y'));
        String l = B.leaves("acacia_leaves");
        disc(s, tx, tr + 2, tz, 3, l, null, 0.9);
        disc(s, tx, tr + 3, tz, 1, l, null, 0.3);
        // zweiter Ast zur Gegenseite
        int ox = x - dir.dx, oz = z - dir.dz;
        if (s.free(ox, tr, oz) && s.rng.chance(0.7)) {
            s.v.set(ox, tr, oz, B.axis("acacia_log", 'y'));
            disc(s, ox, tr + 1, oz, 2, l, null, 0.9);
        }
    }
}
