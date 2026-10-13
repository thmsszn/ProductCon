package com.crudecompute.multiblock;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by controller block entities: exposes capabilities per cell of the structure.
 * Cells are given in local coordinates (right, up, forward), see {@link MultiblockControllerBlock}.
 */
public interface MultiblockCapabilities {
    @Nullable
    default IFluidHandler getFluidHandler(int right, int up, int forward, @Nullable Direction side) {
        return null;
    }

    @Nullable
    default IEnergyStorage getEnergyStorage(int right, int up, int forward, @Nullable Direction side) {
        return null;
    }

    @Nullable
    default IItemHandler getItemHandler(int right, int up, int forward, @Nullable Direction side) {
        return null;
    }
}
