package com.crudecompute.kinetic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Solves rotation networks lazily: blocks mark themselves dirty when they are placed, removed or change their
 * output, and at the end of the level tick every dirty network is flood-filled once and gets its new speed.
 */
public final class KineticNetworkManager {
    /** Upper bound for a single network, protects against runaway flood fills. */
    public static final int MAX_NETWORK_SIZE = 4096;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> DIRTY = new HashMap<>();

    private KineticNetworkManager() {
    }

    public static void markDirty(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        DIRTY.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        Set<BlockPos> dirty = DIRTY.remove(level.dimension());
        if (dirty == null || dirty.isEmpty()) {
            return;
        }
        Set<BlockPos> solved = new HashSet<>();
        for (BlockPos start : dirty) {
            if (!solved.contains(start)) {
                solve(level, start, solved);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DIRTY.clear();
    }

    /** Flood-fills the network containing {@code start} and applies speed + stress to every member. */
    public static void solve(Level level, BlockPos start, Set<BlockPos> solved) {
        if (!level.isLoaded(start) || !(level.getBlockEntity(start) instanceof KineticBlockEntity first)) {
            return;
        }
        List<KineticBlockEntity> members = new ArrayList<>();
        ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        solved.add(start);
        queue.add(first);
        while (!queue.isEmpty() && members.size() < MAX_NETWORK_SIZE) {
            KineticBlockEntity current = queue.poll();
            members.add(current);
            for (Direction dir : Direction.values()) {
                if (!current.hasShaftTowards(dir)) {
                    continue;
                }
                BlockPos next = current.getBlockPos().relative(dir);
                if (solved.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof KineticBlockEntity neighbour
                        && !neighbour.isRemoved()
                        && neighbour.hasShaftTowards(dir.getOpposite())) {
                    solved.add(next);
                    queue.add(neighbour);
                }
            }
        }

        float speed = 0;
        float capacity = 0;
        float stress = 0;
        for (KineticBlockEntity member : members) {
            float generated = member.getGeneratedSpeed();
            if (generated > 0) {
                speed = Math.max(speed, generated);
                capacity += member.getStressCapacity();
            }
            stress += member.getStressImpact();
        }
        boolean overstressed = speed > 0 && stress > capacity;
        float finalSpeed = overstressed ? 0 : speed;
        for (KineticBlockEntity member : members) {
            member.applyNetwork(finalSpeed, overstressed, capacity, stress);
        }
    }
}
