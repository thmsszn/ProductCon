package com.crudecompute.util;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Restricted views on fluid tanks for capabilities. */
public final class FluidHandlers {
    private FluidHandlers() {
    }

    /** Only allows draining (machine outputs). */
    public static IFluidHandler outputOnly(FluidTank tank) {
        return combined(null, tank);
    }

    /** Only allows filling (machine inputs). */
    public static IFluidHandler inputOnly(FluidTank tank) {
        return combined(tank, null);
    }

    /** Filling goes into {@code input}, draining comes out of {@code output}. Either may be null. */
    public static IFluidHandler combined(FluidTank input, FluidTank output) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 2;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                FluidTank t = tank == 0 ? input : output;
                return t == null ? FluidStack.EMPTY : t.getFluid();
            }

            @Override
            public int getTankCapacity(int tank) {
                FluidTank t = tank == 0 ? input : output;
                return t == null ? 0 : t.getCapacity();
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return tank == 0 && input != null && input.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return input == null ? 0 : input.fill(resource, action);
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return output == null ? FluidStack.EMPTY : output.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return output == null ? FluidStack.EMPTY : output.drain(maxDrain, action);
            }
        };
    }
}
