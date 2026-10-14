package com.crudecompute.oil;

import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModBlocks;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.util.FluidHandlers;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.Optimizable;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public class OilDerrickBlockEntity extends KineticBlockEntity implements MultiblockCapabilities, Optimizable {
    public static final float IMPACT = 128f;
    public static final int TANK_CAPACITY = 32_000;
    public static final int FULL_RESERVOIR = 12;
    public static final int MAX_PUSH = 400;

    private final FluidTank output = new FluidTank(TANK_CAPACITY, stack -> stack.is(ModFluids.CRUDE_OIL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler outputHandler = FluidHandlers.outputOnly(output);
    private int deposits = -1;
    private float accumulated;
    private boolean optimized;

    public OilDerrickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_DERRICK.get(), pos, state);
    }

    public IFluidHandler getOutput() {
        return outputHandler;
    }

    private Direction facing() {
        return getBlockState().getValue(HorizontalMachineBlock.FACING);
    }

    /** Foot of the drill pipe, in the middle of the footprint. */
    public BlockPos drillPos() {
        return MultiblockControllerBlock.toWorld(worldPosition, facing(), 0, 0, 1);
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (deposits < 0 || level.getGameTime() % 100 == Math.floorMod(worldPosition.hashCode(), 100)) {
            deposits = scanDeposits();
        }
        boolean working = isPowered() && deposits > 0 && output.getSpace() > 0;
        if (working) {
            accumulated += speed / 32f * 3f * Math.min(1f, deposits / (float) FULL_RESERVOIR) * boost();
            int whole = (int) accumulated;
            if (whole > 0) {
                accumulated -= whole;
                output.fill(new FluidStack(ModFluids.CRUDE_OIL.get(), whole), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        if (!output.isEmpty()) {
            for (int right = -1; right <= 1; right++) {
                for (int forward = 0; forward <= 2; forward++) {
                    BlockPos cell = MultiblockControllerBlock.toWorld(worldPosition, facing(), right, 0, forward);
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        Transfer.pushFluid(level, cell, dir, output, MAX_PUSH);
                    }
                }
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(HorizontalMachineBlock.ACTIVE) != working) {
            level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
        }
    }

    private int scanDeposits() {
        if (level == null) {
            return 0;
        }
        BlockPos well = drillPos();
        int count = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = well.getY() - 1; y >= level.getMinBuildHeight() && count < 96; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(well.getX() + dx, y, well.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    FluidState fs = s.getFluidState();
                    if (s.is(ModBlocks.OIL_SHALE.get()) || (fs.isSource() && fs.getType().isSame(ModFluids.CRUDE_OIL.get()))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void showStatus(Player player) {
        if (overstressed || speed <= 0) {
            Lang.warn(player, kineticStatus(getBlockState().getBlock().getName()));
            return;
        }
        if (deposits <= 0) {
            Lang.warn(player, Lang.msg("pumpjack.no_oil"));
            return;
        }
        Component line = Lang.msg("derrick", Lang.num(speed), deposits, output.getFluidAmount(), TANK_CAPACITY);
        if (optimized) {
            line = line.copy().append(Lang.msg("optimized"));
        }
        Lang.status(player, line);
    }

    @Nullable
    @Override
    public IFluidHandler getFluidHandler(int right, int up, int forward, @Nullable Direction side) {
        return up == 0 ? outputHandler : null;
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side == facing().getOpposite();
    }

    @Override
    public float getStressImpact() {
        return IMPACT;
    }

    @Override
    public boolean isOptimized() {
        return optimized;
    }

    @Override
    public void setOptimized() {
        optimized = true;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Output", output.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("Accumulated", accumulated);
        tag.putBoolean("Optimized", optimized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.readFromNBT(registries, tag.getCompound("Output"));
        accumulated = tag.getFloat("Accumulated");
        optimized = tag.getBoolean("Optimized");
    }
}
