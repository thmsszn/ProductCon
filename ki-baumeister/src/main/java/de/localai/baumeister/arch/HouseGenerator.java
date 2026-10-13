package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.List;

/**
 * Wohnhaus-Generator (Haus, Hütte, Villa, Taverne, Scheune, modernes Haus). Baut ein vollständiges Gebäude:
 * Fundament, Fachwerk/Mauerwerk, Fenster mit Simsen und Läden, Tür mit Vordach, Satteldach/Walmdach/Flachdach
 * mit Überstand, Schornstein, Treppenhaus, Raumaufteilung mit Möbeln und Garten.
 */
final class HouseGenerator {
    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final BuildingSpec.Type type;

    private int bx0, bx1, bz0, bz1, fw, fd;
    private int ix0, ix1, iz0, iz1;
    private int floors, fh, ytop, ridgeY;
    private Palette.Roof roof;
    private boolean ridgeX;
    private int doorX, doorW;
    private int chimX = -1, chimZ = -1;
    private boolean chimOutside;
    private int partZ = -1;
    private final List<int[]> postsNS = new ArrayList<>();
    private int[] postsX;
    private int[] postsZ;

    HouseGenerator(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.spec = site.spec;
        this.type = site.spec.type();
    }

    Voxels build() {
        layout();
        s.clearSite(p == null ? "grass_block" : groundBlock());
        foundation();
        walls();
        interiorFloors();
        planChimney();
        door();
        windows();
        roof();
        if (FacadeDetail.applies(spec.style(), p.wall()) && roof != Palette.Roof.FLAT) {
            FacadeDetail.roofBands(v, p, bx0 - 1, bz0 - 1, bx1 + 1, bz1 + 1, ytop);
        }
        chimney();
        stairs();
        partitions();
        furnish();
        exterior();
        return v;
    }

    private String groundBlock() {
        return spec.style().sandy() ? "sand" : "grass_block";
    }

    // ------------------------------------------------------------------ Grundriss

    private void layout() {
        int W = s.w, H = s.h, D = s.d;
        int side = W >= 13 ? 2 : W >= 9 ? 1 : 0;
        int back = D >= 13 ? 2 : D >= 9 ? 1 : 0;
        int front = D >= 16 ? 4 : D >= 12 ? 3 : D >= 9 ? 2 : D >= 7 ? 1 : 0;
        int maxW = switch (type) {
            case COTTAGE -> 11;
            case MANSION -> 31;
            case BARN -> 15;
            default -> 21;
        };
        int maxD = switch (type) {
            case COTTAGE -> 9;
            case MANSION -> 19;
            case BARN -> 21;
            default -> 15;
        };
        fw = W - 2 * side;
        if (fw > maxW) {
            side = (W - maxW) / 2;
            fw = W - 2 * side;
        }
        fd = D - front - back;
        if (fd > maxD) {
            int extra = fd - maxD;
            front += (extra + 1) / 2;
            back += extra / 2;
            fd = D - front - back;
        }
        while (fw < 5 && side > 0) { side--; fw = W - 2 * side; }
        while (fd < 5 && (front > 0 || back > 0)) {
            if (front > back) front--; else back--;
            fd = D - front - back;
        }
        bx0 = side;
        bx1 = side + fw - 1;
        bz0 = front;
        bz1 = front + fd - 1;
        ix0 = bx0 + 1;
        ix1 = bx1 - 1;
        iz0 = bz0 + 1;
        iz1 = bz1 - 1;

        fh = p.floorHeight();
        roof = spec.roof() != null ? spec.roof() : p.defaultRoof();
        if (roof == Palette.Roof.CONE) roof = Palette.Roof.HIP;
        if (type == BuildingSpec.Type.MODERN_HOUSE && spec.roof() == null) roof = Palette.Roof.FLAT;
        ridgeX = fw >= fd;
        int span = (ridgeX ? fd : fw) + 2;
        int roofLayers = roof == Palette.Roof.FLAT ? 2 : span / 2 + 2;
        int maxFloors = (H - 1 - roofLayers) / fh;
        if (maxFloors < 1 && roof != Palette.Roof.FLAT) {
            roof = Palette.Roof.FLAT;
            maxFloors = (H - 3) / fh;
        }
        if (maxFloors < 1) {
            fh = Math.max(3, H - 3);
            maxFloors = 1;
        }
        int wanted = spec.floors();
        if (type == BuildingSpec.Type.COTTAGE) wanted = 1;
        if (type == BuildingSpec.Type.MANSION) wanted = Math.max(2, wanted);
        floors = Math.max(1, Math.min(wanted, maxFloors));
        ytop = floors * fh;
        ridgeY = ytop;

        postsX = posts(fw, p.bigWindows() ? 6 : 4);
        postsZ = posts(fd, p.bigWindows() ? 6 : 4);
    }

