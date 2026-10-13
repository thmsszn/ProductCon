package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Freier Bauplan: beliebig viele rechteckige oder runde Baukörper, auch gestapelt, werden zu einer gemeinsamen
 * Grundfläche pro Geschoss vereinigt. Wände entstehen überall dort, wo die Fläche endet; dazu Eckpfeiler, Fenster,
 * Tür, Leitern, Zwischendecken, Dächer je Baukörper und Einrichtung. Die Form kommt aus den Teilen, die die KI
 * beschreibt oder die {@link Layouts} liefern.
 */
final class FreeBuilder {
    private static final class Vol {
        int x0, z0, x1, z1, b, n;
        boolean round, ridgeX;
        double cx, cz, r;
        String roof;

        boolean contains(int x, int z) {
            if (!round) return x >= x0 && x <= x1 && z >= z0 && z <= z1;
            double dx = x - cx, dz = z - cz;
            return dx * dx + dz * dz <= (r + 0.3) * (r + 0.3);
        }

        int top() {
            return b + n;
        }
    }

    private final Site s;
    private final Voxels v;
    private final Palette p;
    private final BuildingSpec spec;
    private final Rng rng;
    private final int W, H, D;
    private final List<Vol> vols = new ArrayList<>();

    private int fh, maxK;
    private boolean[][][] lev;
    private int[][] topVol;
    private int mf, doorX = -1, doorZ = -1;
    private Dir doorDir = Dir.NORTH;
    private String frameB, wallB, groundWallB;

    FreeBuilder(Site site) {
        this.s = site;
        this.v = site.v;
        this.p = site.p;
        this.spec = site.spec;
        this.rng = site.rng;
        this.W = site.w;
        this.H = site.h;
        this.D = site.d;
    }

    Voxels build() {
        s.clearSite(spec.style().sandy() ? "sand" : "grass_block");
        if (!plan()) return new HouseGenerator(s).build();
        shell();
        windows();
        door();
        ladders();
        roofs();
        furnish();
        exterior();
        return v;
    }

    // ------------------------------------------------------------------ Planung

    private List<BuildingSpec.Part> source() {
        if (!spec.parts().isEmpty()) return spec.parts();
        Rng r = new Rng(spec.seed() * 7 + 3);
        Layouts.Kind kind = Layouts.pick(spec, r);
        return Layouts.parts(spec, kind, r);
    }

    private String defaultRoof() {
        if (spec.roof() != null) return spec.roof().name().toLowerCase();
        return p.defaultRoof().name().toLowerCase();
    }

    private int roofNeed(Vol q) {
        String roof = q.roof;
        int span = Math.min(q.x1 - q.x0, q.z1 - q.z0) + 1;
        if (q.round) {
            int r = (int) Math.round(q.r);
            return roof.equals("flat") ? 1 : roof.equals("dome") ? r + 2 : (int) (r * 1.5) + 3;
        }
        return switch (roof) {
            case "flat" -> 2;
            case "hip" -> Math.min((span + 2) / 2 + 1, 6);
            default -> ((q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0) + 3) / 2 + 2;
        };
    }

