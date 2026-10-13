package de.localai.baumeister.arch;

import java.util.List;

/**
 * Fassaden- und Vorbau-Details für kleine und mittlere Wohnhäuser (Fachwerk-/Holzstile): Steinplatte mit breiter
 * Stufe, überdachter Vorbau auf Holzpfosten mit Steinpfeilern und Zaunbrüstung, Pflanzkübel neben der Tür,
 * hängende Laternen, Fensterrahmen/-kästen, Ranken sowie ein Steinsockel-Streifen rund ums Haus.
 * Reine Engine-Klasse: keine Minecraft-Importe, Blöcke sind Zustandstexte.
 */
final class FacadeDetail {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final Rng rng;
    private final BuildingSpec spec;
    private final boolean[][][] lev;
    private final int maxK, fh, W, D;
    private final String frameB;
    private final int doorX, doorZ;
    private final Dir o;
    private final int ax, az;
    private final String pil, pilWall;
    private int built; // Tiefe des gebauten Vorbaus (0 = keiner)

    FacadeDetail(Site site, boolean[][][] lev, int maxK, int fh, String frameB, String groundWallB, int doorX, int doorZ, Dir doorDir) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.rng = new Rng(site.spec.seed() * 31 + 11);
        this.spec = site.spec;
        this.lev = lev;
        this.maxK = maxK;
        this.fh = fh;
        this.W = site.w;
        this.D = site.d;
        this.frameB = frameB;
        this.doorX = doorX;
        this.doorZ = doorZ;
        this.o = doorDir;
        this.ax = doorDir.wallAxis() == 'x' ? 1 : 0;
        this.az = 1 - ax;
        String[] pair = stonePair(groundWallB, p.foundation());
        this.pil = pair[0];
        this.pilWall = pair[1];
    }

    /** Welche Stile die Fassadendetails bekommen (Holz-/Fachwerkhäuser, keine Stein- und Festungsbauten). */
    static boolean applies(Palette.Style st) {
        return switch (st) {
            case MEDIEVAL, RUSTIC, NORDIC, VIKING, VICTORIAN -> true;
            default -> false;
        };
    }

    /** Wie {@link #applies(Palette.Style)}, aber ohne Steinwände (gleiche Regel wie in {@link FreeBuilder}). */
    static boolean applies(Palette.Style st, String wallBlock) {
        if (!applies(st)) return false;
        String n = B.name(wallBlock);
        return !(n.contains("stone") || n.contains("cobble") || n.contains("deepslate") || n.contains("andesite")
            || n.contains("diorite") || n.contains("granite") || n.contains("calcite") || n.contains("quartz")
            || n.contains("sandstone") || n.contains("blackstone") || n.contains("tuff"));
    }

    /** Zweiter Dachbaustoff für Zierstreifen (Holzdach: Sandstein, Steindach: Steinziegel); null = keiner. */
    static Materials.Family bandFor(Materials.Family roof) {
        String k = roof.key();
        if (k.contains("copper") || k.contains("sandstone") || k.contains("quartz") || k.equals("bamboo")) return null;
        boolean wood = roof.block().endsWith("_planks");
        if (wood) return Materials.family(k.equals("birch") || k.equals("pale_oak") ? "stone_brick" : "sandstone");
        if (k.contains("stone_brick")) return Materials.family("cobblestone");
        return Materials.family("stone_brick");
    }

    /** Jede dritte Stufenreihe eines Dachs (ab Höhe yt+1) auf die Zweitfamilie umstellen; Rechteck inkl. Überstand. */
    static void roofBands(Voxels v, Palette p, int x0, int z0, int x1, int z1, int yt) {
        Materials.Family band = bandFor(p.roof());
        if (band == null) return;
        String from = B.id(p.roof().stairs()) + "[", to = B.id(band.stairs());
        for (int i = 2; yt + 1 + i < v.h; i += 3) {
            int y = yt + 1 + i;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    String c = v.get(x, y, z);
                    if (c != null && c.startsWith(from)) v.set(x, y, z, to + c.substring(from.length() - 1));
                }
        }
    }

    /**
     * Kleine Fassadendetails für den klassischen Rechteckbau ({@link HouseGenerator}, Front = Norden): Steinstreifen
     * rund ums Haus, breite Eingangsstufe und Pflanzkübel (Fass + Strauch/Blumentopf) beidseits der Tür.
     */
    static void houseExtras(Site s, int bx0, int bz0, int bx1, int bz1, int doorX, int doorW, boolean porch) {
        Voxels v = s.v;
        Rng rng = new Rng(s.spec.seed() * 17 + 5);
        String[] pair = stonePair(s.p.groundWall(), s.p.foundation());
        for (int x = bx0 - 1; x <= bx1 + 1; x++)
            for (int z = bz0 - 1; z <= bz1 + 1; z++) {
                boolean inside = x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1;
                if (inside || !v.in(x, 0, z)) continue;
                String g = B.name(v.get(x, 0, z));
                if (g.equals("grass_block") || g.equals("sand")) v.set(x, 0, z, B.id(rng.chance(0.2) ? "cobblestone" : pair[0]));
            }
        if (bz0 >= 2) {
            int zs = porch ? bz0 - 3 : bz0 - 1;
            for (int z = zs; z >= Math.max(0, zs - 1); z--) {
                int ext = z == zs ? 1 : 0;
                for (int x = doorX - ext; x < doorX + doorW + ext; x++) {
                    if (v.in(x, 0, z)) v.set(x, 0, z, B.id(rng.chance(0.2) ? "cobblestone" : pair[0]));
                }
            }
        }
        if (bz0 >= 1) {
            List<String> bushes = List.of("flowering_azalea_leaves", s.p.leaves(), "azalea_leaves");
            boolean first = rng.chance(0.5);
            for (int sg = 0; sg < 2; sg++) {
                int x = sg == 0 ? doorX - 2 - (porch ? 1 : 0) : doorX + doorW + 1 + (porch ? 1 : 0), z = bz0 - 1;
                if (!v.in(x, 2, z) || !v.isEmpty(x, 1, z) || !v.isEmpty(x, 2, z) || s.isReserved(x, 1, z)) continue;
                v.set(x, 1, z, B.with("barrel", "facing", "up", "open", "false"));
                v.set(x, 2, z, (sg == 0) == first ? B.leaves(B.id(rng.pick(bushes))) : B.id(rng.pick(POTS)));
            }
        }
    }

    /** Steinblock + passende Mauer: aus Sockelmaterial gewählt, sonst Bruchstein. */
    private static String[] stonePair(String... candidates) {
        for (String c : candidates) {
            String n = B.name(c);
            switch (n) {
                case "cobblestone": return new String[] {"cobblestone", "cobblestone_wall"};
                case "mossy_cobblestone": return new String[] {"mossy_cobblestone", "mossy_cobblestone_wall"};
                case "stone_bricks": return new String[] {"stone_bricks", "stone_brick_wall"};
                case "mossy_stone_bricks": return new String[] {"mossy_stone_bricks", "mossy_stone_brick_wall"};
                case "bricks": return new String[] {"bricks", "brick_wall"};
                case "cobbled_deepslate": return new String[] {"cobbled_deepslate", "cobbled_deepslate_wall"};
                case "deepslate_bricks": return new String[] {"deepslate_bricks", "deepslate_brick_wall"};
                case "sandstone": return new String[] {"sandstone", "sandstone_wall"};
                default: break;
            }
        }
        return new String[] {"cobblestone", "cobblestone_wall"};
    }

    private boolean inLev(int k, int x, int z) {
        return k >= 0 && k < maxK && x >= 0 && z >= 0 && x < W && z < D && lev[k][x][z];
    }

    private boolean inLevAny(int x, int z) {
        for (int k = 0; k < maxK; k++) if (inLev(k, x, z)) return true;
        return false;
    }

    private int openMask(int k, int x, int z) {
        int m = 0;
        for (Dir d : Dir.values()) if (!inLev(k, x + d.dx, z + d.dz)) m |= 1 << d.ordinal();
        return m;
    }

    private String nameAt(int x, int y, int z) {
        String c = v.get(x, y, z);
        return c == null ? "" : B.name(c);
    }

    private int cx(int dist, int w) {
        return doorX + o.dx * dist + w * ax;
    }

    private int cz(int dist, int w) {
        return doorZ + o.dz * dist + w * az;
    }

    private Dir lat() {
        return ax == 1 ? Dir.EAST : Dir.SOUTH;
    }

    // ------------------------------------------------------------------ Vorbau

    /** Baut den Vorbau vor der Tür; false, wenn nirgends Platz ist. */
    boolean porch() {
        if (doorX < 0) return false;
        int cy = Math.max(3, fh - 1); // eine Ebene unter der Traufe (Sparren liegen auf y = fh)
        int[][] tries = {{4, 3}, {3, 3}, {3, 2}, {2, 2}};
        for (int[] t : tries) {
            if (fits(t[0], t[1], cy)) {
                build(t[0], t[1], cy);
                built = t[1];
                return true;
            }
        }
        return false;
    }

    /** Wandfackeln und Fensterläden/-kästen vor der Tür weichen dem Vorbau. */
    private static boolean removable(String n) {
        return n.equals("wall_torch") || n.endsWith("_trapdoor") || n.startsWith("potted_");
    }

    private boolean fits(int hw, int pd, int cy) {
        for (int dist = 1; dist <= pd; dist++)
            for (int w = -hw; w <= hw; w++) {
                int x = cx(dist, w), z = cz(dist, w);
                if (!v.in(x, cy, z) || inLevAny(x, z) || s.isReserved(x, 1, z)) return false;
                String deck = v.get(x, cy, z);
                if (deck != null && !B.isAir(deck) && !removable(B.name(deck))) return false; // Dach/Traufe nicht überbauen
                for (int y = 1; y < cy; y++) {
                    String at = v.get(x, y, z);
                    if (at == null || B.isAir(at) || removable(B.name(at))) continue;
                    return false;
                }
            }
        return true;
    }

    private void build(int hw, int pd, int cy) {
        String fence = B.id(p.fence());
        Materials.Family roof = p.roof();
        Materials.Family band = bandFor(roof);
        char latAxis = ax == 1 ? 'x' : 'z', depthAxis = ax == 1 ? 'z' : 'x';
        // Plattform aus gemischtem Steinpflaster, Wandfackeln der Tür weichen
        for (int dist = 1; dist <= pd; dist++)
            for (int w = -hw; w <= hw; w++) {
                int x = cx(dist, w), z = cz(dist, w);
                v.set(x, 0, z, paving());
                for (int y = 1; y <= cy; y++) {
                    if (removable(nameAt(x, y, z))) v.set(x, y, z, B.AIR);
                    if (y < cy) s.reserve(x, y, z);
                }
            }
        // vordere Reihe: Öffnung (3 breit), Holzpfosten auf Steinsockel, Zaun, Steinpfeiler an den Ecken
        for (int w = -hw; w <= hw; w++) {
            int a = Math.abs(w), x = cx(pd, w), z = cz(pd, w);
            if (a <= 1) continue;
            if (a == 2) {
                v.set(x, 1, z, B.id(pil));
                for (int y = 2; y < cy; y++) v.set(x, y, z, B.axis(frameB, 'y'));
            } else if (a < hw) {
                v.set(x, 1, z, fence);
            } else {
                v.set(x, 1, z, B.id(pil));
                v.set(x, 2, z, wallState(B.id(pilWall)));
                if (cy - 1 > 2) v.set(x, cy - 1, z, B.axis(frameB, latAxis));
            }
        }
        // Seitenbrüstung
        for (int dist = 1; dist < pd; dist++)
            for (int sg : new int[] {-1, 1}) v.set(cx(dist, sg * hw), 1, cz(dist, sg * hw), fence);
        // sichtbare Sparrenreihe unter dem Dach: abwechselnd Balkenkopf und Klappe
        for (int w = -hw; w <= hw; w++) {
            int a = Math.abs(w), x = cx(pd, w), z = cz(pd, w);
            if (a == 2 && cy - 1 >= 2) continue; // Pfosten reicht bis unters Dach
            if (a == hw && hw >= 3) continue; // Eckpfeiler hat seinen eigenen Abschluss
            v.set(x, cy - 1, z, (w & 1) == 0 ? B.axis(frameB, latAxis) : B.trapdoor(p.trapdoor(), o, true, false));
        }
        for (int dist = 1; dist < pd; dist++)
            for (int sg : new int[] {-1, 1}) {
                int x = cx(dist, sg * hw), z = cz(dist, sg * hw);
                v.set(x, cy - 1, z, (dist & 1) == 1 ? B.axis(frameB, depthAxis) : B.trapdoor(p.trapdoor(), o, true, false));
            }
        // Dach: Pultdach aus Vollblöcken mit Zierstreifen und abgeschrägter Traufkante
        Dir toHouse = o.opposite();
        for (int dist = 1; dist <= pd; dist++)
            for (int w = -hw; w <= hw; w++) {
                String deck = (dist == 2 && band != null) ? B.id(band.block()) : B.id(roof.block());
                v.set(cx(dist, w), cy, cz(dist, w), deck);
            }
        for (int w = -hw - 1; w <= hw + 1; w++) {
            Dir f = toHouse;
            if (w == -hw - 1) f = lat();
            else if (w == hw + 1) f = lat().opposite();
            setFree(cx(pd + 1, w), cy, cz(pd + 1, w), B.stairs(roof, f, false));
        }
        for (int dist = 1; dist <= pd; dist++)
            for (int sg : new int[] {-1, 1}) {
                Dir f = sg < 0 ? lat() : lat().opposite();
                setFree(cx(dist, sg * (hw + 1)), cy, cz(dist, sg * (hw + 1)), B.stairs(roof, f, false));
            }
        // hängende Laternen
        String lantern = B.lantern(p.light(), true);
        int ld = Math.max(1, pd - 1);
        for (int sg : new int[] {-1, 1}) {
            int x = cx(ld, sg), z = cz(ld, sg);
            if (v.isEmpty(x, cy - 1, z)) v.set(x, cy - 1, z, lantern);
        }
        // Pflanzkübel neben der Tür: Fass mit Strauch bzw. Blumentopf
        List<String> bushes = List.of("flowering_azalea_leaves", p.leaves(), "azalea_leaves");
        boolean first = rng.chance(0.5);
        for (int sg : new int[] {-1, 1}) {
            int x = cx(1, sg * 2), z = cz(1, sg * 2);
            v.set(x, 1, z, B.with("barrel", "facing", "up", "open", "false"));
            boolean bush = (sg < 0) == first;
            v.set(x, 2, z, bush ? B.leaves(B.id(rng.pick(bushes))) : B.id(rng.pick(POTS)));
        }
        // Ranken an den Pfosten
        if (pd >= 3) {
            for (int sg : new int[] {-1, 1}) {
                if (!rng.chance(0.75)) continue;
                int x = cx(pd - 1, sg * 2), z = cz(pd - 1, sg * 2);
                int len = 2 + rng.nextInt(2);
                for (int y = cy - 1; y > cy - 1 - len && y >= 2; y--)
                    if (v.isEmpty(x, y, z)) v.set(x, y, z, B.with("vine", o.id(), "true"));
            }
        }
        fixFences(hw, pd);
    }

    private static final List<String> POTS = List.of("potted_red_tulip", "potted_poppy", "potted_dandelion",
        "potted_blue_orchid", "potted_azure_bluet", "potted_cornflower", "potted_allium", "potted_fern");

    /** Mischpflaster der Plattform. */
    private String paving() {
        double r = rng.nextDouble();
        if (r < 0.12) return B.id("cobblestone");
        if (r < 0.20) return B.id("mossy_stone_bricks");
        if (r < 0.28) return B.id("cracked_stone_bricks");
        return s.vary("stone_bricks");
    }

    /** Mauerblock mit expliziten Zuständen (Pfosten oben, keine Verbindung). */
    private static String wallState(String wall) {
        return B.with(wall, "up", "true", "north", "none", "east", "none", "south", "none", "west", "none", "waterlogged", "false");
    }

    private void setFree(int x, int y, int z, String state) {
        if (v.in(x, y, z) && v.isEmpty(x, y, z)) v.set(x, y, z, state);
    }

    /** Zäune an der Brüstung verbinden sich mit Nachbarzäunen und vollen Blöcken. */
    private void fixFences(int hw, int pd) {
        String fenceName = B.name(B.id(p.fence()));
        for (int dist = 1; dist <= pd; dist++)
            for (int w = -hw; w <= hw; w++) {
                int x = cx(dist, w), z = cz(dist, w);
                if (!nameAt(x, 1, z).equals(fenceName)) continue;
                v.set(x, 1, z, fenceState(x, 1, z));
            }
    }

    private String fenceState(int x, int y, int z) {
        String[] val = new String[4];
        for (Dir d : Dir.values()) {
            int nx = x + d.dx, nz = z + d.dz;
            String n = nameAt(nx, y, nz);
            boolean link = n.endsWith("_fence") || (v.isSolid(nx, y, nz) && !n.endsWith("_stairs") && !n.endsWith("_slab")
                && !n.equals("barrel") && !n.endsWith("leaves") && !n.endsWith("_wall"));
            val[d.ordinal()] = String.valueOf(link);
        }
        return B.with(B.id(p.fence()), "north", val[Dir.NORTH.ordinal()], "east", val[Dir.EAST.ordinal()],
            "south", val[Dir.SOUTH.ordinal()], "west", val[Dir.WEST.ordinal()], "waterlogged", "false");
    }

    // ------------------------------------------------------------------ Stufe und Sockel

    /** Breite Eingangsstufe: Pflaster in Wegbreite vor dem Vorbau (bzw. vor der Tür), nach außen schmaler. */
    void apron() {
        apronFrom(built + 1);
    }

    /** Eingangsstufe ab Abstand {@code from} zur Tür (z. B. hinter dem alten, flachen Vorbau). */
    void apronFrom(int from) {
        if (doorX < 0) return;
        for (int dist = from; dist <= from + 1; dist++) {
            int hw = dist == from ? 2 : 1;
            for (int w = -hw; w <= hw; w++) {
                int x = cx(dist, w), z = cz(dist, w);
                if (!v.in(x, 0, z) || inLevAny(x, z)) continue;
                v.set(x, 0, z, paving());
            }
        }
    }

    /** Steinstreifen auf Bodenhöhe rund ums Erdgeschoss, der den Sockel nach außen fortsetzt. */
    void plinthRing() {
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                if (inLev(0, x, z)) continue;
                boolean near = false;
                for (int dx = -1; dx <= 1 && !near; dx++)
                    for (int dz = -1; dz <= 1; dz++) if (inLev(0, x + dx, z + dz)) { near = true; break; }
                if (!near) continue;
                String g = nameAt(x, 0, z);
                if (!g.equals("grass_block") && !g.equals("sand")) continue;
                v.set(x, 0, z, paving());
            }
    }

    // ------------------------------------------------------------------ Fenster und Wände

    /** Fensterrahmen aus Stämmen bzw. Klappen, Blumenkästen und Vordächer über den Fenstern. */
    void windows(boolean shutters) {
        String pane = B.name(B.id(p.window()));
        boolean boxFeature = spec.has(BuildingSpec.Feature.FLOWER_BOXES);
        for (int k = 0; k < maxK; k++) {
            int y0 = k * fh;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z]) continue;
                    int mask = openMask(k, x, z);
                    if (Integer.bitCount(mask) != 1) continue;
                    Dir out = Dir.values()[Integer.numberOfTrailingZeros(mask)];
                    if (!nameAt(x, y0 + 2, z).equals(pane)) continue;
                    int yHi = y0 + 2;
                    while (yHi + 1 <= y0 + fh - 1 && nameAt(x, yHi + 1, z).equals(pane)) yHi++;
                    int ox = x + out.dx, oz = z + out.dz;
                    if (!v.in(ox, yHi + 1, oz) || s.isReserved(ox, y0 + 2, oz)) continue;
                    int lx = out.wallAxis() == 'x' ? 1 : 0, lz = 1 - lx;
                    // Blumenkasten: Klappe unter dem Fenster, Blumentopf davor
                    if (rng.chance(boxFeature ? 0.8 : 0.3) && v.isEmpty(ox, y0 + 1, oz) && v.isEmpty(ox, y0 + 2, oz)) {
                        if (v.isSolid(ox, y0, oz)) {
                            // Kasten steht auf festem Grund: Klappe als Boden, Topf darauf
                            v.set(ox, y0 + 1, oz, B.trapdoor(p.trapdoor(), out, true, false));
                            v.set(ox, y0 + 2, oz, B.id(rng.pick(POTS)));
                        } else {
                            // oberes Geschoss ohne Auflage: aufgeklappte Klappe als Fensterbrett an der Wand, kein Topf
                            v.set(ox, y0 + 1, oz, B.trapdoor(p.trapdoor(), out, false, true));
                        }
                    }
                    // Vordach über dem Fenster
                    if (k < maxK - 1 && rng.chance(0.5)) setFree(ox, yHi + 1, oz, B.trapdoor(p.trapdoor(), out, false, false));
                    // Rahmen aus Stamm links und rechts, wenn keine Läden dran sind
                    if (!shutters) {
                        for (int sg : new int[] {-1, 1}) {
                            int nx = x + sg * lx, nz = z + sg * lz;
                            if (!inLev(k, nx, nz) || openMask(k, nx, nz) != mask) continue;
                            for (int y = y0 + 2; y <= yHi; y++) {
                                String at = nameAt(nx, y, nz);
                                if (at.equals(pane) || at.endsWith("_door") || B.isThin(v.get(nx, y, nz))) continue;
                                v.set(nx, y, nz, B.axis(frameB, 'y'));
                            }
                        }
                    }
                }
        }
    }

    /** Ranken an Außenwänden des Erdgeschosses, unten mit einem Laubstrauch. */
    void vines() {
        int count = 0;
        for (int x = 0; x < W && count < 6; x++)
            for (int z = 0; z < D && count < 6; z++) {
                if (!lev[0][x][z]) continue;
                int mask = openMask(0, x, z);
                if (Integer.bitCount(mask) != 1) continue;
                Dir out = Dir.values()[Integer.numberOfTrailingZeros(mask)];
                if (Math.abs(x - doorX) + Math.abs(z - doorZ) < 5 || !rng.chance(0.04)) continue;
                int ox = x + out.dx, oz = z + out.dz;
                if (!v.in(ox, 1, oz) || !v.isEmpty(ox, 1, oz) || s.isReserved(ox, 1, oz)) continue;
                int top = Math.max(3, fh - 1), len = 0;
                for (int y = top; y >= 2; y--) {
                    String wall = v.get(x, y, z);
                    if (wall == null || B.isAir(wall) || B.isThin(wall) || !v.isEmpty(ox, y, oz)) break;
                    len++;
                }
                if (len < 2) continue;
                for (int y = top; y > top - len; y--) v.set(ox, y, oz, B.with("vine", out.opposite().id(), "true"));
                if (top - len == 1) v.set(ox, 1, oz, B.leaves(B.id(rng.chance(0.5) ? p.leaves() : "flowering_azalea_leaves")));
                count++;
            }
    }
}