    /** Gleichmäßig verteilte Pfosten (relative Positionen 0..len-1, inklusive Ecken). */
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

    // ------------------------------------------------------------------ Rohbau

    private void foundation() {
        for (int x = bx0; x <= bx1; x++)
            for (int z = bz0; z <= bz1; z++) {
                boolean ring = x == bx0 || x == bx1 || z == bz0 || z == bz1;
                v.set(x, 0, z, ring ? s.vary(p.foundation()) : B.id(p.floor().block()));
            }
    }

    private boolean onRing(int x, int z) {
        return (x == bx0 || x == bx1) && z >= bz0 && z <= bz1 || (z == bz0 || z == bz1) && x >= bx0 && x <= bx1;
    }

    private void walls() {
        boolean plinth = p.timber() && p.groundWall().equals(p.wall());
        for (int y = 1; y <= ytop; y++) {
            boolean beamRow = y % fh == 0;
            for (int x = bx0; x <= bx1; x++)
                for (int z = bz0; z <= bz1; z++) {
                    if (!onRing(x, z)) continue;
                    boolean alongX = z == bz0 || z == bz1;
                    boolean corner = (x == bx0 || x == bx1) && (z == bz0 || z == bz1);
                    boolean post = alongX ? contains(postsX, x - bx0) : contains(postsZ, z - bz0);
                    String block;
                    if (corner) {
                        block = B.axis(p.frame(), 'y');
                    } else if (beamRow) {
                        block = p.timber() ? B.axis(p.frame(), alongX ? 'x' : 'z') : band(y);
                    } else if (p.timber() && post) {
                        block = B.axis(p.frame(), 'y');
                    } else {
                        String mat = y < fh ? p.groundWall() : p.wall();
                        if (plinth && y == 1) mat = p.foundation();
                        block = s.vary(mat);
                    }
                    v.set(x, y, z, block);
                }
        }
    }

    /** Geschossband bei Mauerwerk-Stilen (Gesims). */
    private String band(int y) {
        return switch (spec.style()) {
            case VICTORIAN, CLASSICAL, STONE -> B.id(p.trim().block());
            case MODERN -> y == ytop ? B.id(p.trim().block()) : B.id(p.wall());
            default -> B.id(y < fh ? p.groundWall() : p.wall());
        };
    }

    private void interiorFloors() {
        for (int f = 1; f <= floors; f++) {
            int y = f * fh;
            v.fill(ix0, y, iz0, ix1, y, iz1, B.id(p.floor().block()));
        }
    }

    // ------------------------------------------------------------------ Schornstein

    private boolean wantsChimney() {
        if (roof == Palette.Roof.FLAT || type == BuildingSpec.Type.BARN || type == BuildingSpec.Type.MODERN_HOUSE) return false;
        if (spec.has(BuildingSpec.Feature.CHIMNEY)) return true;
        return switch (spec.style()) {
            case MEDIEVAL, RUSTIC, NORDIC, VICTORIAN -> true;
            default -> false;
        };
    }

