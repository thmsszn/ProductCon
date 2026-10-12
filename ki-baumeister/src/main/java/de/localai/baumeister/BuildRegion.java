package de.localai.baumeister;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * Baubereich in der Welt plus Ausrichtung. Der Bauplan wird in lokalen Koordinaten erzeugt
 * (Vorderseite bei z = 0) und so gedreht, dass die Vorderseite zum Spieler zeigt.
 */
public record BuildRegion(BlockPos min, BlockPos max, Rotation rotation) {

    public static BuildRegion of(BlockPos a, BlockPos b, Vec3 viewer) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        double dx = viewer.x - (min.getX() + max.getX() + 1) / 2.0;
        double dz = viewer.z - (min.getZ() + max.getZ() + 1) / 2.0;
        Rotation rot;
        if (Math.abs(dz) >= Math.abs(dx)) rot = dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        else rot = dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        return new BuildRegion(min, max, rot);
    }

    public int sizeX() { return max.getX() - min.getX() + 1; }
    public int sizeY() { return max.getY() - min.getY() + 1; }
    public int sizeZ() { return max.getZ() - min.getZ() + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    private boolean quarterTurn() {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /** Breite (lokales x) aus Sicht der Vorderseite. */
    public int localWidth() { return quarterTurn() ? sizeZ() : sizeX(); }
    public int localHeight() { return sizeY(); }
    /** Tiefe (lokales z) von der Vorderseite nach hinten. */
    public int localDepth() { return quarterTurn() ? sizeX() : sizeZ(); }

    /** Lokale Koordinate → Weltposition (passend zu {@code BlockState.rotate(rotation)}). */
    public BlockPos toWorld(int lx, int ly, int lz) {
        int y = min.getY() + ly;
        return switch (rotation) {
            case NONE -> new BlockPos(min.getX() + lx, y, min.getZ() + lz);
            case CLOCKWISE_180 -> new BlockPos(max.getX() - lx, y, max.getZ() - lz);
            case CLOCKWISE_90 -> new BlockPos(max.getX() - lz, y, min.getZ() + lx);
            case COUNTERCLOCKWISE_90 -> new BlockPos(min.getX() + lz, y, max.getZ() - lx);
        };
    }

    /** Himmelsrichtung, in die die Vorderseite zeigt. */
    public String frontName() {
        return switch (rotation) {
            case NONE -> "Norden";
            case CLOCKWISE_180 -> "Süden";
            case CLOCKWISE_90 -> "Osten";
            case COUNTERCLOCKWISE_90 -> "Westen";
        };
    }

    public boolean sameArea(BuildRegion other) {
        return other != null && min.equals(other.min) && max.equals(other.max);
    }
}
