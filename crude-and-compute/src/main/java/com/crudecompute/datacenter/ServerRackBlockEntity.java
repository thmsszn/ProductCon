package com.crudecompute.datacenter;

import com.crudecompute.hardware.Catalog;
import com.crudecompute.hardware.HardwareSim;
import com.crudecompute.hardware.HwError;
import com.crudecompute.hardware.HwError.Problem;
import com.crudecompute.hardware.Kind;
import com.crudecompute.hardware.Part;
import com.crudecompute.hardware.ServerConfig;
import com.crudecompute.hardware.ServerState;
import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * A 14U rack holding individual servers (1U / 2U / 4U). Every server is simulated separately: hardware determines
 * load, heat and compute; the rack supplies electricity (FE) and inlet air whose temperature depends on the room,
 * the rack's own waste heat and nearby cooling units.
 */
public class ServerRackBlockEntity extends SyncedBlockEntity implements TickingBlockEntity, MultiblockCapabilities {
    public static final int RACK_U = 14;
    /** Model space: height of the first U slot above the rack floor and height of one U. */
    public static final float U0 = 0.125f;
    public static final float U_H = 0.125f;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_THROTTLED = 2;
    public static final int STATUS_OVERHEATED = 3;
    public static final int STATUS_NO_POWER = 4;

    public static final class Slot {
        public int u;
        public ServerConfig cfg;
        public ServerState st = new ServerState();
        /** Whether the rack currently supplies this server (false while it is shed during a shortage). */
        public boolean powered = true;

        public Slot(int u, ServerConfig cfg) {
            this.u = u;
            this.cfg = cfg;
        }

        public boolean covers(int row) {
            return row >= u && row < u + cfg.sizeU;
        }
    }