    private void planChimney() {
        if (!wantsChimney()) return;
        if (roof == Palette.Roof.GABLE && ridgeX) {
            chimZ = (bz0 + bz1) / 2;
            chimOutside = bx1 + 1 < s.w;
            chimX = chimOutside ? bx1 + 1 : bx1;
        } else if (roof == Palette.Roof.GABLE) {
            chimX = (bx0 + bx1) / 2;
            chimOutside = bz1 + 1 < s.d;
            chimZ = chimOutside ? bz1 + 1 : bz1;
        } else {
            chimX = ix1 - 1;
            chimZ = iz1 - 1;
            chimOutside = false;
        }
    }

    private void chimney() {
        if (chimX < 0) return;
        int top;
        if (roof == Palette.Roof.GABLE) {
            top = ridgeY + 1;
        } else {
            int roofAt = ytop;
            for (int y = s.h - 1; y > ytop; y--) {
                if (!v.isEmpty(chimX, y, chimZ)) { roofAt = y; break; }
            }
            top = roofAt + 2;
        }
        top = Math.min(top, s.h - 2);
        for (int y = chimOutside ? 0 : 1; y <= top; y++) {
            v.set(chimX, y, chimZ, s.vary(p.chimney()));
            if (!chimOutside) s.reserve(chimX, y, chimZ);
        }
        if (v.in(chimX, top + 1, chimZ)) v.set(chimX, top + 1, chimZ, B.campfire());
    }

    // ------------------------------------------------------------------ Tür & Fenster

    private void door() {
        int center = (fw - 1) / 2;
        int a = 0, b = fw - 1;
        for (int i = 0; i + 1 < postsX.length; i++) {
            if (postsX[i] <= center && center <= postsX[i + 1]) { a = postsX[i]; b = postsX[i + 1]; break; }
        }
        int bay = b - a - 1;
        boolean wide = (type == BuildingSpec.Type.MANSION || type == BuildingSpec.Type.TAVERN || type == BuildingSpec.Type.BARN) && bay >= 4;
        doorW = wide ? 2 : 1;
        if (bay < doorW) { a = center - 1; bay = doorW; }
        doorX = bx0 + a + 1 + (bay - doorW) / 2;
        for (int i = 0; i < doorW; i++) {
            int x = doorX + i;
            v.set(x, 1, bz0, B.door(p.door(), Dir.SOUTH, i == 1));
            v.set(x, 2, bz0, B.AIR);
            if (fh >= 4 && 3 % fh != 0) {
                v.set(x, 3, bz0, p.timber() ? B.axis(p.frame(), 'x') : B.id(p.trim().block()));
            }
            for (int y = 1; y <= 2; y++) {
                s.reserve(x, y, bz0 + 1);
                s.reserve(x, y, bz0 - 1);
            }
        }
    }

    private void windows() {
        for (Dir dir : Dir.values()) {
            boolean alongX = dir == Dir.NORTH || dir == Dir.SOUTH;
            int[] posts = alongX ? postsX : postsZ;
            for (int f = 0; f < floors; f++) {
                int yb = f * fh;
                for (int i = 0; i + 1 < posts.length; i++) {
                    int a = posts[i], b = posts[i + 1];
                    int len = b - a - 1;
                    if (len < 1) continue;
                    if (dir == Dir.NORTH && f == 0 && bx0 + a < doorX + doorW && bx0 + b > doorX) {
                        // Feld mit der Haustür: bei breiten Feldern kleine Fenster neben der Tür
                        if (len >= doorW + 4) {
                            placeWindow(dir, alongX, a + 1, 1, yb, false);
                            placeWindow(dir, alongX, b - 1, 1, yb, false);
                        }
                        continue;
                    }
                    if (chimneyInBay(dir, alongX, a, b)) continue;
                    int width = p.bigWindows() ? Math.max(1, len - 1) : len >= 4 ? 2 : 1;
                    if (p.bigWindows() && len >= 5) width = len - 2;
                    int start = a + 1 + (len - width) / 2;
                    boolean tall = p.bigWindows() && f == 0 && (dir == Dir.SOUTH || dir == Dir.NORTH);
                    placeWindow(dir, alongX, start, width, yb, tall);
                }
            }
        }
    }

