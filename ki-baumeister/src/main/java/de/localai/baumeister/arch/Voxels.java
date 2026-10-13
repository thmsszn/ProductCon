package de.localai.baumeister.arch;

import java.util.Arrays;

/**
 * 3D-Raster aus Blockzuständen in lokalen Koordinaten: x = Breite, y = Höhe (0 = Bodenschicht),
 * z = Tiefe (0 = Vorderseite, Norden). {@code null} heißt „nicht verändern“.
 */
public final class Voxels {
    public final int w;
    public final int h;
    public final int d;
    private final String[] cells;

    public Voxels(int w, int h, int d) {
        this.w = w;
        this.h = h;
        this.d = d;
        this.cells = new String[w * h * d];
    }

    public boolean in(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < d;
    }

    private int index(int x, int y, int z) {
        return (y * d + z) * w + x;
    }

    public void set(int x, int y, int z, String state) {
        if (in(x, y, z)) cells[index(x, y, z)] = state;
    }

    /** Setzt nur, wenn die Zelle leer (null oder Luft) ist. */
    public void setIfEmpty(int x, int y, int z, String state) {
        if (in(x, y, z) && B.isAir(cells[index(x, y, z)])) cells[index(x, y, z)] = state;
    }

    public String get(int x, int y, int z) {
        return in(x, y, z) ? cells[index(x, y, z)] : null;
    }

    public boolean isEmpty(int x, int y, int z) {
        return in(x, y, z) && B.isAir(cells[index(x, y, z)]);
    }

    /** Fester Vollblock (keine Luft, keine dünnen Teile wie Scheiben, Zäune, Fackeln). */
    public boolean isSolid(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && !B.isAir(s) && !B.isThin(s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = Math.max(0, Math.min(y1, y2)); y <= Math.min(h - 1, Math.max(y1, y2)); y++)
            for (int z = Math.max(0, Math.min(z1, z2)); z <= Math.min(d - 1, Math.max(z1, z2)); z++)
                for (int x = Math.max(0, Math.min(x1, x2)); x <= Math.min(w - 1, Math.max(x1, x2)); x++)
                    cells[index(x, y, z)] = state;
    }

    public void fillAll(String state) {
        Arrays.fill(cells, state);
    }

    /** Anzahl der Zellen mit einem Block (ohne Luft). */
    public int countBlocks() {
        int n = 0;
        for (String s : cells) if (!B.isAir(s)) n++;
        return n;
    }

    /** Kopie, um 180° gedreht (für Vorschaubilder von hinten). */
    public Voxels rotated180() {
        Voxels r = new Voxels(w, h, d);
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    String s = get(x, y, z);
                    if (s != null) s = rotateState180(s);
                    r.set(w - 1 - x, y, d - 1 - z, s);
                }
        return r;
    }

    private static String rotateState180(String s) {
        return s.replace("facing=north", "facing=#s").replace("facing=south", "facing=north").replace("facing=#s", "facing=south")
            .replace("facing=east", "facing=#w").replace("facing=west", "facing=east").replace("facing=#w", "facing=west");
    }
}
