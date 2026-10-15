package com.crudecompute.power;

import com.crudecompute.CrudeCompute;
import com.crudecompute.datacenter.PowerCableBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Electrical grid made of cables. Every group of touching cables is one network: all producers (generators, inverters)
 * feed all consumers (racks, coolers) of that network, surplus charges the batteries (buffers), a shortage is covered
 * by them. There is no flow direction to get wrong: whatever is plugged into a cable is part of the network.
 * <p>
 * Realism: the network throughput is limited by the cable rating and every cable segment wastes a little energy
 * (the loss is shown on the cables).
 */
@EventBusSubscriber(modid = CrudeCompute.MODID)
public final class PowerGrid {
    /** Rating of one cable network in FE per tick (= 327 kW). */
    public static final int RATING = 32_768;
    /** Resistive loss per cable segment, capped. */
    public static final double LOSS_PER_CABLE = 0.0015;
    public static final double MAX_LOSS = 0.2;

    private static final Map<Level, LevelGrid> GRIDS = new java.util.WeakHashMap<>();

    private PowerGrid() {
    }

    // ------------------------------------------------------------------ registration
    private static LevelGrid grid(Level level) {
        return GRIDS.computeIfAbsent(level, l -> new LevelGrid());
    }

    public static void add(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            LevelGrid g = grid(level);
            g.cables.add(pos.immutable());
            g.dirty = true;
        }
    }

    public static void remove(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            LevelGrid g = grid(level);
            g.cables.remove(pos);
            g.dirty = true;
        }
    }

    public static void markDirty(Level level) {
        if (!level.isClientSide) {
            grid(level).dirty = true;
        }
    }

    /** The network the cable at {@code pos} belongs to (null if none yet). */
    @Nullable
    public static Network networkAt(Level level, BlockPos pos) {
        LevelGrid g = GRIDS.get(level);
        if (g == null) {
            return null;
        }
        for (Network n : g.networks) {
            if (n.cables.contains(pos)) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ simulation
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelGrid g = GRIDS.get(level);
        if (g == null || g.cables.isEmpty()) {
            return;
        }
        if (g.dirty || level.getGameTime() % 100 == 0) {
            g.rebuild(level);
        }
        for (Network n : g.networks) {
            n.tick(level);
        }
    }

    /** Test hook: simulate one tick of every network of the level right now. */
    public static void tickNow(ServerLevel level) {
        LevelGrid g = grid(level);
        g.rebuild(level);
        for (Network n : g.networks) {
            n.tick(level);
        }
    }

    private static final class LevelGrid {
        final Set<BlockPos> cables = new HashSet<>();
        List<Network> networks = new ArrayList<>();
        boolean dirty = true;

        void rebuild(ServerLevel level) {
            dirty = false;
            List<Network> result = new ArrayList<>();
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos start : cables) {
                if (!seen.add(start)) {
                    continue;
                }
                Network net = new Network();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(start);
                net.cables.add(start);
                while (!queue.isEmpty()) {
                    BlockPos p = queue.poll();
                    for (Direction d : Direction.values()) {
                        BlockPos n = p.relative(d);
                        if (cables.contains(n)) {
                            if (seen.add(n)) {
                                net.cables.add(n);
                                queue.add(n);
                            }
                        } else if (level.isLoaded(n)
                                && level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite()) != null) {
                            net.ports.add(new Port(n, d.getOpposite()));
                        }
                    }
                }
                result.add(net);
            }
            networks = result;
        }
    }

    private record Port(BlockPos pos, Direction side) {
    }

    /** One connected group of cables with the devices plugged into it, plus the numbers of the last tick. */
    public static final class Network {
        final Set<BlockPos> cables = new HashSet<>();
        final List<Port> ports = new ArrayList<>();
        private int produced;
        private int consumed;
        private int buffered;
        private int lost;
        private int demand;
        private int supply;
        private boolean overloaded;
        private int producerCount;
        private int consumerCount;
        private int bufferCount;

        public int cableCount() {
            return cables.size();
        }

        /** FE per tick drawn from producers and batteries. */
        public int produced() {
            return produced;
        }

        public int consumed() {
            return consumed;
        }

        /** Positive = batteries charge, negative = batteries discharge (FE per tick). */
        public int buffered() {
            return buffered;
        }

        public int lost() {
            return lost;
        }

        public int demand() {
            return demand;
        }

        public int supply() {
            return supply;
        }

        public boolean overloaded() {
            return overloaded;
        }

        public int producerCount() {
            return producerCount;
        }

        public int consumerCount() {
            return consumerCount;
        }

        public int bufferCount() {
            return bufferCount;
        }

        public double lossFraction() {
            return Math.min(MAX_LOSS, LOSS_PER_CABLE * cables.size());
        }

        private static final class Endpoint {
            final IEnergyStorage storage;
            int amount;

            Endpoint(IEnergyStorage storage) {
                this.storage = storage;
            }
        }

        void tick(ServerLevel level) {
            List<Endpoint> producers = new ArrayList<>();
            List<Endpoint> consumers = new ArrayList<>();
            List<Endpoint> buffers = new ArrayList<>();
            Map<IEnergyStorage, Boolean> seen = new IdentityHashMap<>();
            for (Port port : ports) {
                if (!level.isLoaded(port.pos)) {
                    continue;
                }
                IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, port.pos, port.side);
                if (s == null || seen.put(s, Boolean.TRUE) != null) {
                    continue;
                }
                boolean out = s.canExtract();
                boolean in = s.canReceive();
                if (out && in) {
                    buffers.add(new Endpoint(s));
                } else if (out) {
                    producers.add(new Endpoint(s));
                } else if (in) {
                    consumers.add(new Endpoint(s));
                }
            }
            producerCount = producers.size();
            consumerCount = consumers.size();
            bufferCount = buffers.size();

            long production = 0;
            for (Endpoint e : producers) {
                e.amount = Math.max(0, e.storage.extractEnergy(RATING, true));
                production += e.amount;
            }
            long need = 0;
            for (Endpoint e : consumers) {
                e.amount = Math.max(0, e.storage.receiveEnergy(RATING, true));
                need += e.amount;
            }
            long canCharge = 0;
            long canDischarge = 0;
            List<Endpoint> chargers = new ArrayList<>();
            List<Endpoint> dischargers = new ArrayList<>();
            for (Endpoint e : buffers) {
                int in = Math.max(0, e.storage.receiveEnergy(RATING, true));
                int out = Math.max(0, e.storage.extractEnergy(RATING, true));
                Endpoint c = new Endpoint(e.storage);
                c.amount = in;
                chargers.add(c);
                Endpoint d = new Endpoint(e.storage);
                d.amount = out;
                dischargers.add(d);
                canCharge += in;
                canDischarge += out;
            }
            double eff = 1.0 - lossFraction();
            long effectiveProduction = (long) Math.floor(production * eff);
            long flow = Math.min(Math.min(effectiveProduction, need), RATING);
            long surplus = Math.max(0, effectiveProduction - flow);
            long charge = Math.min(Math.min(surplus, canCharge), Math.max(0, RATING - flow));
            long deficit = Math.max(0, need - flow);
            long discharge = Math.min(Math.min((long) Math.floor(canDischarge * eff), deficit), Math.max(0, RATING - flow));
            supply = (int) Math.min(Integer.MAX_VALUE, effectiveProduction + (long) Math.floor(canDischarge * eff));
            demand = (int) Math.min(Integer.MAX_VALUE, need);
            overloaded = (effectiveProduction + canDischarge * eff > RATING) && need > RATING;

            long fromProducers = Math.min(production, (long) Math.ceil((flow + charge) / eff));
            long fromBuffers = Math.min(canDischarge, (long) Math.ceil(discharge / eff));
            long extracted = extract(producers, fromProducers) + extract(dischargers, fromBuffers);
            long pool = (long) Math.floor(extracted * eff);
            long delivered = give(consumers, Math.min(pool, need));
            pool -= delivered;
            long charged = 0;
            if (fromBuffers == 0 && pool > 0) {
                charged = give(chargers, pool);
                pool -= charged;
            }
            produced = (int) Math.min(Integer.MAX_VALUE, extracted);
            consumed = (int) delivered;
            buffered = (int) (charged - (fromBuffers > 0 ? Math.min(extracted, fromBuffers) : 0));
            lost = (int) Math.max(0, extracted - delivered - charged);
        }

        /** Takes {@code amount} from the endpoints in proportion to what each offers. Returns what was actually taken. */
        private static long extract(List<Endpoint> list, long amount) {
            long total = 0;
            for (Endpoint e : list) {
                total += e.amount;
            }
            if (amount <= 0 || total <= 0) {
                return 0;
            }
            long taken = 0;
            for (Endpoint e : list) {
                int share = (int) Math.min(e.amount, Math.ceil(amount * (double) e.amount / total));
                if (share > 0) {
                    taken += e.storage.extractEnergy((int) Math.min(share, amount - taken), false);
                }
                if (taken >= amount) {
                    break;
                }
            }
            return taken;
        }

        /** Hands out {@code amount} in proportion to each endpoint's need. Returns what was accepted. */
        private static long give(List<Endpoint> list, long amount) {
            long total = 0;
            for (Endpoint e : list) {
                total += e.amount;
            }
            if (amount <= 0 || total <= 0) {
                return 0;
            }
            long accepted = 0;
            for (Endpoint e : list) {
                int share = (int) Math.min(e.amount, Math.ceil(amount * (double) e.amount / total));
                if (share > 0) {
                    accepted += e.storage.receiveEnergy((int) Math.min(share, amount - accepted), false);
                }
                if (accepted >= amount) {
                    break;
                }
            }
            return accepted;
        }
    }
}