    private boolean chimneyInBay(Dir dir, boolean alongX, int a, int b) {
        if (chimX < 0) return false;
        if (alongX) {
            int wallZ = dir == Dir.NORTH ? bz0 : bz1;
            boolean nearWall = chimZ == wallZ || chimZ == wallZ + dir.dz;
            return nearWall && chimX - bx0 > a && chimX - bx0 < b;
        }
        int wallX = dir == Dir.WEST ? bx0 : bx1;
        boolean nearWall = chimX == wallX || chimX == wallX + dir.dx;
        return nearWall && chimZ - bz0 > a && chimZ - bz0 < b;
    }

    /** Fenster in eine Wand setzen; {@code start} ist relativ zur Wandecke. */
    private void placeWindow(Dir dir, boolean alongX, int start, int width, int yb, boolean tall) {
        int y0 = tall ? yb + 1 : yb + 2;
        int y1 = yb + fh - 1;
        if (y1 < y0) y1 = y0;
        boolean decorate = !p.bigWindows();
        for (int i = 0; i < width; i++) {
            int t = start + i;
            int x = alongX ? bx0 + t : (dir == Dir.WEST ? bx0 : bx1);
            int z = alongX ? (dir == Dir.NORTH ? bz0 : bz1) : bz0 + t;
            for (int y = y0; y <= y1; y++) v.set(x, y, z, B.id(p.window()));
            int ox = x + dir.dx, oz = z + dir.dz;
            if (decorate && !tall && s.free(ox, y0 - 1, oz) && v.in(ox, y0 - 1, oz)) {
                v.set(ox, y0 - 1, oz, B.trapdoor(p.trapdoor(), dir, true, false));
                if (spec.has(BuildingSpec.Feature.FLOWER_BOXES) && s.free(ox, y0, oz)) {
                    v.set(ox, y0, oz, B.id(s.rng.pick(List.of("potted_red_tulip", "potted_poppy", "potted_dandelion",
                        "potted_blue_orchid", "potted_azure_bluet"))));
                }
            }
        }
        if (decorate && !tall && (p.shutters() || spec.has(BuildingSpec.Feature.SHUTTERS))) {
            for (int t : new int[] {start - 1, start + width}) {
                int x = alongX ? bx0 + t : (dir == Dir.WEST ? bx0 : bx1);
                int z = alongX ? (dir == Dir.NORTH ? bz0 : bz1) : bz0 + t;
                int ox = x + dir.dx, oz = z + dir.dz;
                for (int y = y0; y <= y1; y++) {
                    if (s.free(ox, y, oz) && v.in(ox, y, oz)) v.set(ox, y, oz, B.trapdoor(p.trapdoor(), dir, y == y1, true));
                }
            }
        }
    }

    // ------------------------------------------------------------------ Dach

    private void roof() {
        switch (roof) {
            case GABLE -> gableRoof();
            case HIP -> hipRoof();
            default -> flatRoof();
        }
    }

    private String gableInfill() {
        return B.id(p.timber() ? p.wall() : (spec.style() == Palette.Style.VICTORIAN ? p.wall() : p.groundWall()));
    }

