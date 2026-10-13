package com.crudecompute.oil;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.ConnectingBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.TickingBlockEntity;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidPipeBlockEntity extends BlockEntity implements TickingBlockEntity {
    public static final int CAPACITY = 500;
    public static final int RATE = 100;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int rotation;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    public IFluidHandler getTank() {
        return tank;
    }

    @Override
    public void serverTick() {
        if (level == null || tank.isEmpty()) {
            return;
        }
        BlockState state = getBlockState();
        Direction[] dirs = Direction.values();
        rotation = (rotation + 1) % dirs.length;
        for (int i = 0; i < dirs.length && !tank.isEmpty(); i++) {
            Direction dir = dirs[(i + rotation) % dirs.length];
            if (!ConnectingBlock.isConnected(state, dir)) {
                continue;
            }
            BlockPos other = worldPosition.relative(dir);
            if (level.getBlockEntity(other) instanceof FluidPipeBlockEntity pipe) {
                equalize(pipe);
            } else {
                Transfer.pushFluid(level, worldPosition, dir, tank, RATE);
            }
        }
    }

    /** Moves half of the difference towards the emptier neighbour pipe. */
    private void equalize(FluidPipeBlockEntity other) {
        FluidStack mine = tank.getFluid();
        FluidStack theirs = other.tank.getFluid();
        if (!theirs.isEmpty() && !FluidStack.isSameFluidSameComponents(mine, theirs)) {
            return;
        }
        int diff = mine.getAmount() - theirs.getAmount();
        if (diff < 2) {
            return;
        }
        int amount = Math.min(RATE, diff / 2);
        FluidStack moved = tank.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        int accepted = other.tank.fill(moved, IFluidHandler.FluidAction.EXECUTE);
        tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    public void showStatus(Player player) {
        if (tank.isEmpty()) {
            Lang.status(player, Lang.msg("pipe.empty"));
        } else {
            Lang.status(player, Lang.msg("pipe", Lang.fluid(tank.getFluid()), tank.getFluidAmount()));
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
