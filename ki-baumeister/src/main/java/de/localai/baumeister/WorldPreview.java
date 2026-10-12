package de.localai.baumeister;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

/** Zeigt den Baubereich als Rahmen und den Bauplan als schimmernde Partikel-Wolke in der Welt. */
public final class WorldPreview {
    private static final int BOX_COLOR = 0xFF22D3EE;
    private static final int CORNER_COLOR = 0xFFFBBF24;
    private static final DustParticleOptions GHOST = new DustParticleOptions(0x4ADE80, 0.8f);
    private static final int GHOST_PER_TICK = 90;

    private static PlanCompiler.Result ghostSource;
    private static List<BlockPos> ghost = List.of();
    private static int cursor;

    private WorldPreview() {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ModConfig.get().preview) return;
        BlockPos a = Session.cornerA, b = Session.cornerB;
        if (a == null && b == null) return;

        try (var ignored = mc.collectPerTickGizmos()) {
            if (a != null) Gizmos.cuboid(a, 0.02f, GizmoStyle.strokeAndFill(CORNER_COLOR, 3.0f, 0x30FBBF24));
            if (b != null) Gizmos.cuboid(b, 0.02f, GizmoStyle.strokeAndFill(CORNER_COLOR, 3.0f, 0x30FBBF24));
            if (a != null && b != null) {
                AABB box = new AABB(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
                Gizmos.cuboid(box, GizmoStyle.stroke(BOX_COLOR, 2.5f));
            }
        }

        PlanCompiler.Result result = Session.result;
        if (result == null || result == Session.built || mc.isPaused()) return;
        if (result != ghostSource) {
            ghostSource = result;
            ghost = result.placements().stream().filter(p -> !p.state().isAir()).map(PlanCompiler.Placement::pos).toList();
            cursor = 0;
        }
        int n = ghost.size();
        for (int i = 0; i < Math.min(n, GHOST_PER_TICK); i++) {
            cursor = (cursor + 1) % n;
            BlockPos pos = ghost.get(cursor);
            level.addAlwaysVisibleParticle(GHOST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
        }
    }
}