    private void gableRoof() {
        int ovA, ovB, ovG;
        if (ridgeX) {
            ovA = Math.min(bz0 >= 1 ? 1 : 0, bz1 + 1 < s.d ? 1 : 0);
            ovG = Math.min(bx0 >= 1 ? 1 : 0, bx1 + 1 < s.w ? 1 : 0);
        } else {
            ovA = Math.min(bx0 >= 1 ? 1 : 0, bx1 + 1 < s.w ? 1 : 0);
            ovG = Math.min(bz0 >= 1 ? 1 : 0, bz1 + 1 < s.d ? 1 : 0);
        }
        ovB = ovA;
        int lo = (ridgeX ? bz0 : bx0) - ovA;
        int hi = (ridgeX ? bz1 : bx1) + ovB;
        int alo = (ridgeX ? bx0 : bz0) - ovG;
        int ahi = (ridgeX ? bx1 : bz1) + ovG;
        Dir upA = ridgeX ? Dir.SOUTH : Dir.EAST;
        Dir upB = upA.opposite();
        Materials.Family r = p.roof();
        for (int i = 0; ; i++) {
            int ca = lo + i, cb = hi - i;
            int y = ytop + 1 + i - ovA;
            if (ca > cb || y >= s.h) break;
            for (int t = alo; t <= ahi; t++) {
                if (ca == cb) {
                    setRoof(t, y, ca, B.id(r.block()));
                    if (y + 1 < s.h) setRoof(t, y + 1, ca, B.slab(r, false));
                    ridgeY = Math.max(ridgeY, y + 1);
                } else {
                    setRoof(t, y, ca, B.stairs(r, upA, false));
                    setRoof(t, y, cb, B.stairs(r, upB, false));
                    ridgeY = Math.max(ridgeY, y);
                }
            }
            // Giebelwände zwischen den Dachflächen
            if (y > ytop) {
                for (int g : new int[] {ridgeX ? bx0 : bz0, ridgeX ? bx1 : bz1}) {
                    for (int c = ca + 1; c <= cb - 1; c++) setRoof(g, y, c, gableInfill());
                }
            }
        }
        // Rundfenster im Giebel
        int mid = ((ridgeX ? bz0 : bx0) + (ridgeX ? bz1 : bx1)) / 2;
        int gy = ytop + 2;
        if (gy < ridgeY - 1) {
            for (int g : new int[] {ridgeX ? bx0 : bz0, ridgeX ? bx1 : bz1}) {
                int x = ridgeX ? g : mid, z = ridgeX ? mid : g;
                if (x == chimX && z == chimZ) continue;
                if (!B.isAir(v.get(x, gy, z)) && !B.name(v.get(x, gy, z)).endsWith("stairs")) v.set(x, gy, z, B.id(p.window()));
            }
        }
    }

    /** Dachblock setzen (lokal: t = Position entlang des Firsts, c = quer dazu). */
    private void setRoof(int t, int y, int c, String state) {
        int x = ridgeX ? t : c, z = ridgeX ? c : t;
        v.set(x, y, z, state);
    }

