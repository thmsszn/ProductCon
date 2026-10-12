package de.localai.baumeister;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Setzt Blöcke verteilt über mehrere Server-Ticks (keine Ruckler, sichtbarer Baufortschritt)
 * und merkt sich die vorherigen Blöcke für „Rückgängig“. Alle Zugriffe laufen auf dem Server-Thread;
 * der Client liest nur den unveränderlichen {@link Progress}-Schnappschuss.
 */
public final class BuildManager {
    private static final int MAX_UNDO = 5;

    /** Fortschritt für die Anzeige; wird atomar ersetzt. */
    public record Progress(boolean running, boolean undo, float fraction, int processed, int total, String message) {}

    private record Change(BlockPos pos, BlockState previous) {}

    private record UndoEntry(ResourceKey<Level> dimension, List<Change> changes) {}

    private static final class Job {
        final ResourceKey<Level> dimension;
        final List<PlanCompiler.Placement> placements;
        final boolean undo;
        final int perTick;
        final List<Change> changes = new ArrayList<>();
        final List<BlockPos> touched = new ArrayList<>();
        int index;
        boolean shaping;
        int placed;
        int protectedBlocks;
        int unloaded;

        Job(ResourceKey<Level> dimension, List<PlanCompiler.Placement> placements, boolean undo, int perTick) {
            this.dimension = dimension;
            this.placements = placements;
            this.undo = undo;
            this.perTick = perTick;
        }

        /** Setzen zählt 90 %, das Verbinden der Nachbarn die restlichen 10 %. */
        float fraction() {
            if (!shaping) return placements.isEmpty() ? 0.9f : 0.9f * index / placements.size();
            return touched.isEmpty() ? 1f : 0.9f + 0.1f * index / touched.size();
        }

        int processed() { return shaping ? placements.size() : index; }
    }

    private static final Deque<UndoEntry> UNDO = new ArrayDeque<>();
    private static Job job;
    private static volatile Progress progress = new Progress(false, false, 0f, 0, 0, "");
    private static volatile int undoAvailable;

    private BuildManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BuildManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
    }

    public static Progress progress() { return progress; }

    public static int undoAvailable() { return undoAvailable; }

    /** Server-Thread: Bau starten. */
    static void startBuild(ResourceKey<Level> dimension, List<PlanCompiler.Placement> placements, int perTick) {
        if (job != null) return;
        job = new Job(dimension, placements, false, perTick);
        progress = new Progress(true, false, 0f, 0, placements.size(), "Baue …");
    }

    /** Server-Thread: letzten Bau rückgängig machen. */
    static void startUndo(int perTick) {
        if (job != null || UNDO.isEmpty()) return;
        UndoEntry entry = UNDO.pop();
        undoAvailable = UNDO.size();
        List<PlanCompiler.Placement> restore = new ArrayList<>(entry.changes().size());
        for (int i = entry.changes().size() - 1; i >= 0; i--) {
            Change c = entry.changes().get(i);
            restore.add(new PlanCompiler.Placement(c.pos(), c.previous()));
        }
        job = new Job(entry.dimension(), restore, true, perTick);
        progress = new Progress(true, true, 0f, 0, restore.size(), "Mache rückgängig …");
    }

    /** Server-Thread: laufenden Bau anhalten (bereits gesetzte Blöcke bleiben rückgängig machbar). */
    static void stop() {
        if (job != null) finish(job, true);
    }

    private static void reset() {
        job = null;
        UNDO.clear();
        undoAvailable = 0;
        progress = new Progress(false, false, 0f, 0, 0, "");
    }

    private static void tick(MinecraftServer server) {
        Job j = job;
        if (j == null) return;
        ServerLevel level = server.getLevel(j.dimension);
        if (level == null) {
            job = null;
            progress = new Progress(false, j.undo, 0f, 0, 0, "Dimension nicht mehr verfügbar – abgebrochen.");
            return;
        }
        int budget = j.perTick;
        if (!j.shaping) {
            // Nur echte Änderungen zählen; unveränderte Positionen (z.B. Luft auf Luft) werden schnell übersprungen.
            int scanLimit = j.perTick * 16;
            while (budget > 0 && scanLimit-- > 0 && j.index < j.placements.size()) {
                PlanCompiler.Placement p = j.placements.get(j.index++);
                BlockPos pos = p.pos();
                if (!level.isLoaded(pos)) {
                    j.unloaded++;
                    continue;
                }
                BlockState old = level.getBlockState(pos);
                if (old == p.state()) continue;
                budget--;
                // Truhen, Öfen usw. des Spielers nie überschreiben.
                if (!j.undo && old.hasBlockEntity()) {
                    j.protectedBlocks++;
                    continue;
                }
                if (level.setBlock(pos, p.state(), Block.UPDATE_ALL)) {
                    if (!j.undo) j.changes.add(new Change(pos, old));
                    j.touched.add(pos);
                    j.placed++;
                }
            }
            if (j.index >= j.placements.size()) {
                j.shaping = true;
                j.index = 0;
            }
        } else {
            // Zweiter Durchgang: Zäune, Scheiben, Treppen usw. mit ihren Nachbarn verbinden.
            while (budget-- > 0 && j.index < j.touched.size()) {
                BlockPos pos = j.touched.get(j.index++);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
                if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (j.index >= j.touched.size()) {
                finish(j, false);
                return;
            }
        }
        progress = new Progress(true, j.undo, j.fraction(), j.processed(), j.placements.size(),
            j.undo ? "Mache rückgängig …" : "Baue …");
    }

    private static void finish(Job j, boolean stopped) {
        job = null;
        if (!j.undo && !j.changes.isEmpty()) {
            UNDO.push(new UndoEntry(j.dimension, List.copyOf(j.changes)));
            while (UNDO.size() > MAX_UNDO) UNDO.removeLast();
        }
        undoAvailable = UNDO.size();
        StringBuilder msg = new StringBuilder();
        if (j.undo) msg.append(stopped ? "Rückgängig angehalten: " : "Rückgängig gemacht: ").append(j.placed).append(" Blöcke wiederhergestellt");
        else msg.append(stopped ? "Bau angehalten: " : "Fertig! ").append(j.placed).append(" Blöcke gesetzt");
        if (j.protectedBlocks > 0) msg.append(", ").append(j.protectedBlocks).append(" geschützte Blöcke (Truhen o.ä.) übersprungen");
        if (j.unloaded > 0) msg.append(", ").append(j.unloaded).append(" in nicht geladenen Chunks übersprungen");
        progress = new Progress(false, j.undo, 1f, j.placements.size(), j.placements.size(), msg.append('.').toString());
    }
}
