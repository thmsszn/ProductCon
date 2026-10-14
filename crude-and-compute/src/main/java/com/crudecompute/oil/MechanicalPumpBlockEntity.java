package com.crudecompute.oil;

import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class MechanicalPumpBlockEntity extends KineticBlockEntity {
    public static final float IMPACT = 16f;
    /** mB per tick per RPM. */
    public static final int MB_PER_RPM = 4;

    /** Pipes connect to the inlet/outlet through this handler; it never holds anything itself. */
    private static final IFluidHandler PORT = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public MechanicalPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_PUMP.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(MechanicalPumpBlock.FACING);
    }

    @Nullable
    public IFluidHandler getPort(@Nullable Direction side) {
        return side == null || side.getAxis() == facing().getAxis() ? PORT : null;
    }

    public int getRate() {
        return isPowered() ? Math.round(speed * MB_PER_RPM) : 0;
    }

    @Override
    public void serverTick() {
        int rate = getRate();
        if (level == null || rate <= 0) {
            return;
        }
        Direction out = facing();
        BlockPos inletPos = worldPosition.relative(out.getOpposite());
        BlockPos outletPos = worldPosition.relative(out);
        if (!level.isLoaded(inletPos) || !level.isLoaded(outletPos)) {
            return;
        }
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, inletPos, out);
        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, outletPos, out.getOpposite());
        if (source != null && target != null) {
            FluidUtil.tryFluidTransfer(target, source, rate, true);
        }
    }

    public void showStatus(Player player) {
        if (!isPowered()) {
            Lang.warn(player, kineticStatus(getBlockState().getBlock().getName()));
            return;
        }
        Lang.status(player, Lang.msg("pump", Lang.num(speed), getRate(), Lang.msg("pump.hint")));
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side.getAxis() != facing().getAxis();
    }

    @Override
    public float getStressImpact() {
        return IMPACT;
    }
}