    private boolean plan() {
        if (H < 6 || W < 7 || D < 7) return false;
        int mx = W >= 20 ? 2 : 1;
        mf = D >= 18 ? 3 : 1;
        int mb = D >= 18 ? 2 : 1;
        int cap = spec.parts().isEmpty() ? 25 : 44;
        int uw = Math.min(W - 2 * mx, cap), ud = Math.min(D - mf - mb, cap);
        if (uw < 7 || ud < 7) return false;
        int ox = (W - uw) / 2;

        String defRoof = defaultRoof();
        List<BuildingSpec.Part> src = source();
        for (BuildingSpec.Part part : src) {
            Vol q = new Vol();
            int w = Math.min(uw, Math.max(5, Math.round(part.w() * uw / 100f)));
            int d = Math.min(ud, Math.max(5, Math.round(part.d() * ud / 100f)));
            int x0 = ox + Math.round(part.x() * uw / 100f);
            int z0 = mf + Math.round(part.z() * ud / 100f);
            x0 = Math.max(ox, Math.min(ox + uw - w, x0));
            z0 = Math.max(mf, Math.min(mf + ud - d, z0));
            q.x0 = x0;
            q.z0 = z0;
            q.x1 = x0 + w - 1;
            q.z1 = z0 + d - 1;
            q.round = part.round();
            if (q.round) {
                int size = Math.min(w, d);
                if (size % 2 == 0) size--;
                size = Math.max(5, size);
                q.r = (size - 1) / 2.0;
                q.cx = x0 + (w - 1) / 2.0;
                q.cz = z0 + (d - 1) / 2.0;
                q.x0 = (int) Math.floor(q.cx - q.r);
                q.x1 = (int) Math.ceil(q.cx + q.r);
                q.z0 = (int) Math.floor(q.cz - q.r);
                q.z1 = (int) Math.ceil(q.cz + q.r);
            }
            q.b = part.base();
            q.n = part.floors();
            q.ridgeX = w >= d;
            String roof = part.roof() != null ? part.roof() : defRoof;
            if (q.round) {
                if (roof.equals("gable") || roof.equals("hip")) roof = "cone";
            } else if (roof.equals("cone") || roof.equals("dome")) {
                roof = "hip";
            }
            q.roof = roof;
            vols.add(q);
        }
        if (vols.isEmpty()) return false;

        fh = p.floorHeight();
        // Höhe einpassen: Geschosse verringern, sonst Flachdach
        for (int guard = 0; guard < 80; guard++) {
            boolean ok = true;
            for (Vol q : vols) {
                if (q.top() * fh + roofNeed(q) > H - 1) ok = false;
            }
            if (ok) break;
            if (fh > 4) {
                fh--;
                continue;
            }
            Vol tallest = vols.stream().max(Comparator.comparingInt(Vol::top)).get();
            if (tallest.roof.equals("gable") && roofNeed(tallest) > 8) tallest.roof = "hip";
            else if (tallest.n > 1) tallest.n--;
            else if (tallest.b > 0) tallest.b--;
            else if (!tallest.roof.equals("flat")) tallest.roof = "flat";
            else if (fh > 3) fh--;
            else return false;
        }
        // Gestapelte Teile brauchen Halt: zu wenig Auflage → auf den Boden setzen
        vols.sort(Comparator.comparingInt(q -> q.b));
        List<Vol> placed = new ArrayList<>();
        for (Vol q : vols) {
            if (q.b > 0) {
                int total = 0, supported = 0;
                for (int x = q.x0; x <= q.x1; x++)
                    for (int z = q.z0; z <= q.z1; z++) {
                        if (!q.contains(x, z)) continue;
                        total++;
                        for (Vol o : placed) {
                            if (o.top() == q.b && o.contains(x, z)) {
                                supported++;
                                break;
                            }
                        }
                    }
                if (total == 0 || supported * 10 < total * 6) q.b = 0;
            }
            placed.add(q);
        }
        maxK = 0;
        for (Vol q : vols) maxK = Math.max(maxK, q.top());
        if (maxK * fh + 3 > H - 1) return false;
        lev = new boolean[maxK][W][D];
        topVol = new int[W][D];
        int[][] topLevel = new int[W][D];
        for (int[] row : topVol) java.util.Arrays.fill(row, -1);
        for (int i = 0; i < vols.size(); i++) {
            Vol q = vols.get(i);
            for (int x = Math.max(0, q.x0); x <= Math.min(W - 1, q.x1); x++)
                for (int z = Math.max(0, q.z0); z <= Math.min(D - 1, q.z1); z++) {
                    if (!q.contains(x, z)) continue;
                    for (int k = q.b; k < q.top(); k++) lev[k][x][z] = true;
                    if (q.top() > topLevel[x][z] || topVol[x][z] < 0) {
                        topLevel[x][z] = q.top();
                        topVol[x][z] = i;
                    }
                }
        }
        wallB = p.wall();
        groundWallB = p.groundWall();
        frameB = p.frame();
        return true;
    }

    // ------------------------------------------------------------------ Hülle

    private boolean inLev(int k, int x, int z) {
        return k >= 0 && k < maxK && x >= 0 && z >= 0 && x < W && z < D && lev[k][x][z];
    }

    private int openMask(int k, int x, int z) {
        int m = 0;
        for (Dir d : Dir.values()) if (!inLev(k, x + d.dx, z + d.dz)) m |= 1 << d.ordinal();
        return m;
    }