    private void hipRoof() {
        int ov = Math.min(Math.min(bx0 >= 1 ? 1 : 0, bx1 + 1 < s.w ? 1 : 0), Math.min(bz0 >= 1 ? 1 : 0, bz1 + 1 < s.d ? 1 : 0));
        if (spec.style() == Palette.Style.JAPANESE && bx0 >= 2 && bz0 >= 2 && bx1 + 2 < s.w && bz1 + 2 < s.d) ov = 2;
        Materials.Family r = p.roof();
        int xLo = bx0 - ov, xHi = bx1 + ov, zLo = bz0 - ov, zHi = bz1 + ov;
        for (int i = 0; ; i++) {
            int ax = xLo + i, bx = xHi - i, az = zLo + i, bz = zHi - i;
            // Breite Traufen (Überstand 2) bleiben auf Wandhöhe, damit die Wände sichtbar bleiben
            int y = ytop + Math.max(0, i - ov + 1);
            if (ax > bx || az > bz || y >= s.h) break;
            if (ax == bx || az == bz) {
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        v.set(x, y, z, B.id(r.block()));
                        if (y + 1 < s.h) v.set(x, y + 1, z, B.slab(r, false));
                    }
                ridgeY = Math.max(ridgeY, y + 1);
                break;
            }
            for (int x = ax; x <= bx; x++) {
                v.set(x, y, az, B.stairs(r, Dir.SOUTH, false));
                v.set(x, y, bz, B.stairs(r, Dir.NORTH, false));
            }
            for (int z = az + 1; z < bz; z++) {
                v.set(ax, y, z, B.stairs(r, Dir.EAST, false));
                v.set(bx, y, z, B.stairs(r, Dir.WEST, false));
            }
            ridgeY = Math.max(ridgeY, y);
            // Innenraum unter dem Dach geschlossen halten (Dachboden-Decke ist bereits gesetzt)
        }
    }

    private void flatRoof() {
        Materials.Family r = p.roof();
        v.fill(ix0, ytop, iz0, ix1, ytop, iz1, B.id(r.block()));
        if (ytop + 1 >= s.h) return;
        boolean desert = spec.style().sandy();
        boolean modern = spec.style() == Palette.Style.MODERN;
        for (int x = bx0; x <= bx1; x++)
            for (int z = bz0; z <= bz1; z++) {
                if (!onRing(x, z)) continue;
                if (modern) {
                    v.set(x, ytop + 1, z, B.slab(p.trim(), false));
                } else {
                    v.set(x, ytop + 1, z, B.id(desert ? p.wall() : p.trim().block()));
                    int t = (x - bx0) + (z - bz0);
                    if (desert && t % 2 == 0 && ytop + 2 < s.h) v.set(x, ytop + 2, z, B.id(p.wall()));
                }
            }
        if (modern) {
            // Dünnes, umlaufendes Vordach
            for (int x = bx0 - 1; x <= bx1 + 1; x++)
                for (int z = bz0 - 1; z <= bz1 + 1; z++) {
                    boolean outer = x == bx0 - 1 || x == bx1 + 1 || z == bz0 - 1 || z == bz1 + 1;
                    if (outer && v.in(x, ytop, z)) v.set(x, ytop, z, B.slab(p.trim(), true));
                }
        }
        ridgeY = ytop + 1;
    }

    // ------------------------------------------------------------------ Treppenhaus

    private void stairs() {
        if (floors < 2) return;
        int iw = ix1 - ix0 + 1;
        boolean straight = floors == 2 ? iw >= fh + 2 : iw >= 2 * fh;
        if (!straight || iz1 - iz0 < 2) {
            ladder();
            return;
        }
        Materials.Family f = p.floor();
        for (int fl = 0; fl < floors - 1; fl++) {
            int yb = fl * fh;
            boolean east = fl % 2 == 0;
            for (int i = 0; i < fh; i++) {
                int x = east ? ix0 + 1 + i : ix1 - 1 - i;
                int y = yb + 1 + i;
                v.set(x, y, iz1, B.stairs(f, east ? Dir.EAST : Dir.WEST, false));
                for (int yy = yb + 1; yy < y; yy++) v.set(x, yy, iz1, B.id(f.block()));
                for (int hy = y; hy <= y + 2; hy++) s.reserve(x, hy, iz1);
            }
            // Deckenöffnung + Geländer auf dem oberen Stockwerk
            int top = (fl + 1) * fh;
            for (int i = 0; i < fh - 1; i++) {
                int x = east ? ix0 + 1 + i : ix1 - 1 - i;
                v.set(x, top, iz1, B.AIR);
                if (s.free(x, top + 1, iz1 - 1)) v.set(x, top + 1, iz1 - 1, B.id(p.fence()));
                s.reserve(x, top + 1, iz1);
                s.reserve(x, top + 2, iz1);
            }
            int arrive = east ? ix0 + 1 + fh : ix1 - 1 - fh;
            s.reserve(arrive, top + 1, iz1);
            s.reserve(east ? ix0 : ix1, yb + 1, iz1);
            s.reserve(east ? ix0 + 1 : ix1 - 1, yb + 1, iz1 - 1);
        }
    }

    private void ladder() {
        int x = ix0, z = iz1;
        for (int fl = 0; fl < floors - 1; fl++) {
            int yb = fl * fh;
            for (int y = yb + 1; y <= yb + fh; y++) {
                v.set(x, y, z, B.ladder(Dir.NORTH));
                s.reserve(x, y, z);
                s.reserve(x, y, z - 1);
            }
        }
    }

    // ------------------------------------------------------------------ Innenräume

    private void partitions() {
        int depth = iz1 - iz0 + 1;
        if (depth < 7 || type == BuildingSpec.Type.BARN || ix1 - ix0 < 3) return;
        partZ = iz0 + depth / 2;
        String mat = B.id(p.timber() || spec.style() == Palette.Style.MODERN ? p.floor().block() : p.groundWall());
        int doorwayX = (ix0 + ix1) / 2;
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            for (int y = yb + 1; y < yb + fh; y++)
                for (int x = ix0; x <= ix1; x++) {
                    if (s.isReserved(x, y, partZ)) continue;
                    boolean opening = x == doorwayX && y <= yb + 2;
                    v.set(x, y, partZ, opening ? B.AIR : mat);
                }
            for (int y = yb + 1; y <= yb + 2; y++) {
                s.reserve(doorwayX, y, partZ);
                s.reserve(doorwayX, y, partZ - 1);
                s.reserve(doorwayX, y, partZ + 1);
            }
        }
    }

    private void furnish() {
        if (!spec.has(BuildingSpec.Feature.INTERIOR)) {
            for (int f = 0; f < floors; f++) s.hangingLight((ix0 + ix1) / 2, f * fh + fh - 1, (iz0 + iz1) / 2);
            return;
        }
        List<String> rooms = new ArrayList<>(spec.rooms());
        List<String> defaults = switch (type) {
            case TAVERN -> List.of("bar", "dining", "bedroom", "bedroom", "storage", "bedroom");
            case MANSION -> List.of("living", "dining", "library", "office", "bedroom", "bedroom");
            case BARN -> List.of("storage", "workshop", "storage");
            case MODERN_HOUSE -> List.of("living", "kitchen", "bedroom", "office", "bedroom", "living");
            default -> List.of("living", "kitchen", "bedroom", "library", "bedroom", "storage");
        };
        int next = 0;
        for (int f = 0; f < floors; f++) {
            int yb = f * fh;
            List<Site.Room> plan = new ArrayList<>();
            if (partZ > 0) {
                plan.add(new Site.Room(ix0, iz0, ix1, partZ - 1, yb + 1, yb + fh, ""));
                plan.add(new Site.Room(ix0, partZ + 1, ix1, iz1, yb + 1, yb + fh, ""));
            } else {
                plan.add(new Site.Room(ix0, iz0, ix1, iz1, yb + 1, yb + fh, ""));
            }
            for (Site.Room room : plan) {
                String kind = next < rooms.size() ? rooms.get(next) : defaults.get(Math.min(next, defaults.size() - 1));
                next++;
                s.furnish(new Site.Room(room.x0(), room.z0(), room.x1(), room.z1(), room.y(), room.ceiling(), kind));
            }
        }
        if (type == BuildingSpec.Type.BARN) {
            for (int x = ix0; x <= ix1; x += 2) {
                if (s.free(x, 1, iz1)) v.set(x, 1, iz1, B.axis("hay_block", 'y'));
                if (s.free(x, 2, iz1)) v.set(x, 2, iz1, B.axis("hay_block", 'x'));
            }
        }
    }

    // ------------------------------------------------------------------ Außenbereich

    private void exterior() {
        boolean path = spec.has(BuildingSpec.Feature.PATH) || bz0 >= 1;
        if (path) {
            for (int z = 0; z < bz0; z++)
                for (int i = 0; i < doorW; i++) v.set(doorX + i, 0, z, B.id(p.path()));
        }
        boolean porch = bz0 >= 2 && (spec.has(BuildingSpec.Feature.PORCH) || type == BuildingSpec.Type.TAVERN
            || type == BuildingSpec.Type.MANSION || spec.has(BuildingSpec.Feature.BALCONY));
        boolean balcony = porch && spec.has(BuildingSpec.Feature.BALCONY) && floors >= 2;
        if (porch) porch(balcony);
        else if (bz0 >= 1) {
            for (int x : new int[] {doorX - 1, doorX + doorW}) {
                if (s.free(x, 2, bz0 - 1) && v.isSolid(x, 2, bz0)) v.set(x, 2, bz0 - 1, B.wallTorch(Dir.NORTH));
            }
        }
        if (FacadeDetail.applies(spec.style(), p.wall())) FacadeDetail.houseExtras(s, bx0, bz0, bx1, bz1, doorX, doorW, porch);
        if (spec.has(BuildingSpec.Feature.LANTERNS) && bz0 >= 3) s.lanternPosts(doorX, 0, doorW);
        boolean fence = spec.has(BuildingSpec.Feature.FENCE) && bx0 >= 1 && bz0 >= 2 && s.d - 1 - bz1 >= 1;
        if (fence) s.fence(doorX, doorW);
        if (spec.has(BuildingSpec.Feature.POOL)) pool();
        if (spec.has(BuildingSpec.Feature.TREES)) trees();
        if (spec.has(BuildingSpec.Feature.GARDEN) || spec.has(BuildingSpec.Feature.TREES)) s.garden(spec.has(BuildingSpec.Feature.GARDEN) ? 1.0 : 0.5);
        if (spec.has(BuildingSpec.Feature.GARDEN) || fence) {
            s.flowersAndBushes(0, 0, s.w - 1, s.d - 1, 0.10, 0.04);
        }
    }

    private void porch(boolean balcony) {
        int x0 = doorX - 1, x1 = doorX + doorW;
        int z0 = bz0 - 2, z1 = bz0 - 1;
        int roofY = balcony ? fh : 3;
        boolean grand = type == BuildingSpec.Type.MANSION || spec.style() == Palette.Style.CLASSICAL;
        for (int x : new int[] {x0, x1}) {
            for (int y = 1; y < roofY; y++) {
                v.set(x, y, z0, grand ? B.axis(p.frame(), 'y') : B.id(p.fence()));
            }
        }
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                v.set(x, roofY, z, balcony ? B.id(p.floor().block()) : B.slab(p.trim(), false));
            }
        if (balcony) {
            for (int x = x0; x <= x1; x++) v.set(x, roofY + 1, z0, B.id(p.fence()));
            v.set(x0, roofY + 1, z1, B.id(p.fence()));
            v.set(x1, roofY + 1, z1, B.id(p.fence()));
            // Balkontür im Obergeschoss
            v.set(doorX, roofY + 1, bz0, B.door(p.door(), Dir.SOUTH, false));
            v.set(doorX, roofY + 2, bz0, B.AIR);
            s.reserve(doorX, roofY + 1, bz0 + 1);
            s.reserve(doorX, roofY + 2, bz0 + 1);
            // Fensterläden/Simse vor der Balkontür entfernen
            for (int y = roofY + 1; y <= roofY + 2; y++) {
                for (int x = x0; x <= x1; x++) {
                    String st = v.get(x, y, z1);
                    if (st != null && B.name(st).endsWith("_trapdoor")) v.set(x, y, z1, B.AIR);
                }
            }
        }
        // Laternen unter dem Vordach seitlich der Tür
        String lantern = B.lantern(p.light(), true);
        for (int x : new int[] {x0, x1}) {
            if (v.isEmpty(x, roofY - 1, z1)) v.set(x, roofY - 1, z1, lantern);
        }
        for (int z = z0; z <= z1; z++)
            for (int i = 0; i < doorW; i++) {
                s.reserve(doorX + i, 1, z);
                s.reserve(doorX + i, 2, z);
            }
    }

    private void pool() {
        int back = s.d - 1 - bz1;
        if (back < 5) return;
        int px0 = Math.max(bx0 + 1, 1), px1 = Math.min(bx1 - 1, px0 + 7);
        int pz0 = bz1 + 2, pz1 = Math.min(s.d - 2, pz0 + 3);
        if (px1 - px0 < 2 || pz1 - pz0 < 1) return;
        for (int x = px0 - 1; x <= px1 + 1; x++)
            for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                boolean edge = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
                v.set(x, 0, z, B.id(edge ? "smooth_quartz" : "water"));
                s.reserve(x, 1, z);
            }
    }

    private void trees() {
        int side = bx0, back = s.d - 1 - bz1;
        if (side >= 3 && back >= 3) {
            s.tree(side / 2, s.d - 1 - back / 2, 3);
            s.tree(s.w - 1 - side / 2, s.d - 1 - back / 2, 3);
        }
        if (side >= 3 && bz0 >= 4) {
            s.tree(side / 2, bz0 / 2, 3);
            s.tree(s.w - 1 - side / 2, bz0 / 2, 3);
        }
    }
}
