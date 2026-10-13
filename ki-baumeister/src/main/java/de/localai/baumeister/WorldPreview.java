package de.localai.baumeister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

/**
 * Vorschau in der Welt: Rahmen um den Baubereich, Ecken-Markierungen und das geplante Gebäude als
 * halbtransparentes, farbiges Hologramm (Kartenfarbe jedes Blocks, nur die sichtbare Außenhülle).
 */
public final class WorldPreview {
    private static final int BOX_COLOR = 0xFF22D3EE;
    private static final int CORNER_COLOR = 0xFFFBBF24;
    private static final int MAX_CUBES = 5000;
    private static final double MAX_DISTANCE_SQ = 160.0 * 160.0;

    private record Cube(BlockPos pos, GizmoStyle style) {}

    private static PlanCompiler.Result hologramSource;
    private static List<Cube> hologram = List.of();

    private WorldPreview() {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ModConfig.get().preview) return;
        BlockPos a = Session.cornerA, b = Session.cornerB;
        // Pro-Tick-Gizmos werden nur ohne Pause abgeholt; während der Pause bleibt das letzte Bild sichtbar.
        if ((a == null && b == null) || mc.isPaused()) return;

        PlanCompiler.Result result = Session.result;
        boolean showHologram = result != null && result != Session.built;
        if (showHologram && result != hologramSource) {
            hologramSource = result;
            hologram = buildHologram(level, result);
        }

        try (var ignored = mc.collectPerTickGizmos()) {
            if (a != null) Gizmos.cuboid(a, 0.02f, GizmoStyle.strokeAndFill(CORNER_COLOR, 3.0f, 0x30FBBF24));
            if (b != null) Gizmos.cuboid(b, 0.02f, GizmoStyle.strokeAndFill(CORNER_COLOR, 3.0f, 0x30FBBF24));
            if (a != null && b != null) {
                AABB box = new AABB(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
                Gizmos.cuboid(box, GizmoStyle.stroke(BOX_COLOR, 2.5f));
            }
            BlockPos m = result == null ? null : result.region().min();
            if (showHologram && mc.player.distanceToSqr(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5) < MAX_DISTANCE_SQ) {
                for (Cube c : hologram) Gizmos.cuboid(c.pos(), 0.0f, c.style());
            }
        }
    }

    /** Nur Blöcke der Außenhülle (mit mindestens einer freien Nachbarseite), höchstens MAX_CUBES. */
    private static List<Cube> buildHologram(ClientLevel level, PlanCompiler.Result result) {
        Set<BlockPos> solid = new HashSet<>();
        for (PlanCompiler.Placement p : result.placements()) {
            if (!p.state().isAir()) solid.add(p.pos());
        }
        List<Cube> cubes = new ArrayList<>();
        for (PlanCompiler.Placement p : result.placements()) {
            if (p.state().isAir()) continue;
            BlockPos pos = p.pos();
            boolean visible = !solid.contains(pos.above()) || !solid.contains(pos.below()) || !solid.contains(pos.north())
                || !solid.contains(pos.south()) || !solid.contains(pos.east()) || !solid.contains(pos.west());
            if (!visible) continue;
            MapColor mc = p.state().getMapColor(level, pos);
            int rgb = mc == null || mc == MapColor.NONE ? 0xB0B0B0 : mc.col;
            int alpha = p.state().canOcclude() ? 0x8C : 0x55;
            cubes.add(new Cube(pos, GizmoStyle.fill(alpha << 24 | (rgb & 0xFFFFFF))));
        }
        if (cubes.size() > MAX_CUBES) {
            // Gleichmäßig ausdünnen statt abschneiden
            List<Cube> thinned = new ArrayList<>(MAX_CUBES);
            double step = cubes.size() / (double) MAX_CUBES;
            for (double i = 0; i < cubes.size() && thinned.size() < MAX_CUBES; i += step) thinned.add(cubes.get((int) i));
            return thinned;
        }
        return cubes;
    }
}
