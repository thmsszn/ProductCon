package com.crudecompute.util;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Rotating collision shapes that were authored facing north (front = -Z). */
public final class ShapeUtil {
    private ShapeUtil() {
    }

    public static VoxelShape rotate(VoxelShape north, Direction facing) {
        if (facing == Direction.NORTH) {
            return north;
        }
        VoxelShape[] result = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double nx0, nz0, nx1, nz1;
            switch (facing) {
                case SOUTH -> {
                    nx0 = 1 - x1;
                    nx1 = 1 - x0;
                    nz0 = 1 - z1;
                    nz1 = 1 - z0;
                }
                case EAST -> {
                    nx0 = 1 - z1;
                    nx1 = 1 - z0;
                    nz0 = x0;
                    nz1 = x1;
                }
                case WEST -> {
                    nx0 = z0;
                    nx1 = z1;
                    nz0 = 1 - x1;
                    nz1 = 1 - x0;
                }
                default -> {
                    nx0 = x0;
                    nx1 = x1;
                    nz0 = z0;
                    nz1 = z1;
                }
            }
            result[0] = Shapes.or(result[0], Shapes.box(nx0, y0, nz0, nx1, y1, nz1));
        });
        return result[0].optimize();
    }

    public static Map<Direction, VoxelShape> horizontalMap(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            map.put(dir, rotate(north, dir));
        }
        return map;
    }

    /**
     * Box sticking out of the face opposite to {@code facing}: {@code from}..{@code to} pixels along the facing
     * axis (0 = back face) and {@code min}..{@code max} pixels on the other two axes.
     */
    public static VoxelShape directional(Direction facing, double min, double from, double max, double to) {
        double a = from / 16.0, b = to / 16.0, lo = min / 16.0, hi = max / 16.0;
        return switch (facing) {
            case UP -> Shapes.box(lo, a, lo, hi, b, hi);
            case DOWN -> Shapes.box(lo, 1 - b, lo, hi, 1 - a, hi);
            case SOUTH -> Shapes.box(lo, lo, a, hi, hi, b);
            case NORTH -> Shapes.box(lo, lo, 1 - b, hi, hi, 1 - a);
            case EAST -> Shapes.box(a, lo, lo, b, hi, hi);
            case WEST -> Shapes.box(1 - b, lo, lo, 1 - a, hi, hi);
        };
    }
}
