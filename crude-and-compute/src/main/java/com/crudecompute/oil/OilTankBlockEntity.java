package com.crudecompute.oil;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class OilTankBlockEntity extends SyncedBlockEntity implements TickingBlockEntity {
    public static final int CAPACITY = 16_000;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                int comparator = getComparatorLevel();
                if (comparator != lastComparator) {
                    lastComparator = comparator;
                    level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                }
            }
        }
    };
    private int lastComparator;
    private int lastSyncedAmount = -1;

    public OilTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_TANK.get(), pos, state);
    }

    public IFluidHandler getTank() {
        return tank;
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    public int getComparatorLevel() {
        if (tank.isEmpty()) {
            return 0;
        }
        return 1 + Math.round(14f * tank.getFluidAmount() / CAPACITY);
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (!tank.isEmpty()) {
            Transfer.pushFluid(level, worldPosition, Direction.DOWN, tank, 200);
        }
        // keep the sight glass on the client roughly up to date
        int amount = tank.getFluidAmount();
        if (level.getGameTime() % 10 == 0
                && (Math.abs(amount - lastSyncedAmount) >= CAPACITY / 100 || (amount == 0) != (lastSyncedAmount == 0))) {
            lastSyncedAmount = amount;
            sync();
        }
    }

    public void showStatus(Player player) {
        if (tank.isEmpty()) {
            Lang.status(player, Lang.msg("tank.empty", CAPACITY));
        } else {
            Lang.status(player, Lang.msg("tank", Lang.fluid(tank.getFluid()), tank.getFluidAmount(), CAPACITY));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
