package de.localai.baumeister.arch;

import java.util.ArrayDeque;
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
    /** Treppen: je Geschoss „Treppe statt Leiter“ und belegte Zellen je Ebene (1 = Lauf/Geländer, 2 = Zugang/Austritt). */
    private boolean[] stairLevel;
    private byte[][][] stairMark;
    // Stein-/Festungsdetails (Zinnen, Strebepfeiler, Schießscharten, Spitzbogenfenster, Sockel, Banner)
    private boolean stone, fort, classic, noTimber;
    /** Fassaden-/Vorbau-Details und Zweistoff-Dach für Holz- und Fachwerkhäuser (siehe {@link FacadeDetail}). */
    private boolean rich;

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
        rich = !stone && FacadeDetail.applies(spec.style());
        shell();
        pickDoor();
        windows();
        door();
        stairs();
        ladders();
        roofs();
        roofDetails();
        furnish();
        exterior();
        if (stone) stoneExterior();
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
        detectStone();
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
                    boolean clean = stone && wall && roundCell(k, x, z); // glatte Turmwand ohne Pfosten/Ringe
                    char axis = (has(mask, Dir.NORTH) || has(mask, Dir.SOUTH)) ? 'x' : 'z';
                    if (k == 0) {
                        v.set(x, 0, z, wall ? B.id(p.foundation()) : floorTile(x, z));
                    } else {
                        v.set(x, y0, z, clean ? s.vary(wallB) : wall ? B.axis(frameB, axis) : floorTile(x, z));
                    }
                    int t = (axis == 'x') ? x : z;
                    for (int y = y0 + 1; y <= y0 + fh - 1; y++) {
                        if (!wall) {
                            v.set(x, y, z, B.AIR);
                            continue;
                        }
                        boolean post = !clean && (corner || (p.timber() && !noTimber && Math.floorMod(t, 4) == 0));
                        if (post) v.set(x, y, z, B.axis(frameB, 'y'));
                        else if (k == 0 && y == 1) v.set(x, y, z, s.vary(groundWallB));
                        else v.set(x, y, z, s.vary(wallB));
                    }
                    if (!inLev(k + 1, x, z)) {
                        int yt = (k + 1) * fh;
                        if (wall) v.set(x, yt, z, clean ? s.vary(wallB) : p.timber() ? B.axis(frameB, axis) : B.id(p.trim().block()));
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
        if (stone) {
            stoneWindows();
            return;
        }
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

    /** Wählt die Haustür-Zelle (nur lesend), damit Fenster und Details Abstand zur Tür halten können. */
    private void pickDoor() {
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
    }

    private void door() {
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
        // Fensterläden eines Nachbarfensters dürfen den Eingang nicht zustellen
        for (int y = 1; y <= 3; y++) {
            String at = v.get(doorX + doorDir.dx, y, doorZ + doorDir.dz);
            if (at != null && B.name(at).endsWith("_trapdoor")) v.set(doorX + doorDir.dx, y, doorZ + doorDir.dz, B.AIR);
        }
        if (stone) stoneDoor();
        s.reserve(doorX - doorDir.dx, 1, doorZ - doorDir.dz);
        s.reserve(doorX - 2 * doorDir.dx, 1, doorZ - 2 * doorDir.dz);
    }

    // ------------------------------------------------------------------ Treppen

    /**
     * Gerade Treppenläufe zwischen den Geschossen: 1 Block breit, {@code fh} Stufen entlang einer Innenwand.
     * Die Stufe i liegt auf Höhe (k-1)*fh+1+i, die letzte ersetzt die Decke des oberen Geschosses. Über den
     * übrigen Stufen wird die Zwischendecke ausgeschnitten und mit einem Geländer gesichert. Wo kein Lauf passt,
     * bleibt es bei der Leiter.
     */
    private void stairs() {
        stairLevel = new boolean[maxK];
        stairMark = new byte[maxK][W][D];
        for (int k = 1; k < maxK; k++) stairLevel[k] = stairRun(k);
    }

    private boolean stairRun(int k) {
        int base = (k - 1) * fh;
        int ySlab = k * fh;
        List<int[]> cands = new ArrayList<>();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                for (Dir d : Dir.values()) {
                    int score = scoreRun(k, x, z, d);
                    if (score == Integer.MIN_VALUE) continue;
                    cands.add(new int[] {score * 8 + rng.nextInt(8), x, z, d.ordinal()});
                }
        cands.sort((p1, p2) -> Integer.compare(p2[0], p1[0]));
        int[] best = null;
        // beste Läufe zuerst; der Lauf darf die begehbare Fläche eines Geschosses nicht zerschneiden
        for (int[] c : cands) {
            if (!keepsConnected(k, c[1], c[2], Dir.values()[c[3]])) continue;
            best = new int[] {c[1], c[2], c[3]};
            break;
        }
        if (best == null) return false;
        Dir d = Dir.values()[best[2]];
        Dir sideA = d.clockwise(), sideB = d.counterClockwise();
        Materials.Family fam = p.floor();
        String fence = B.id(p.fence());
        for (int i = 0; i < fh; i++) {
            int cx = best[0] + i * d.dx, cz = best[1] + i * d.dz;
            v.set(cx, base + 1 + i, cz, B.stairs(fam, d, false));
            if (i == fh - 1) {
                // die letzte Stufe ersetzt die Decke und liegt schon auf dem oberen Boden
                for (int y = ySlab + 1; y <= ySlab + Math.min(2, fh - 1); y++) v.set(cx, y, cz, B.AIR);
            } else {
                for (int y = base + 2 + i; y <= ySlab; y++) v.set(cx, y, cz, B.AIR);
                // Geländer an den offenen Seiten des Ausschnitts (nicht an Wänden)
                for (Dir sd : new Dir[] {sideA, sideB}) {
                    int sx = cx + sd.dx, sz = cz + sd.dz;
                    if (inLev(k, sx, sz) && openMask(k, sx, sz) == 0) {
                        v.set(sx, ySlab + 1, sz, fence);
                        if (stairMark[k][sx][sz] == 0) stairMark[k][sx][sz] = 1;
                    }
                }
            }
            stairMark[k - 1][cx][cz] = 1;
            stairMark[k][cx][cz] = 1;
            for (int y = base + 1; y <= ySlab + 2; y++) s.reserve(cx, y, cz);
        }
        // Absturzsicherung am unteren Ende des Ausschnitts
        int bx = best[0] - d.dx, bz = best[1] - d.dz;
        if (openMask(k, bx, bz) == 0) {
            v.set(bx, ySlab + 1, bz, fence);
            stairMark[k][bx][bz] = 1;
        }
        // Zugang unten und Austritt oben freihalten
        int ex = best[0] + fh * d.dx, ez = best[1] + fh * d.dz;
        if (stairMark[k - 1][bx][bz] == 0) stairMark[k - 1][bx][bz] = 2;
        if (stairMark[k][ex][ez] == 0) stairMark[k][ex][ez] = 2;
        for (int y = base + 1; y <= ySlab + 2; y++) {
            s.reserve(bx, y, bz);
            s.reserve(ex, y, ez);
        }
        for (int y = ySlab + 1; y <= ySlab + 2; y++) {
            for (int i = 0; i < fh; i++)
                for (Dir sd : new Dir[] {sideA, sideB}) s.reserve(best[0] + i * d.dx + sd.dx, y, best[1] + i * d.dz + sd.dz);
        }
        return true;
    }

    /** Zählt die zusammenhängenden freien Innenzellen eines Geschosses; belegt sind Läufe, Geländer und ggf. ein neuer Lauf. */
    private int components(int level, boolean[][] extraBlocked) {
        boolean[][] seen = new boolean[W][D];
        int comps = 0;
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                if (seen[x][z] || !freeCell(level, x, z, extraBlocked)) continue;
                comps++;
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[] {x, z});
                seen[x][z] = true;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    for (Dir d : Dir.values()) {
                        int nx = c[0] + d.dx, nz = c[1] + d.dz;
                        if (nx < 0 || nz < 0 || nx >= W || nz >= D || seen[nx][nz] || !freeCell(level, nx, nz, extraBlocked)) continue;
                        seen[nx][nz] = true;
                        q.add(new int[] {nx, nz});
                    }
                }
            }
        return comps;
    }

    private boolean freeCell(int level, int x, int z, boolean[][] extra) {
        return inLev(level, x, z) && openMask(level, x, z) == 0 && stairMark[level][x][z] != 1 && !(extra != null && extra[x][z]);
    }

    private boolean keepsConnected(int k, int x, int z, Dir d) {
        Dir sideA = d.clockwise(), sideB = d.counterClockwise();
        boolean[][] low = new boolean[W][D], up = new boolean[W][D];
        for (int i = -1; i < fh; i++) {
            int cx = x + i * d.dx, cz = z + i * d.dz;
            if (i >= 0 && i <= 1) low[cx][cz] = true;
            if (i < fh - 1) up[cx][cz] = true;
            if (i >= 0 && i < fh - 1) {
                for (Dir sd : new Dir[] {sideA, sideB}) {
                    int sx = cx + sd.dx, sz = cz + sd.dz;
                    if (inLev(k, sx, sz) && openMask(k, sx, sz) == 0) up[sx][sz] = true;
                }
            }
        }
        return components(k - 1, low) <= components(k - 1, null) && components(k, up) <= components(k, null);
    }

    /** Bewertung eines möglichen Laufs ab (x,z) in Richtung d; {@link Integer#MIN_VALUE} wenn er nicht passt. */
    private int scoreRun(int k, int x, int z, Dir d) {
        int wallSides = 0;
        Dir sideA = d.clockwise(), sideB = d.counterClockwise();
        for (int i = -1; i <= fh; i++) {
            int cx = x + i * d.dx, cz = z + i * d.dz;
            boolean core = i >= 0 && i < fh;
            boolean entry = i == -1;
            // Zugang (unten) und Austritt (oben) müssen begehbare Innenzellen sein
            if (entry) {
                if (!inLev(k - 1, cx, cz) || openMask(k - 1, cx, cz) != 0 || stairMark[k - 1][cx][cz] == 1) return Integer.MIN_VALUE;
                if (!inLev(k, cx, cz) || stairMark[k][cx][cz] != 0) return Integer.MIN_VALUE;
            } else if (!core) {
                if (!inLev(k, cx, cz) || openMask(k, cx, cz) != 0 || stairMark[k][cx][cz] == 1) return Integer.MIN_VALUE;
            } else {
                if (!inLev(k - 1, cx, cz) || !inLev(k, cx, cz)) return Integer.MIN_VALUE;
                if (openMask(k - 1, cx, cz) != 0 || openMask(k, cx, cz) != 0) return Integer.MIN_VALUE;
                if (stairMark[k - 1][cx][cz] != 0 || stairMark[k][cx][cz] != 0) return Integer.MIN_VALUE;
                if (k == 1 && Math.abs(cx - doorX) + Math.abs(cz - doorZ) <= 2) return Integer.MIN_VALUE;
                for (Dir sd : new Dir[] {sideA, sideB}) {
                    int sx = cx + sd.dx, sz = cz + sd.dz;
                    if (!inLev(k - 1, sx, sz) || !inLev(k, sx, sz)) return Integer.MIN_VALUE;
                    boolean wallLow = openMask(k - 1, sx, sz) != 0, wallUp = openMask(k, sx, sz) != 0;
                    if (wallLow != wallUp) return Integer.MIN_VALUE;
                    if (wallLow) wallSides++;
                    // Geländerzellen dürfen nicht schon belegt sein
                    else if (i < fh - 1 && stairMark[k][sx][sz] != 0) return Integer.MIN_VALUE;
                }
            }
            if (k == 1 && entry && cx == doorX - doorDir.dx && cz == doorZ - doorDir.dz) return Integer.MIN_VALUE;
        }
        // Wandlauf (Wand auf einer Seite) bevorzugen, nicht quer durch den Raum
        int score = wallSides;
        if (wallSides < fh - 1) score -= 40;
        // nahe an der Tür beginnen wirkt natürlich
        if (doorX >= 0 && k == 1) score -= (Math.abs(x - doorX) + Math.abs(z - doorZ)) / 6;
        return score;
    }

    // ------------------------------------------------------------------ Leitern

    private void ladders() {
        for (int k = 1; k < maxK; k++) {
            if (stairLevel != null && stairLevel[k]) continue;
            List<int[]> cand = new ArrayList<>();
            // 2. Durchgang: Zugangs-/Austrittszellen einer Treppe notfalls mitbenutzen
            for (int pass = 0; pass < 2 && cand.isEmpty(); pass++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z] || !lev[k - 1][x][z]) continue;
                    if (openMask(k, x, z) != 0 || openMask(k - 1, x, z) != 0) continue;
                    if (Math.abs(x - doorX) + Math.abs(z - doorZ) <= 2) continue;
                    if (stairMark != null && (pass == 0 ? stairMark[k][x][z] != 0 || stairMark[k - 1][x][z] != 0 : stairMark[k][x][z] == 1 || stairMark[k - 1][x][z] == 1)) continue;
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
        if (crenelled(q)) {
            crenels(q, yt);
            return;
        }
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
        if (stone) roseWindows(q, yt);
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
            if (i == maxLayers && bx - ax >= 3 && bz - az >= 3 && y + 1 < H) {
                hipDeck(r, ax, bx, az, bz, y);
                break;
            }
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

    /** Gekappte Walmdach-Spitze: statt einer nackten Platte ein flaches Dach mit umlaufender Brüstung und Eckpfosten. */
    private void hipDeck(Materials.Family r, int ax, int bx, int az, int bz, int y) {
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                place(x, y, z, B.id(r.block()));
                boolean rim = x == ax || x == bx || z == az || z == bz;
                boolean corner = (x == ax || x == bx) && (z == az || z == bz);
                if (corner) place(x, y + 1, z, B.id(p.fence()));
                else if (rim) place(x, y + 1, z, B.slab(r, false));
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

    // ------------------------------------------------------------------ Dachdetails (Traufe, First, Gauben, Schornstein)

    private int chimX = -1, chimZ = -1;

    /** Zelle auf einer Dachseite: u entlang des Firsts, d = Abstand von der Wandebene nach außen, side -1/+1. */
    private int[] slopeCell(Vol q, int side, int u, int d) {
        if (q.ridgeX) return new int[] {u, side < 0 ? q.z0 - d : q.z1 + d};
        return new int[] {side < 0 ? q.x0 - d : q.x1 + d, u};
    }

    private Dir slopeOut(Vol q, int side) {
        if (q.ridgeX) return side < 0 ? Dir.NORTH : Dir.SOUTH;
        return side < 0 ? Dir.WEST : Dir.EAST;
    }

    private boolean gableVol(Vol q) {
        return !q.round && !q.roof.equals("flat") && !q.roof.equals("hip") && !q.roof.equals("cone") && !q.roof.equals("dome");
    }

    /** Höchster belegter Dach-Block eines Giebeldachs (Firstplatte eingerechnet). */
    private int gableTop(Vol q) {
        int span = (q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0) + 1;
        return q.top() * fh + 1 + (span + 1) / 2 + (span % 2 == 1 ? 1 : 0);
    }

    private void roofDetails() {
        for (Vol q : vols) {
            if (q.round) continue;
            int yt = q.top() * fh;
            if (gableVol(q)) {
                eaves(q, yt, true);
                ridgeCaps(q, yt);
            } else if (q.roof.equals("hip")) {
                eaves(q, yt, false);
            }
        }
        if (spec.has(BuildingSpec.Feature.CHIMNEY)) chimney();
        for (Vol q : vols) if (gableVol(q)) dormers(q, q.top() * fh);
        if (rich) roofBands();
    }

    /** Traufgesims: Platten unter dem Dachüberstand (Giebeldach nur an den Traufseiten, Walmdach ringsum). */
    private void eaves(Vol q, int yt, boolean gableOnly) {
        String stairName = B.name(p.roof().stairs());
        for (int x = q.x0 - 1; x <= q.x1 + 1; x++)
            for (int z = q.z0 - 1; z <= q.z1 + 1; z++) {
                boolean xe = x < q.x0 || x > q.x1, ze = z < q.z0 || z > q.z1;
                if (!xe && !ze) continue;
                if (gableOnly && (q.ridgeX ? xe : ze)) continue;
                if (!v.in(x, yt + 1, z) || !nameAt(x, yt + 1, z).equals(stairName)) continue;
                if (rich && !(xe && ze)) {
                    rafter(q, x, z, yt, xe, ze);
                    continue;
                }
                place(x, yt, z, B.slab(p.trim(), true));
            }
    }

    /** Sichtbare Sparrenreihe unter der Traufe (Balkenkopf/Klappe im Wechsel) und ein ausgestellter Traufkick. */
    private void rafter(Vol q, int x, int z, int yt, boolean xe, boolean ze) {
        Dir out = ze ? (z < q.z0 ? Dir.NORTH : Dir.SOUTH) : (x < q.x0 ? Dir.WEST : Dir.EAST);
        int idx = ze ? x : z;
        char depth = out.wallAxis() == 'x' ? 'z' : 'x';
        place(x, yt, z, (idx & 1) == 0 ? B.axis(frameB, depth) : B.trapdoor(p.trapdoor(), out, true, false));
        place(x + out.dx, yt, z + out.dz, B.stairs(p.roof(), out.opposite(), false));
    }

    /** Zweistoff-Dach: jede dritte Stufenreihe der Dachflächen aus einem zweiten Baustoff (Holz mit Sandsteinband). */
    private void roofBands() {
        for (Vol q : vols) {
            if (q.round || !(gableVol(q) || q.roof.equals("hip"))) continue;
            FacadeDetail.roofBands(v, p, q.x0 - 1, q.z0 - 1, q.x1 + 1, q.z1 + 1, q.top() * fh);
        }
    }

    /** First-Abschluss: an beiden Giebelenden ein erhöhter Zierstein; bei langen Dächern zusätzlich eine Firstkrone. */
    private void ridgeCaps(Vol q, int yt) {
        Materials.Family r = p.roof();
        int span = (q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0) + 1;
        int len = (q.ridgeX ? q.x1 - q.x0 : q.z1 - q.z0) + 1;
        if (span % 2 == 0 || span < 5 || len < 7) return;
        int yr = yt + 1 + (span + 1) / 2;
        if (yr + 2 >= H) return;
        int mid = q.ridgeX ? (q.z0 + q.z1) / 2 : (q.x0 + q.x1) / 2;
        int lo = (q.ridgeX ? q.x0 : q.z0) - 1, hi = (q.ridgeX ? q.x1 : q.z1) + 1;
        String ridge = B.id(r.block());
        for (int u : new int[] {lo, hi}) {
            int x = q.ridgeX ? u : mid, z = q.ridgeX ? mid : u;
            if (!ridge.equals(v.get(x, yr, z))) continue;
            v.set(x, yr + 1, z, ridge);
            if (v.isEmpty(x, yr + 2, z)) v.set(x, yr + 2, z, B.slab(r, false));
        }
        // Firstkrone: bei langen Dächern ragt jede vierte Zelle als voller Block aus der Firstplatte
        if (len >= 13) {
            for (int u = lo + 4; u < hi - 3; u += 4) {
                int x = q.ridgeX ? u : mid, z = q.ridgeX ? mid : u;
                if (ridge.equals(v.get(x, yr, z)) && B.slab(r, false).equals(v.get(x, yr + 1, z))) v.set(x, yr + 1, z, ridge);
            }
        }
    }

    /** Gauben: kleine Giebelfenster auf den beiden Dachflächen großer Giebeldächer. */
    private void dormers(Vol q, int yt) {
        Materials.Family r = p.roof();
        int span = (q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0) + 1;
        int len = (q.ridgeX ? q.x1 - q.x0 : q.z1 - q.z0) + 1;
        if (span < 9 || len < 9) return;
        int last = (span + 1) / 2;
        int j = Math.max(1, last - 3);
        if (j + 2 > last) return;
        int uLo = q.ridgeX ? q.x0 : q.z0;
        int n = Math.max(1, (len - 2) / 6);
        Dir lat = q.ridgeX ? Dir.EAST : Dir.SOUTH;
        for (int side = -1; side <= 1; side += 2) {
            Dir out = slopeOut(q, side);
            for (int k = 0; k < n; k++) {
                int ud = uLo + Math.round((k + 1) * (len - 1) / (float) (n + 1));
                if (!dormerFits(q, side, ud, j, yt, out)) continue;
                int y = yt + 1 + j;
                int[] c = slopeCell(q, side, ud, 1 - j), ci = slopeCell(q, side, ud, -j);
                v.set(c[0], y + 1, c[1], B.id(p.window()));
                v.set(ci[0], y + 1, ci[1], B.AIR);
                v.set(c[0], y + 2, c[1], B.id(r.block()));
                v.set(ci[0], y + 2, ci[1], B.id(r.block()));
                for (int sg = -1; sg <= 1; sg += 2) {
                    int[] cl = slopeCell(q, side, ud + sg, 1 - j), cil = slopeCell(q, side, ud + sg, -j);
                    v.set(cl[0], y + 1, cl[1], s.vary(wallB));
                    v.set(cil[0], y + 1, cil[1], s.vary(wallB));
                    Dir face = sg < 0 ? lat : lat.opposite();
                    v.set(cl[0], y + 2, cl[1], B.stairs(r, face, false));
                    v.set(cil[0], y + 2, cil[1], B.stairs(r, face, false));
                }
            }
        }
    }

    /** Prüft, ob an der Stelle wirklich unverbautes Dach dieses Baukörpers liegt (sonst keine Gaube). */
    private boolean dormerFits(Vol q, int side, int ud, int j, int yt, Dir out) {
        int y = yt + 1 + j;
        if (y + 2 >= H) return false;
        String s0 = B.stairs(p.roof(), out.opposite(), false);
        for (int du = -1; du <= 1; du++) {
            int[] a = slopeCell(q, side, ud + du, 1 - j), b = slopeCell(q, side, ud + du, -j), c = slopeCell(q, side, ud + du, -j - 1);
            if (!v.in(a[0], y + 2, a[1]) || !v.in(c[0], y + 2, c[1])) return false;
            if (chimX >= 0 && Math.abs(a[0] - chimX) <= 2 && Math.abs(a[1] - chimZ) <= 2) return false;
            if (!s0.equals(v.get(a[0], y, a[1])) || !s0.equals(v.get(b[0], y + 1, b[1]))) return false;
            if (B.isAir(v.get(c[0], y + 2, c[1]))) return false;
            if (!B.isAir(v.get(a[0], y + 1, a[1])) || !B.isAir(v.get(a[0], y + 2, a[1]))) return false;
            if (!B.isAir(v.get(b[0], y + 2, b[1]))) return false;
        }
        return true;
    }

    /**
     * Schornstein: Mauerwerksschacht vom Boden entlang der Giebelwand (außen) oder, wenn dort kein Platz ist,
     * im Inneren durch das Dach, 2 Blöcke über den First, mit Rauch (Lagerfeuer) und Kamin im Raum darunter.
     */
    private boolean shaftColumn(int x, int z) {
        for (int y = 1; y <= maxK * fh + 1; y++) {
            if (s.isReserved(x, y, z)) return true;
            String at = v.get(x, y, z);
            if (at != null && B.name(at).equals("ladder")) return true;
        }
        return false;
    }

    private void chimney() {
        Vol best = null;
        int bestScore = Integer.MIN_VALUE;
        int[] pick = null;
        for (Vol q : vols) {
            if (q.round || q.b != 0 || !gableVol(q)) continue;
            int span = (q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0) + 1;
            int mid = q.ridgeX ? (q.z0 + q.z1) / 2 : (q.x0 + q.x1) / 2;
            for (int e = -1; e <= 1; e += 2) {
                Dir out = q.ridgeX ? (e < 0 ? Dir.WEST : Dir.EAST) : (e < 0 ? Dir.NORTH : Dir.SOUTH);
                int wx = q.ridgeX ? (e < 0 ? q.x0 : q.x1) : mid, wz = q.ridgeX ? mid : (e < 0 ? q.z0 : q.z1);
                int cx = wx + out.dx, cz = wz + out.dz;
                if (cx < 0 || cz < 0 || cx >= W || cz >= D) continue;
                if (!chimneyColumnFree(cx, cz, out)) continue;
                if (openMask(0, wx, wz) != (1 << out.ordinal())) continue;
                if (Math.abs(wx - doorX) + Math.abs(wz - doorZ) <= 3) continue;
                // Feuerstelle nicht vor/in Treppen- oder Leiterschächten
                if (shaftColumn(wx - out.dx, wz - out.dz) || shaftColumn(wx, wz)) continue;
                int score = span * 2 + (out == Dir.NORTH ? -30 : 0) + (out == Dir.SOUTH ? 10 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = q;
                    pick = new int[] {cx, cz, wx, wz, out.ordinal(), 1};
                }
            }
        }
        if (best == null) {
            // Fallback: Schacht im Inneren, möglichst nah am höchsten Dachpunkt
            int bestH = Integer.MIN_VALUE;
            for (Vol q : vols) {
                if (q.round || q.b != 0 || q.roof.equals("flat") || q.roof.equals("cone") || q.roof.equals("dome")) continue;
                for (int x = q.x0 + 1; x <= q.x1 - 1; x++)
                    for (int z = q.z0 + 1; z <= q.z1 - 1; z++) {
                        if (!inLev(0, x, z) || openMask(0, x, z) != 0 || Math.abs(x - doorX) + Math.abs(z - doorZ) <= 2) continue;
                        if (!innerColumnFree(x, z)) continue;
                        int wallD = Math.min(Math.min(x - q.x0, q.x1 - x), Math.min(z - q.z0, q.z1 - z));
                        int score = columnTop(x, z) * 10 - wallD;
                        if (score > bestH) {
                            bestH = score;
                            best = q;
                            pick = new int[] {x, z, x, z, 0, 0};
                        }
                    }
            }
        }
        if (best == null) return;
        int cx = pick[0], cz = pick[1], wx = pick[2], wz = pick[3];
        boolean outside = pick[5] == 1;
        Dir out = Dir.values()[pick[4]];
        int roofTop = outside ? gableTop(best) : columnTop(cx, cz);
        int top = Math.min(roofTop + 2, H - 2);
        if (top < roofTop + 1) return;
        String stone = p.chimney();
        for (int y = 1; y <= top; y++) {
            if (y == 1 && !outside) continue;
            v.set(cx, y, cz, s.vary(stone));
            s.reserve(cx, y, cz);
        }
        v.set(cx, top + 1, cz, B.campfire());
        chimX = cx;
        chimZ = cz;
        if (outside) {
            int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
            // Sockel: Stufen links, rechts und vor dem Schacht verankern ihn am Boden
            for (int sg = -1; sg <= 1; sg += 2) {
                int sx = cx + sg * ax, sz = cz + sg * az;
                Dir toward = sg < 0 ? (ax == 1 ? Dir.EAST : Dir.SOUTH) : (ax == 1 ? Dir.WEST : Dir.NORTH);
                if (v.in(sx, 1, sz) && B.isAir(v.get(sx, 1, sz))) v.set(sx, 1, sz, B.stairs(p.trim(), toward, false));
            }
            int fx0 = cx + out.dx, fz0 = cz + out.dz;
            if (v.in(fx0, 1, fz0) && B.isAir(v.get(fx0, 1, fz0))) v.set(fx0, 1, fz0, B.stairs(p.trim(), out.opposite(), false));
            // Fenster und Läden hinter dem Schacht zumauern
            for (int k = 0; k < maxK; k++) {
                if (!inLev(k, wx, wz)) continue;
                for (int y = k * fh + 1; y <= k * fh + fh - 1; y++) {
                    String nm = nameAt(wx, y, wz);
                    if (nm.endsWith("_pane") || nm.endsWith("glass") || nm.endsWith("_stairs")) v.set(wx, y, wz, s.vary(wallB));
                }
            }
            for (int sg = -1; sg <= 1; sg += 2) {
                int sx = cx + sg * ax, sz = cz + sg * az;
                for (int y = 2; y < maxK * fh; y++) {
                    if (v.in(sx, y, sz) && nameAt(sx, y, sz).endsWith("_trapdoor")) v.set(sx, y, sz, B.AIR);
                }
            }
            // Kamin im Raum: Feuerstelle in der Wandnische, Mauerwerk darüber, Stufen als Wangen, Sims
            v.set(wx, 1, wz, B.campfire());
            for (int y = 2; y <= Math.min(fh - 1, 4); y++) v.set(wx, y, wz, s.vary(stone));
            int fx = wx - out.dx, fz = wz - out.dz;
            s.reserve(wx, 1, wz);
            if (inLev(0, fx, fz) && openMask(0, fx, fz) == 0) {
                for (int y = 1; y <= 3; y++) s.reserve(fx, y, fz);
                if (fh >= 5) v.set(fx, 3, fz, B.slab(p.trim(), true));
                for (int sg = -1; sg <= 1; sg += 2) {
                    int sx = fx + sg * ax, sz = fz + sg * az;
                    if (inLev(0, sx, sz) && openMask(0, sx, sz) == 0 && B.isAir(v.get(sx, 1, sz))) {
                        v.set(sx, 1, sz, B.stairs(p.trim(), out, false));
                        s.reserve(sx, 1, sz);
                    }
                }
            }
        } else {
            // Innenschacht: Ofen am Fuß, zum längsten freien Weg gewandt
            Dir face = Dir.SOUTH;
            int bestD = -1;
            for (Dir d : Dir.values()) {
                int dist = 0;
                for (int i = 1; i < 30 && inLev(0, cx + d.dx * i, cz + d.dz * i) && openMask(0, cx + d.dx * i, cz + d.dz * i) == 0; i++) dist = i;
                if (dist > bestD) { bestD = dist; face = d; }
            }
            v.set(cx, 1, cz, B.with("furnace", "facing", face.id(), "lit", "true"));
            for (Dir d : Dir.values()) s.reserve(cx + d.dx, 1, cz + d.dz);
        }
    }

    private boolean inLevAny(int x, int z) {
        for (int k = 0; k < maxK; k++) if (inLev(k, x, z)) return true;
        return false;
    }

    private boolean chimneyColumnFree(int cx, int cz, Dir out) {
        int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
        for (int dd = -1; dd <= 1; dd++) {
            if (inLevAny(cx + dd * ax, cz + dd * az) || inLevAny(cx + out.dx + dd * ax, cz + out.dz + dd * az)) return false;
        }
        return v.in(cx, 1, cz) && B.isAir(v.get(cx, 1, cz));
    }

    private boolean innerColumnFree(int x, int z) {
        for (int k = 0; k < maxK; k++) {
            if (!inLev(k, x, z)) break;
            if (k > 0 && openMask(k, x, z) != 0) return false;
        }
        for (int y = 1; y < maxK * fh; y++) {
            if (nameAt(x, y, z).equals("ladder") || s.isReserved(x, y, z)) return false;
        }
        return true;
    }

    private String nameAt(int x, int y, int z) {
        String c = v.get(x, y, z);
        return c == null ? "" : B.name(c);
    }

    private int columnTop(int x, int z) {
        for (int y = H - 1; y > 0; y--) if (!v.isEmpty(x, y, z)) return y;
        return 0;
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

    /** Vorbau: kleine Plattform mit Stufenkante, zwei Pfosten und Plattendach vor der Tür, wenn Platz ist. */
    private void porch() {
        Dir o = doorDir;
        int ax = o.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
        int cy = Math.max(3, fh - 1);
        // freie Fläche: 2 Reihen Plattform + Stufenkante, 3 Zellen breit (plus Pfostenzellen)
        for (int dist = 1; dist <= 3; dist++)
            for (int w = -2; w <= 2; w++) {
                int x = doorX + o.dx * dist + w * ax, z = doorZ + o.dz * dist + w * az;
                if (!v.in(x, cy, z)) return;
                if (inLev(0, x, z)) return;
                if (dist <= 2 && Math.abs(w) <= 1) {
                    String at = v.get(x, 1, z);
                    if (at != null && !B.isAir(at)) return;
                    for (int y = 2; y <= cy; y++) {
                        String up = v.get(x, y, z);
                        if (up != null && !B.isAir(up)) return;
                    }
                }
            }
        String slabRoof = B.slab(p.roof(), false);
        String fence = B.id(p.fence());
        for (int dist = 1; dist <= 2; dist++) {
            for (int w = -1; w <= 1; w++) {
                int x = doorX + o.dx * dist + w * ax, z = doorZ + o.dz * dist + w * az;
                v.set(x, 0, z, B.id(p.floor().block()));
                v.set(x, cy, z, slabRoof);
                for (int y = 1; y < cy; y++) s.reserve(x, y, z);
            }
        }
        // bündige Kante der Plattform (Boden und Weg liegen auf gleicher Höhe)
        for (int w = -1; w <= 1; w++) {
            int x = doorX + o.dx * 3 + w * ax, z = doorZ + o.dz * 3 + w * az;
            v.set(x, 0, z, B.id(p.floor().block()));
            s.reserve(x, 1, z);
        }
        // Pfosten an den vorderen Ecken des Dachs
        for (int w : new int[] {-1, 1}) {
            int x = doorX + o.dx * 2 + w * ax, z = doorZ + o.dz * 2 + w * az;
            for (int y = 1; y < cy; y++) v.set(x, y, z, fence);
        }
    }

    /** Fassadendetails (siehe {@link FacadeDetail}): Vorbau mit Pfosten, Stufe, Fensterrahmen, Ranken, Sockelstreifen. */
    private void facade() {
        FacadeDetail fd = new FacadeDetail(s, lev, maxK, fh, frameB, groundWallB, doorX, doorZ, doorDir);
        boolean deep = fd.porch();
        if (!deep) porch();
        fd.apron();
        fd.windows(p.shutters() || spec.has(BuildingSpec.Feature.SHUTTERS));
        fd.vines();
        fd.plinthRing();
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
            if (rich) facade();
            else porch();
            if (spec.has(BuildingSpec.Feature.LANTERNS) && doorDir == Dir.NORTH && doorZ >= 4) s.lanternPosts(doorX - 1, doorZ - 3, 3);
        }
        if (spec.has(BuildingSpec.Feature.FENCE) && mf >= 2 && doorX >= 0) s.fence(Math.max(0, doorX - 1), 3);
        if (spec.has(BuildingSpec.Feature.TREES) || spec.has(BuildingSpec.Feature.GARDEN)) {
            int[][] spots = {{2, mf + 2}, {W - 3, mf + 2}, {2, D - 3}, {W - 3, D - 3}, {W / 2, D - 2}};
            for (int[] t : spots) if (farFromBuilding(t[0], t[1], 4) && rng.chance(0.75)) s.tree(t[0], t[1], 3 + rng.nextInt(2));
        }
        if (spec.has(BuildingSpec.Feature.GARDEN)) s.flowersAndBushes(0, 0, W - 1, D - 1, 0.06, 0.02);
    }

    // ------------------------------------------------------------------ Stein-, Gotik- und Festungsdetails

    /** Steinoptik erkennen: Steinstile oder Wände aus Stein/Ziegel/Kalkstein usw. */
    private void detectStone() {
        Palette.Style st = spec.style();
        classic = st == Palette.Style.CLASSICAL;
        boolean plain = st == Palette.Style.MODERN || st == Palette.Style.DESERT || st == Palette.Style.JAPANESE;
        stone = st == Palette.Style.STONE || classic || st == Palette.Style.FANTASY || (!plain && stoneLike(wallB));
        fort = stone && !classic;
        // Steinwand in Fachwerk-Palette: Holzgerüst durch Stein-Eckquader ersetzen
        if (fort && p.timber() && stoneLike(wallB) && !stoneLike(frameB)) {
            frameB = keystone();
            noTimber = true;
        }
    }

    private static boolean stoneLike(String block) {
        String n = B.name(block);
        return n.contains("stone") || n.contains("cobble") || n.contains("deepslate") || n.contains("andesite")
            || n.contains("diorite") || n.contains("granite") || n.contains("calcite") || n.contains("quartz")
            || n.contains("sandstone") || n.contains("blackstone") || n.contains("tuff") || n.equals("stone_bricks");
    }

    /** Steinfamilie für Sockel, Fensterbögen und Pfeilerkappen (Holz-Zierleisten werden durch Stein ersetzt). */
    private Materials.Family stoneFam() {
        if (stoneLike(p.trim().block())) return p.trim();
        return Materials.family(wallB, Materials.family("stone_brick"));
    }

    private String keystone() {
        String n = B.name(wallB);
        if (n.contains("quartz")) return B.id("chiseled_quartz_block");
        if (n.contains("sandstone")) return B.id("chiseled_sandstone");
        if (n.contains("deepslate")) return B.id("chiseled_deepslate");
        if (n.contains("blackstone")) return B.id("chiseled_polished_blackstone");
        return B.id("chiseled_stone_bricks");
    }

    private static Dir dirOf(int dx, int dz) {
        for (Dir d : Dir.values()) if (d.dx == dx && d.dz == dz) return d;
        return Dir.NORTH;
    }

    /** Zelle gehört auf dieser Ebene nur zu runden Teilen (Turmwand). */
    private boolean roundCell(int k, int x, int z) {
        boolean r = false;
        for (Vol q : vols) {
            if (k < q.b || k >= q.top() || !q.contains(x, z)) continue;
            if (!q.round) return false;
            r = true;
        }
        return r;
    }

    private Dir singleOut(int k, int x, int z) {
        if (!inLev(k, x, z)) return null;
        int mask = openMask(k, x, z);
        if (Integer.bitCount(mask) != 1) return null;
        return Dir.values()[Integer.numberOfTrailingZeros(mask)];
    }

    private boolean nearDoor(int x, int z, int dist) {
        return doorX >= 0 && Math.abs(x - doorX) + Math.abs(z - doorZ) <= dist;
    }

    private boolean crenelled(Vol q) {
        if (fort) return true;
        if (!q.round || stone) return false;
        Palette.Style st = spec.style();
        return st == Palette.Style.MEDIEVAL || st == Palette.Style.RUSTIC || st == Palette.Style.NORDIC;
    }

    /** Zinnenkranz: Zinnen (Merlons) im Wechsel mit Lücken, an Ecken immer Zinne; Türme nach Winkel gezählt. */
    private void crenels(Vol q, int yt) {
        String cap = B.id(stoneFam().block());
        for (int x = q.x0; x <= q.x1; x++)
            for (int z = q.z0; z <= q.z1; z++) {
                if (!q.contains(x, z) || !inLev(q.top() - 1, x, z) || openMask(q.top() - 1, x, z) == 0) continue;
                if (inLev(q.top(), x, z)) continue;
                int mask = openMask(q.top() - 1, x, z);
                boolean merlon;
                if (q.round) {
                    double a = Math.atan2(z - q.cz, x - q.cx);
                    int n = Math.max(6, (int) Math.round(Math.PI * q.r));
                    int idx = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * n);
                    merlon = idx % 2 == 0;
                } else {
                    merlon = Integer.bitCount(mask) >= 2 || ((x + z) & 1) == 0;
                }
                v.set(x, yt, z, cap);
                if (!merlon) continue;
                place(x, yt + 1, z, s.vary(wallB));
                // Eckzinnen der Rechteckteile sind doppelt hoch
                if (!q.round && Integer.bitCount(mask) >= 2) place(x, yt + 2, z, s.vary(wallB));
            }
    }

    /** Spitzbogenfenster (Gotik) bzw. Rundbogenfenster mit Sims; Türme bekommen Schießscharten. */
    private void stoneWindows() {
        Materials.Family sf = stoneFam();
        String pane = B.id(p.window());
        for (int k = 0; k < maxK; k++) {
            int y0 = k * fh;
            int hi = fh >= 6 ? y0 + 4 : fh == 5 ? y0 + 3 : y0 + 2;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!lev[k][x][z]) continue;
                    int mask = openMask(k, x, z);
                    if (Integer.bitCount(mask) != 1) continue;
                    if (roundCell(k, x, z)) continue;
                    Dir out = Dir.values()[Integer.numberOfTrailingZeros(mask)];
                    int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
                    if (!straight(k, x - ax, z - az, mask) || !straight(k, x + ax, z + az, mask)) continue;
                    int t = ax == 1 ? x : z;
                    if (Math.floorMod(t, 4) != 2) continue;
                    if (k == 0 && nearDoor(x, z, 2)) continue;
                    for (int y = y0 + 2; y <= hi; y++) v.set(x, y, z, pane);
                    if (fh >= 5) {
                        // Bogenschultern: umgedrehte Treppen links und rechts der obersten Scheibe
                        for (int sgn : new int[] {-1, 1}) {
                            v.set(x + sgn * ax, hi, z + sgn * az, B.stairs(sf, dirOf(sgn * ax, sgn * az), true));
                        }
                        // Sohlbank
                        v.set(x, y0 + 1, z, B.stairs(sf, out, true));
                    }
                }
        }
        roundSlits(classic);
    }

    /** Schießscharten (bei klassischem Stil Fensterscheiben) an Rundtürmen: pro Geschoss versetzt auf 8 Achsen. */
    private void roundSlits(boolean glass) {
        String pane = B.id(p.window());
        for (Vol q : vols) {
            if (!q.round) continue;
            int slots = q.r >= 7 ? 8 : 4;
            for (int k = q.b; k < q.top(); k++) {
                int y0 = k * fh;
                int hi = fh >= 5 ? y0 + 3 : y0 + 2;
                for (int j = 0; j < slots; j++) {
                    double a = (j + (k % 2 == 0 ? 0.0 : 0.5)) * 2 * Math.PI / slots;
                    double tx = q.cx + q.r * Math.cos(a), tz = q.cz + q.r * Math.sin(a);
                    int bx = -1, bz = -1;
                    double best = 1e9;
                    for (int x = (int) Math.round(tx) - 1; x <= (int) Math.round(tx) + 1; x++)
                        for (int z = (int) Math.round(tz) - 1; z <= (int) Math.round(tz) + 1; z++) {
                            if (singleOut(k, x, z) == null || !roundCell(k, x, z)) continue;
                            double dd = (x - tx) * (x - tx) + (z - tz) * (z - tz);
                            if (dd < best) {
                                best = dd;
                                bx = x;
                                bz = z;
                            }
                        }
                    if (bx < 0 || (k == 0 && nearDoor(bx, bz, 2))) continue;
                    for (int y = y0 + 2; y <= hi; y++) v.set(bx, y, bz, glass ? pane : B.AIR);
                }
            }
        }
    }

    /** Rosenfenster: kreuzförmige Scheiben in der Giebelfüllung der Stirnwände, wenn der Giebel hoch genug ist. */
    private void roseWindows(Vol q, int yt) {
        int span = q.ridgeX ? q.z1 - q.z0 : q.x1 - q.x0;
        if (span / 2 < 4) return;
        String pane = B.id(p.window());
        String ring = B.id(stoneFam().block());
        for (int end = 0; end < 2; end++) {
            int fx = q.ridgeX ? (end == 0 ? q.x0 : q.x1) : (q.x0 + q.x1) / 2;
            int fz = q.ridgeX ? (q.z0 + q.z1) / 2 : (end == 0 ? q.z0 : q.z1);
            // nur freie Außenseite (kein Anbau davor)
            int ox = q.ridgeX ? fx + (end == 0 ? -1 : 1) : fx, oz = q.ridgeX ? fz : fz + (end == 0 ? -1 : 1);
            if (inLev(q.top() - 1, ox, oz)) continue;
            int half = span / 2;
            int yc = yt + Math.min(half - 2, 5);
            int[][] cross = half >= 6
                ? new int[][] {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-2, 0}, {2, 0}, {0, -2}, {0, 2}}
                : new int[][] {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] c : cross) {
                int x = q.ridgeX ? fx : fx + c[0], z = q.ridgeX ? fz + c[0] : fz, y = yc + c[1];
                String cur = v.get(x, y, z);
                if (cur == null || B.isAir(cur)) continue;
                v.set(x, y, z, pane);
            }
            if (q.ridgeX) {
                for (int dz : new int[] {-1, 1}) for (int dy : new int[] {-1, 1}) {
                    String cur = v.get(fx, yc + dy, fz + dz);
                    if (cur != null && !B.isAir(cur)) v.set(fx, yc + dy, fz + dz, ring);
                }
            } else {
                for (int dx : new int[] {-1, 1}) for (int dy : new int[] {-1, 1}) {
                    String cur = v.get(fx + dx, yc + dy, fz);
                    if (cur != null && !B.isAir(cur)) v.set(fx + dx, yc + dy, fz, ring);
                }
            }
        }
    }

    /** Spitzbogen-Portal: Oberlicht, Schlussstein, Bogenschultern, vorspringende Pfeiler (Nische) und Fackeln. */
    private void stoneDoor() {
        Materials.Family sf = stoneFam();
        Dir o = doorDir;
        int ax = o.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
        int mask = 1 << o.ordinal();
        String key = keystone();
        boolean tall = fh >= 5;
        // Fackeln des Standardbaus entfernen (sie wandern an die Pfeiler)
        for (int sgn : new int[] {-1, 1}) {
            int tx = doorX + sgn * ax + o.dx, tz = doorZ + sgn * az + o.dz;
            String cur = v.get(tx, 2, tz);
            if (cur != null && B.name(cur).equals("wall_torch")) v.set(tx, 2, tz, B.AIR);
        }
        if (tall) {
            v.set(doorX, 3, doorZ, B.id(p.window()));
            v.set(doorX, 4, doorZ, key);
        } else {
            v.set(doorX, 3, doorZ, key);
        }
        for (int sgn : new int[] {-1, 1}) {
            int wx = doorX + sgn * ax, wz = doorZ + sgn * az;
            if (!straight(0, wx, wz, mask)) continue;
            if (tall) v.set(wx, 3, wz, B.stairs(sf, dirOf(sgn * ax, sgn * az), true));
            // Pfeiler vor der Wand: die Tür liegt dadurch in einer Nische
            int jx = wx + o.dx, jz = wz + o.dz;
            int top = tall ? 4 : 3;
            if (!v.isEmpty(jx, 1, jz)) continue;
            for (int y = 1; y < top; y++) place(jx, y, jz, B.id(stoneFam().block()));
            place(jx, top, jz, B.stairs(sf, o.opposite(), false));
            place(jx + o.dx, 2, jz + o.dz, B.wallTorch(o));
        }
    }

    // ------------------------------------------------------------------ Außen: Sockel, Strebepfeiler, Fackeln, Banner

    private void stoneExterior() {
        if (fh < 4) return;
        if (fort) buttresses();
        plinth();
        accents();
    }

    /** Strebepfeiler an langen, geraden Außenwänden (rechteckige Teile). */
    private void buttresses() {
        Materials.Family sf = stoneFam();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                Dir out = singleOut(0, x, z);
                if (out == null || roundCell(0, x, z)) continue;
                int mask = 1 << out.ordinal();
                int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
                int t = ax == 1 ? x : z;
                int period = flatTop(x, z) ? 8 : 4;
                if (Math.floorMod(t, period) != 0) continue;
                // Lauflänge der geraden Wand: mindestens 9 Zellen
                int run = 1;
                for (int sgn : new int[] {-1, 1})
                    for (int i = 1; i < 12 && straight(0, x + sgn * i * ax, z + sgn * i * az, mask); i++) run++;
                if (run < 9 || nearDoor(x, z, 3)) continue;
                int levels = 1;
                while (levels < 2 && straight(levels, x, z, mask)) levels++;
                int top = levels * fh - 1;
                int ox = x + out.dx, oz = z + out.dz;
                if (!v.isEmpty(ox, 1, oz) || !v.isEmpty(ox + out.dx, 1, oz + out.dz)) continue;
                for (int y = 1; y < top; y++) place(ox, y, oz, s.vary(wallB));
                place(ox, top, oz, B.stairs(sf, out.opposite(), false));
                place(ox + out.dx, 1, oz + out.dz, B.stairs(sf, out.opposite(), false));
            }
    }

    /** Geböschter Sockel: Treppenreihe rings um das Erdgeschoss (tall side zur Wand). */
    private void plinth() {
        Materials.Family sf = stoneFam();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                if (!lev[0][x][z]) continue;
                int mask = openMask(0, x, z);
                if (mask == 0) continue;
                for (Dir d : Dir.values()) {
                    if (!has(mask, d)) continue;
                    int ox = x + d.dx, oz = z + d.dz;
                    if (doorX >= 0 && ox == doorX + doorDir.dx && oz == doorZ + doorDir.dz) continue;
                    if (!v.in(ox, 1, oz) || !v.isEmpty(ox, 1, oz) || inLev(0, ox, oz)) continue;
                    v.set(ox, 1, oz, B.stairs(sf, d.opposite(), false));
                }
            }
    }

    /** Wandfackeln im Erdgeschoss und Wandbanner an den Obergeschossen. */
    private void accents() {
        String banner = B.id(p.bed() + "_wall_banner");
        for (int k = 0; k < maxK; k++) {
            int y0 = k * fh;
            int yy = fh >= 5 ? y0 + 3 : y0 + 2;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    Dir out = singleOut(k, x, z);
                    if (out == null || roundCell(k, x, z)) continue;
                    int mask = 1 << out.ordinal();
                    int ax = out.wallAxis() == 'x' ? 1 : 0, az = 1 - ax;
                    if (!straight(k, x - ax, z - az, mask) || !straight(k, x + ax, z + az, mask)) continue;
                    int t = ax == 1 ? x : z;
                    String wall = v.get(x, yy, z);
                    if (wall == null || B.isAir(wall) || B.isThin(wall) || B.name(wall).endsWith("_stairs")) continue;
                    int ox = x + out.dx, oz = z + out.dz;
                    if (!v.isEmpty(ox, yy, oz)) continue;
                    if (Math.floorMod(t, 8) == 1 && k == 0 && !nearDoor(x, z, 3)) {
                        v.set(ox, yy, oz, B.wallTorch(out));
                    } else if (Math.floorMod(t, 8) == 4 && !classic) {
                        v.set(ox, yy, oz, B.with(banner, "facing", out.id()));
                    }
                }
        }
        // Rundtürme: je eine Fackel an den vier Himmelsrichtungen des Erdgeschosses
        for (Vol q : vols) {
            if (!q.round || q.b != 0) continue;
            int yy = fh >= 5 ? 3 : 2;
            for (Dir d : Dir.values()) {
                int tx = (int) Math.round(q.cx + d.dx * q.r), tz = (int) Math.round(q.cz + d.dz * q.r);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = tx + dx, z = tz + dz;
                        if (singleOut(0, x, z) != d || !roundCell(0, x, z) || nearDoor(x, z, 3)) continue;
                        int ox = x + d.dx, oz = z + d.dz;
                        String wall = v.get(x, yy, z);
                        if (wall == null || B.isAir(wall) || B.isThin(wall) || !v.isEmpty(ox, yy, oz)) continue;
                        v.set(ox, yy, oz, B.wallTorch(d));
                        dx = 2;
                        break;
                    }
            }
        }
    }
}
