package com.crudecompute.datacenter;

import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** Battery bank: 16 MFE, bidirectional on every terminal. The cable network decides when it charges or discharges. */
public class BatteryBlockEntity extends SyncedBlockEntity implements TickingBlockEntity, MultiblockCapabilities {
    public static final int CAPACITY = 16_000_000;
    public static final int RATE = 32_768;

    private final ModEnergyStorage storage = new ModEnergyStorage(CAPACITY, 0, 0, this::setChanged);
    private final IEnergyStorage terminal = new Terminal();
    private int lastSyncedLevel = -1;
    private int lastComparator = -1;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state);
    }

    /** Every face of every cell is a terminal. */
    @Nullable
    public IEnergyStorage getEnergy(@Nullable Direction side) {
        return terminal;
    }

    @Nullable
    @Override
    public IEnergyStorage getEnergyStorage(int right, int up, int forward, @Nullable Direction side) {
        return terminal;
    }

    public int getStoredEnergy() {
        return storage.getEnergyStored();
    }

    public float getFill() {
        return storage.getEnergyStored() / (float) CAPACITY;
    }

    @Override
    public void serverTick() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        int bars = Math.round(getFill() * 100);
        if (bars != lastSyncedLevel) {
            lastSyncedLevel = bars;
            sync();
        }
        int comparator = Math.round(getFill() * 15f);
        if (comparator != lastComparator) {
            lastComparator = comparator;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public void showStatus(Player player) {
        Lang.status(player, Lang.msg("battery", storage.getEnergyStored(), CAPACITY, Math.round(getFill() * 100)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", storage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.setEnergy(tag.getInt("Energy"));
    }

    /** Charges and discharges through the same terminal, each limited to {@link #RATE} per call. */
    private final class Terminal implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.max(0, Math.min(Math.min(RATE, toReceive), storage.getSpace()));
            if (!simulate && accepted > 0) {
                storage.addInternal(accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int out = Math.max(0, Math.min(Math.min(RATE, toExtract), storage.getEnergyStored()));
            if (!simulate && out > 0) {
                storage.removeInternal(out);
            }
            return out;
        }

        @Override
        public int getEnergyStored() {
            return storage.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
