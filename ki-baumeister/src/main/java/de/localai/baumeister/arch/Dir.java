package de.localai.baumeister.arch;

/**
 * Himmelsrichtungen im lokalen Bauplan-Koordinatensystem: Norden = -z = Vorderseite des Gebäudes.
 * Die Namen entsprechen den Minecraft-Blockzustandswerten (facing=north …).
 */
public enum Dir {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx;
    public final int dz;

    Dir(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Dir opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    public Dir clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Dir counterClockwise() {
        return values()[(ordinal() + 3) % 4];
    }

    /** Achse, entlang der eine Wand mit dieser Außenrichtung verläuft ("x" oder "z"). */
    public char wallAxis() {
        return dx == 0 ? 'x' : 'z';
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