    private final List<Slot> slots = new ArrayList<>();
    private final ModEnergyStorage energy = new ModEnergyStorage(200_000, 16_384, 0, this::setChanged);
    private boolean powered = true;
    private int status;
    private float inletC = 22f;
    private float coolingLoad;
    private float cracCooling;

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER_RACK.get(), pos, state);
    }

    // ------------------------------------------------------------------ slots
    public List<Slot> getSlots() {
        return slots;
    }

    @Nullable
    public Slot slotAt(int row) {
        for (Slot s : slots) {
            if (s.covers(row)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public Slot slotStartingAt(int u) {
        for (Slot s : slots) {
            if (s.u == u) {
                return s;
            }
        }
        return null;
    }

    public boolean fits(int u, int size, @Nullable Slot ignore) {
        if (u < 0 || u + size > RACK_U) {
            return false;
        }
        for (Slot s : slots) {
            if (s != ignore && u < s.u + s.cfg.sizeU && s.u < u + size) {
                return false;
            }
        }
        return true;
    }

    public int firstFit(int size) {
        for (int u = 0; u + size <= RACK_U; u++) {
            if (fits(u, size, null)) {
                return u;
            }
        }
        return -1;
    }

    public int usedU() {
        int n = 0;
        for (Slot s : slots) {
            n += s.cfg.sizeU;
        }
        return n;
    }

    /** Inserts at the preferred U (0-based from the bottom) or the first free spot. Returns the U used or -1. */
    public int insert(ServerConfig cfg, int preferredU) {
        int u = preferredU >= 0 && fits(preferredU, cfg.sizeU, null) ? preferredU : firstFit(cfg.sizeU);
        if (u < 0) {
            return -1;
        }
        Slot slot = new Slot(u, cfg);
        slot.st.inputW = (float) HardwareSim.nominalInputW(cfg);
        slots.add(slot);
        slots.sort(Comparator.comparingInt(s -> s.u));
        sync();
        return u;
    }

    @Nullable
    public Slot remove(int u) {
        Slot s = slotStartingAt(u);
        if (s != null) {
            slots.remove(s);
            sync();
        }
        return s;
    }

    public boolean move(int u, int delta) {
        Slot s = slotStartingAt(u);
        if (s == null || !fits(u + delta, s.cfg.sizeU, s)) {
            return false;
        }
        s.u += delta;
        slots.sort(Comparator.comparingInt(x -> x.u));
        sync();
        return true;
    }

    public void setPriority(int u, int priority) {
        Slot s = slotStartingAt(u);
        if (s != null) {
            s.cfg.priority = Math.max(0, Math.min(2, priority));
            sync();
        }
    }

    public void setMode(int u, int mode) {
        Slot s = slotStartingAt(u);
        if (s != null) {
            s.cfg.mode = mode;
            recomputeAll();
            sync();
        }
    }

    @Nullable
    public Problem installPart(int u, Part part) {
        Slot s = slotStartingAt(u);
        if (s == null) {
            return new Problem(HwError.NOT_FOUND, 0);
        }
        Problem p = s.cfg.install(part);
        if (p == null) {
            recomputeAll();
            sync();
        }
        return p;
    }

    @Nullable
    public Problem removePart(int u, Kind kind, int index) {
        Slot s = slotStartingAt(u);
        if (s == null) {
            return new Problem(HwError.NOT_FOUND, 0);
        }
        Problem p = s.cfg.remove(kind, index);
        if (p == null) {
            recomputeAll();
            sync();
        }
        return p;
    }

    // ------------------------------------------------------------------ aggregated values
    public int getGpus() {
        int n = 0;
        for (Slot s : slots) {
            n += s.cfg.gpus.size();
        }
        return n;
    }

    /** Current compute output in PFLOPS. */
    public float getCompute() {
        float c = 0;
        for (Slot s : slots) {
            if (s.st.status == ServerState.ONLINE || s.st.status == ServerState.THROTTLED) {
                c += s.st.compute;
            }
        }
        return c;
    }

    /** Current draw in FE per tick. */
    public int getPowerDraw() {
        return (int) Math.ceil(requestedW(false) / HardwareSim.WATTS_PER_FE_TICK);
    }

    public float getHeatW() {
        float h = 0;
        for (Slot s : slots) {
            h += s.st.loadW;
        }
        return h;
    }

    public float getTemperature() {
        float t = inletC;
        for (Slot s : slots) {
            t = Math.max(t, s.st.temp);
        }
        return t;
    }

    public float getInlet() {
        return inletC;
    }

    public float getCoolingLoad() {
        return coolingLoad;
    }

    public float getCracCooling() {
        return cracCooling;
    }

    public boolean isPowered() {
        return powered;
    }

    public int getStatus() {
        return status;
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    public int getEnergyCapacity() {
        return energy.getMaxEnergyStored();
    }

    private double requestedW(boolean nominal) {
        double w = 0;
        for (Slot s : slots) {
            w += nominal || !s.powered ? HardwareSim.nominalInputW(s.cfg) : s.st.inputW;
        }
        return w;
    }

    // ------------------------------------------------------------------ simulation
    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        allocatePower();
        if (level.getGameTime() % 20 == Math.floorMod(worldPosition.hashCode(), 20)) {
            thermalUpdate();
        }
    }

    /**
     * Shares the buffered energy between the servers. When there is not enough for everyone, expendable servers are
     * shed first (priority 2), critical ones (priority 0) stay up the longest.
     */
    private void allocatePower() {
        List<Slot> order = new ArrayList<>(slots);
        order.sort(Comparator.comparingInt((Slot s) -> s.cfg.priority).thenComparingInt(s -> s.u));
        int available = energy.getEnergyStored();
        int used = 0;
        boolean all = true;
        for (Slot s : order) {
            double nominal = HardwareSim.nominalInputW(s.cfg);
            int need = (int) Math.ceil((s.powered ? s.st.inputW : nominal) / HardwareSim.WATTS_PER_FE_TICK);
            if (need <= 0 || available >= need) {
                available -= Math.max(0, need);
                used += Math.max(0, need);
                s.powered = true;
            } else {
                s.powered = false;
                all = false;
            }
        }
        if (used > 0) {
            energy.removeInternal(used);
        }
        powered = all;
    }

    /** Number of servers currently without supply because the rack ran short of energy. */
    public int getShedCount() {
        int n = 0;
        for (Slot s : slots) {
            if (!s.powered) {
                n++;
            }
        }
        return n;
    }

    /** Energy lost in the power supplies (heat on top of the load), in watts. */
    public float getPsuLossW() {
        float w = 0;
        for (Slot s : slots) {
            w += Math.max(0, s.st.inputW - s.st.loadW);
        }
        return w;
    }

    private void recomputeAll() {
        // inputs changed: refresh requested draw immediately so the brown-out check stays consistent
        for (Slot s : slots) {
            s.st.inputW = (float) HardwareSim.nominalInputW(s.cfg);
        }
    }

    /** Once per second: inlet air temperature, thermal step of every server, rack status. */
    private void thermalUpdate() {
        if (level == null) {
            return;
        }
        float ambient = Math.max(8f, Math.min(40f, 12f + level.getBiome(worldPosition).value().getBaseTemperature() * 10f));
        cracCooling = CoolingUnitBlockEntity.coolingFor(level, worldPosition);
        float recirc = 0.0012f * getHeatW();
        inletC = Math.max(14f, Math.min(60f, ambient + recirc - Math.min(12f, cracCooling * 2f)));
        double removable = 0;
        for (Slot s : slots) {
            float localInlet = inletC + 0.12f * s.u;
            HardwareSim.step(s.cfg, s.st, localInlet, s.powered, 1.0);
            removable += (HardwareSim.passiveG(s.cfg.sizeU) + HardwareSim.coolerG(s.cfg, 1f)) * Math.max(1f, HardwareSim.THROTTLE_C - localInlet);
        }
        float heat = getHeatW();
        coolingLoad = removable > 0 ? (float) Math.min(2.0, heat / removable) : 0f;

        int worst = STATUS_IDLE;
        boolean anyRunning = false;
        for (Slot s : slots) {
            int st = s.st.status;
            if (st == ServerState.ONLINE || st == ServerState.THROTTLED) {
                anyRunning = true;
            }
            int rank = switch (st) {
                case ServerState.OVERHEATED -> 4;
                case ServerState.NO_POWER -> 3;
                case ServerState.THROTTLED -> 2;
                case ServerState.ONLINE -> 1;
                default -> 0;
            };
            int cur = switch (worst) {
                case STATUS_OVERHEATED -> 4;
                case STATUS_NO_POWER -> 3;
                case STATUS_THROTTLED -> 2;
                case STATUS_ONLINE -> 1;
                default -> 0;
            };
            if (rank > cur) {
                worst = switch (rank) {
                    case 4 -> STATUS_OVERHEATED;
                    case 3 -> STATUS_NO_POWER;
                    case 2 -> STATUS_THROTTLED;
                    default -> STATUS_ONLINE;
                };
            }
        }
        status = worst;
        BlockState state = getBlockState();
        if (state.getValue(HorizontalMachineBlock.ACTIVE) != anyRunning) {
            level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, anyRunning), Block.UPDATE_CLIENTS);
        }
        sync();
    }

    public void showStatus(Player player) {
        Component line = Component.translatable("hw.rack.summary", slots.size(), usedU(), RACK_U, Lang.num(getHeatW() / 1000.0),
                Lang.num(getTemperature()), Component.translatable("hw.status." + status));
        if (status == STATUS_ONLINE || status == STATUS_IDLE) {
            Lang.status(player, line);
        } else {
            Lang.warn(player, line);
        }
    }

    @Nullable
    @Override
    public IEnergyStorage getEnergyStorage(int right, int up, int forward, @Nullable Direction side) {
        return energy;
    }

    // ------------------------------------------------------------------ persistence
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Status", status);
        tag.putBoolean("Powered", powered);
        tag.putFloat("Inlet", inletC);
        tag.putFloat("CoolLoad", coolingLoad);
        tag.putFloat("Crac", cracCooling);
        ListTag list = new ListTag();
        for (Slot s : slots) {
            CompoundTag e = new CompoundTag();
            e.putInt("U", s.u);
            e.put("Cfg", s.cfg.save());
            e.put("State", s.st.save());
            list.add(e);
        }
        tag.put("Servers", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        status = tag.getInt("Status");
        powered = !tag.contains("Powered") || tag.getBoolean("Powered");
        inletC = tag.contains("Inlet") ? tag.getFloat("Inlet") : 22f;
        coolingLoad = tag.getFloat("CoolLoad");
        cracCooling = tag.getFloat("Crac");
        slots.clear();
        for (Tag t : tag.getList("Servers", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            Slot s = new Slot(e.getInt("U"), ServerConfig.load(e.getCompound("Cfg")));
            s.st = ServerState.load(e.getCompound("State"));
            slots.add(s);
        }
        slots.sort(Comparator.comparingInt(s -> s.u));
    }

    /** Used by the catalog index lookups in the GUI. */
    public static Part catalogPart(int index) {
        return index >= 0 && index < Catalog.ALL.size() ? Catalog.ALL.get(index) : null;
    }
}
