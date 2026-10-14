package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class BatteryBlockEntity extends SyncedBlockEntity implements TickingBlockEntity {
    public static final int CAPACITY = 2_000_000;
    public static final int RATE = 8192;

    private final ModEnergyStorage storage = new ModEnergyStorage(CAPACITY, 0, 0, this::setChanged);
    private final IEnergyStorage input = new View(true);
    private final IEnergyStorage output = new View(false);
    private int lastSyncedLevel = -1;
    private int lastComparator = -1;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(HorizontalMachineBlock.FACING);
    }

    /** Front = output only, every other side = input only. */
    @Nullable
    public IEnergyStorage getEnergy(@Nullable Direction side) {
        if (side == null) {
            return input;
        }
        return side == facing() ? output : input;
    }

    public int getStoredEnergy() {
        return storage.getEnergyStored();
    }

    public float getFill() {
        return storage.getEnergyStored() / (float) CAPACITY;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        Transfer.pushEnergy(level, worldPosition, facing(), storage, RATE);
        if (level.getGameTime() % 10 == 0) {
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

    /** One-directional face of the battery. */
    private final class View implements IEnergyStorage {
        private final boolean in;

        private View(boolean in) {
            this.in = in;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!in) {
                return 0;
            }
            int accepted = Math.min(Math.min(RATE, toReceive), storage.getSpace());
            if (!simulate) {
                storage.addInternal(accepted);
            }
            return Math.max(0, accepted);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (in) {
                return 0;
            }
            int extracted = Math.min(Math.min(RATE, toExtract), storage.getEnergyStored());
            if (!simulate) {
                storage.removeInternal(extracted);
            }
            return Math.max(0, extracted);
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
            return !in;
        }

        @Override
        public boolean canReceive() {
            return in;
        }
    }
}