    private static boolean has(int mask, Dir d) {
        return (mask & (1 << d.ordinal())) != 0;
    }

    private boolean occ(int x, int y, int z) {
        if (y < 1 || x < 0 || z < 0 || x >= W || z >= D) return false;
        int k = (y - 1) / fh;
        return k < maxK && lev[k][x][z] && y <= (k + 1) * fh;
    }

    private void place(int x, int y, int z, String state) {
        if (!v.in(x, y, z) || occ(x, y, z)) return;
        v.setIfEmpty(x, y, z, state);
    }

    private String floorTile(int x, int z) {
        return B.id(p.floor().block());
    }

    private boolean flatTop(int x, int z) {
        int i = topVol[x][z];
        return i >= 0 && vols.get(i).roof.equals("flat");
    }

    private void shell() {
        for (int k = 0; k < maxK; k++) {
            int y0 = k * fh;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z]) continue;
                    int mask = openMask(k, x, z);
                    boolean wall = mask != 0;
                    boolean corner = Integer.bitCount(mask) >= 2;
                    char axis = (has(mask, Dir.NORTH) || has(mask, Dir.SOUTH)) ? 'x' : 'z';
                    if (k == 0) {
                        v.set(x, 0, z, wall ? B.id(p.foundation()) : floorTile(x, z));
                    } else {
                        v.set(x, y0, z, wall ? B.axis(frameB, axis) : floorTile(x, z));
                    }
                    int t = (axis == 'x') ? x : z;
                    for (int y = y0 + 1; y <= y0 + fh - 1; y++) {
                        if (!wall) {
                            v.set(x, y, z, B.AIR);
                            continue;
                        }
                        boolean post = corner || (p.timber() && Math.floorMod(t, 4) == 0);
                        if (post) v.set(x, y, z, B.axis(frameB, 'y'));
                        else if (k == 0 && y == 1) v.set(x, y, z, s.vary(groundWallB));
                        else v.set(x, y, z, s.vary(wallB));
                    }
                    if (!inLev(k + 1, x, z)) {
                        int yt = (k + 1) * fh;
                        if (wall) v.set(x, yt, z, p.timber() ? B.axis(frameB, axis) : B.id(p.trim().block()));
                        else v.set(x, yt, z, flatTop(x, z) ? B.id(p.roof().block()) : floorTile(x, z));
                    }
                }
        }
    }

    // ------------------------------------------------------------------ Fenster

    private boolean straight(int k, int x, int z, int mask) {
        return inLev(k, x, z) && openMask(k, x, z) == mask;
    }

    private void windows() {
        boolean big = p.bigWindows();
        boolean shutters = p.shutters() || spec.has(BuildingSpec.Feature.SHUTTERS);
        for (int k = 0; k < maxK; k++) {
            int y0 = k * fh;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z]) continue;
                    int mask = openMask(k, x, z);
                    if (Integer.bitCount(mask) != 1) continue;
                    Dir out = Dir.values()[Integer.numberOfTrailingZeros(mask)];
                    int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
                    if (!straight(k, x - ax, z - az, mask) || !straight(k, x + ax, z + az, mask)) continue;
                    int t = ax == 1 ? x : z;
                    String pane = B.id(p.window());
                    if (big) {
                        if (Math.floorMod(t, 4) == 0) continue;
                        for (int y = y0 + 1; y <= y0 + fh - 1; y++) v.set(x, y, z, pane);
                        continue;
                    }
                    if (Math.floorMod(t, 3) != 1) continue;
                    int yHi = fh >= 4 ? y0 + 3 : y0 + 2;
                    for (int y = y0 + 2; y <= yHi; y++) v.set(x, y, z, pane);
                    // Sims unter dem Fenster
                    if (fh >= 4) v.set(x, y0 + 1, z, B.stairs(p.trim(), out, true));
                    if (shutters) {
                        for (int sgn : new int[] {-1, 1}) {
                            int nx = x + sgn * ax, nz = z + sgn * az;
                            if (!straight(k, nx, nz, mask)) continue;
                            for (int y = y0 + 2; y <= yHi; y++) {
                                place(nx + out.dx, y, nz + out.dz, B.trapdoor(p.trapdoor(), out, false, true));
                            }
                        }
                    }
                }
        }
    }

    // ------------------------------------------------------------------ Tür

    private void door() {
        int bestScore = Integer.MAX_VALUE;
        int cxm = W / 2;
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                if (!lev[0][x][z]) continue;
                int mask = openMask(0, x, z);
                if (Integer.bitCount(mask) != 1) continue;
                Dir out = Dir.values()[Integer.numberOfTrailingZeros(mask)];
                int bx = x - out.dx, bz = z - out.dz;
                if (!inLev(0, bx, bz) || openMask(0, bx, bz) != 0) continue;
                int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
                if (!straight(0, x - ax, z - az, mask) || !straight(0, x + ax, z + az, mask)) continue;
                // Nordseite bevorzugen, dann möglichst weit vorn und mittig
                int score = (out == Dir.NORTH ? 0 : 1000) + z * 4 + Math.abs(x - cxm);
                if (score < bestScore) {
                    bestScore = score;
                    doorX = x;
                    doorZ = z;
                    doorDir = out;
                }
            }
        if (doorX < 0) return;
        v.set(doorX, 1, doorZ, B.door(p.door(), doorDir.opposite(), false));
        v.set(doorX, 2, doorZ, B.AIR);
        if (fh >= 4) v.set(doorX, 3, doorZ, B.id(p.trim().block()));
        int ax = doorDir.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
        for (int sgn : new int[] {-1, 1}) {
            int wx = doorX + sgn * ax, wz = doorZ + sgn * az;
            if (straight(0, wx, wz, 1 << doorDir.ordinal())) {
                place(wx + doorDir.dx, 2, wz + doorDir.dz, B.wallTorch(doorDir));
            }
        }
        s.reserve(doorX - doorDir.dx, 1, doorZ - doorDir.dz);
        s.reserve(doorX - 2 * doorDir.dx, 1, doorZ - 2 * doorDir.dz);
    }

    // ------------------------------------------------------------------ Leitern

    private void ladders() {
        for (int k = 1; k < maxK; k++) {
            List<int[]> cand = new ArrayList<>();
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z] || !lev[k - 1][x][z]) continue;
                    if (openMask(k, x, z) != 0 || openMask(k - 1, x, z) != 0) continue;
                    if (Math.abs(x - doorX) + Math.abs(z - doorZ) <= 2) continue;
                    for (Dir w : Dir.values()) {
                        int wx = x + w.dx, wz = z + w.dz;
                        if (inLev(k, wx, wz) && openMask(k, wx, wz) != 0 && inLev(k - 1, wx, wz) && openMask(k - 1, wx, wz) != 0) {
                            cand.add(new int[] {x, z, w.ordinal()});
                            break;
                        }
                    }
                }
            if (cand.isEmpty()) continue;
            int[] c = cand.get(rng.nextInt(cand.size()));
            Dir w = Dir.values()[c[2]];
            for (int y = (k - 1) * fh + 1; y <= k * fh + 1; y++) v.set(c[0], y, c[1], B.ladder(w.opposite()));
            for (int y : new int[] {(k - 1) * fh + 1, k * fh + 1}) {
                s.reserve(c[0], y, c[1]);
                s.reserve(c[0] - w.dx, y, c[1] - w.dz);
            }
        }
    }

    // ------------------------------------------------------------------ Dächer

    private void roofs() {
        List<Vol> order = new ArrayList<>(vols);
        order.sort(Comparator.comparingInt((Vol q) -> q.top()).reversed());
        for (Vol q : order) {
            int yt = q.top() * fh;
            switch (q.roof) {
                case "flat" -> parapet(q, yt);
                case "hip" -> hip(q, yt);
                case "cone" -> cone(q, yt, true);
                case "dome" -> cone(q, yt, false);
                default -> gable(q, yt);
            }
        }
    }

    private void parapet(Vol q, int yt) {
        boolean modern = spec.style() == Palette.Style.MODERN;
        for (int x = q.x0; x <= q.x1; x++)
            for (int z = q.z0; z <= q.z1; z++) {
                if (!q.contains(x, z) || !inLev(q.top() - 1, x, z) || openMask(q.top() - 1, x, z) == 0) continue;
                if (inLev(q.top(), x, z)) continue;
                if (modern) place(x, yt + 1, z, B.slab(p.trim(), true));
                else if (((x + z) & 1) == 0) place(x, yt + 1, z, B.id(wallB));
            }
        if (q.round) return;
    }

    private void gable(Vol q, int yt) {
        Materials.Family r = p.roof();
        int ov = 1;
        if (q.ridgeX) {
            for (int i = 0; ; i++) {
                int za = q.z0 - ov + i, zb = q.z1 + ov - i, y = yt + 1 + i;
                if (za > zb || y >= H) break;
                for (int x = q.x0 - ov; x <= q.x1 + ov; x++) {
                    if (za == zb) {
                        place(x, y, za, B.id(r.block()));
                        place(x, y + 1, za, B.slab(r, false));
                    } else {
                        place(x, y, za, B.stairs(r, Dir.SOUTH, false));
                        place(x, y, zb, B.stairs(r, Dir.NORTH, false));
                    }
                }
                if (i >= 1) for (int z = za + 1; z < zb; z++) {
                    place(q.x0, y - 1, z, s.vary(wallB));
                    place(q.x1, y - 1, z, s.vary(wallB));
                }
            }
        } else {
            for (int i = 0; ; i++) {
                int xa = q.x0 - ov + i, xb = q.x1 + ov - i, y = yt + 1 + i;
                if (xa > xb || y >= H) break;
                for (int z = q.z0 - ov; z <= q.z1 + ov; z++) {
                    if (xa == xb) {
                        place(xa, y, z, B.id(r.block()));
                        place(xa, y + 1, z, B.slab(r, false));
                    } else {
                        place(xa, y, z, B.stairs(r, Dir.EAST, false));
                        place(xb, y, z, B.stairs(r, Dir.WEST, false));
                    }
                }
                if (i >= 1) for (int x = xa + 1; x < xb; x++) {
                    place(x, y - 1, q.z0, s.vary(wallB));
                    place(x, y - 1, q.z1, s.vary(wallB));
                }
            }
        }
        // Giebelfüllung: die Dreiecke an den Stirnseiten über der Wand
        gableInfill(q, yt);
    }

    private void gableInfill(Vol q, int yt) {
        if (q.ridgeX) {
            int half = (q.z1 - q.z0) / 2;
            for (int i = 1; i <= half; i++)
                for (int z = q.z0 + i; z <= q.z1 - i; z++) {
                    place(q.x0, yt + i, z, s.vary(wallB));
                    place(q.x1, yt + i, z, s.vary(wallB));
                }
        } else {
            int half = (q.x1 - q.x0) / 2;
            for (int i = 1; i <= half; i++)
                for (int x = q.x0 + i; x <= q.x1 - i; x++) {
                    place(x, yt + i, q.z0, s.vary(wallB));
                    place(x, yt + i, q.z1, s.vary(wallB));
                }
        }
    }

    private void hip(Vol q, int yt) {
        Materials.Family r = p.roof();
        int ov = 1;
        int xLo = q.x0 - ov, xHi = q.x1 + ov, zLo = q.z0 - ov, zHi = q.z1 + ov;
        int maxLayers = Math.max(2, H - 3 - yt);
        for (int i = 0; ; i++) {
            int ax = xLo + i, bx = xHi - i, az = zLo + i, bz = zHi - i, y = yt + 1 + i;
            if (ax > bx || az > bz || y >= H) break;
            if (ax == bx || az == bz || i == maxLayers) {
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        place(x, y, z, B.id(r.block()));
                        place(x, y + 1, z, B.slab(r, false));
                    }
                break;
            }
            for (int x = ax; x <= bx; x++) {
                place(x, y, az, B.stairs(r, Dir.SOUTH, false));
                place(x, y, bz, B.stairs(r, Dir.NORTH, false));
            }
            for (int z = az + 1; z < bz; z++) {
                place(ax, y, z, B.stairs(r, Dir.EAST, false));
                place(bx, y, z, B.stairs(r, Dir.WEST, false));
            }
        }
    }

    /** Kegel- oder Kuppeldach über einem runden Teil: abnehmende Kreisscheiben mit Spitze. */
    private void cone(Vol q, int yt, boolean cone) {
        Materials.Family r = p.roof();
        int R = (int) Math.round(q.r) + 1;
        int avail = H - 2 - yt;
        int hgt = cone ? Math.min((int) (q.r * 1.5) + 2, avail) : Math.min(R, avail);
        if (hgt < 2) {
            parapet(q, yt);
            return;
        }
        for (int i = 0; i < hgt; i++) {
            double frac = (double) i / hgt;
            double ri = cone ? R * (1.0 - frac) : R * Math.sqrt(Math.max(0, 1 - frac * frac));
            int y = yt + 1 + i;
            for (int x = (int) Math.floor(q.cx - R); x <= (int) Math.ceil(q.cx + R); x++)
                for (int z = (int) Math.floor(q.cz - R); z <= (int) Math.ceil(q.cz + R); z++) {
                    double dx = x - q.cx, dz = z - q.cz;
                    if (dx * dx + dz * dz > (ri + 0.2) * (ri + 0.2)) continue;
                    place(x, y, z, B.id(r.block()));
                }
        }
        int cxI = (int) Math.round(q.cx), czI = (int) Math.round(q.cz);
        place(cxI, yt + 1 + hgt, czI, B.id(p.fence()));
        place(cxI, yt + 2 + hgt, czI, B.with("end_rod", "facing", "up"));
    }

    // ------------------------------------------------------------------ Einrichtung

    private void furnish() {
        List<String> rooms = spec.rooms().isEmpty() ? List.of("living", "kitchen", "bedroom", "library", "dining", "bedroom")
            : spec.rooms();
        int idx = 0;
        for (Vol q : vols) {
            int ax0, az0, ax1, az1;
            if (q.round) {
                int half = (int) Math.max(1, Math.floor(q.r * 0.65));
                ax0 = (int) Math.round(q.cx) - half;
                ax1 = (int) Math.round(q.cx) + half;
                az0 = (int) Math.round(q.cz) - half;
                az1 = (int) Math.round(q.cz) + half;
            } else {
                ax0 = q.x0 + 1;
                az0 = q.z0 + 1;
                ax1 = q.x1 - 1;
                az1 = q.z1 - 1;
            }
            if (ax1 - ax0 < 2 || az1 - az0 < 2) continue;
            for (int k = q.b; k < q.top(); k++) {
                String type = rooms.get(idx % rooms.size());
                idx++;
                if (!spec.has(BuildingSpec.Feature.INTERIOR)) continue;
                s.furnish(new Site.Room(ax0, az0, ax1, az1, k * fh + 1, (k + 1) * fh, type));
            }
        }
    }

    // ------------------------------------------------------------------ Außenanlagen

    private boolean farFromBuilding(int x, int z, int margin) {
        for (int dx = -margin; dx <= margin; dx++)
            for (int dz = -margin; dz <= margin; dz++) {
                if (inLev(0, x + dx, z + dz)) return false;
            }
        return true;
    }

    private void exterior() {
        if (doorX >= 0) {
            int ax = doorDir.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
            for (int i = 1; i < 64; i++) {
                int x = doorX + doorDir.dx * i, z = doorZ + doorDir.dz * i;
                if (!v.in(x, 0, z)) break;
                for (int w = -1; w <= 1; w++) {
                    int px = x + w * ax, pz = z + w * az;
                    if (v.in(px, 0, pz) && !inLev(0, px, pz)) v.set(px, 0, pz, B.id(p.path()));
                }
            }
            if (spec.has(BuildingSpec.Feature.LANTERNS) && doorDir == Dir.NORTH && doorZ >= 4) s.lanternPosts(doorX - 1, doorZ - 3, 3);
        }
        if (spec.has(BuildingSpec.Feature.FENCE) && mf >= 2 && doorX >= 0) s.fence(Math.max(0, doorX - 1), 3);
        if (spec.has(BuildingSpec.Feature.TREES) || spec.has(BuildingSpec.Feature.GARDEN)) {
            int[][] spots = {{2, mf + 2}, {W - 3, mf + 2}, {2, D - 3}, {W - 3, D - 3}, {W / 2, D - 2}};
            for (int[] t : spots) if (farFromBuilding(t[0], t[1], 4) && rng.chance(0.75)) s.tree(t[0], t[1], 3 + rng.nextInt(2));
        }
        if (spec.has(BuildingSpec.Feature.GARDEN)) s.flowersAndBushes(0, 0, W - 1, D - 1, 0.06, 0.02);
    }
}
