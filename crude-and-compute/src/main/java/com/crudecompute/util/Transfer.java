package com.crudecompute.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** Small helpers for pushing fluids / energy into neighbours and for block entity tickers. */
public final class Transfer {
    private Transfer() {
    }

    /** Push fluid from {@code source} into the block next to {@code pos} on side {@code dir}. Returns the moved amount. */
    public static int pushFluid(Level level, BlockPos pos, Direction dir, IFluidHandler source, int max) {
        BlockPos target = pos.relative(dir);
        if (!level.isLoaded(target)) {
            return 0;
        }
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, target, dir.getOpposite());
        if (handler == null || handler == source) {
            return 0;
        }
        FluidStack moved = FluidUtil.tryFluidTransfer(handler, source, max, true);
        return moved.getAmount();
    }

    /** Push energy into the block next to {@code pos} on side {@code dir}. Returns the amount accepted. */
    public static int pushEnergy(Level level, BlockPos pos, Direction dir, ModEnergyStorage source, int max) {
        BlockPos target = pos.relative(dir);
        if (source.getEnergyStored() <= 0 || !level.isLoaded(target)) {
            return 0;
        }
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, dir.getOpposite());
        if (storage == null || storage == source || !storage.canReceive()) {
            return 0;
        }
        int accepted = storage.receiveEnergy(Math.min(max, source.getEnergyStored()), false);
        source.removeInternal(accepted);
        return accepted;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> ticker(
            BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
