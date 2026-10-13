package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.Optimizable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DieselEngineBlockEntity extends KineticBlockEntity implements Optimizable {
    public static final float SPEED = 64f;
    public static final float CAPACITY = 1024f;
    public static final int TANK_CAPACITY = 4000;
    /** One mB of diesel lasts this many ticks (a bucket runs 200 seconds). */
    public static final int TICKS_PER_MB = 4;

    private final FluidTank fuel = new FluidTank(TANK_CAPACITY, stack -> stack.is(ModFluids.DIESEL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int burnTicks;
    private boolean running;
    private boolean optimized;

    public DieselEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIESEL_ENGINE.get(), pos, state);
    }

    public IFluidHandler getFuelHandler() {
        return fuel;
    }

    @Override
    public void serverTick() {
        if (burnTicks <= 0 && fuel.getFluidAmount() > 0) {
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
            burnTicks = TICKS_PER_MB;
        }
        boolean nowRunning = burnTicks > 0;
        if (burnTicks > 0) {
            burnTicks--;
        }
        if (nowRunning != running) {
            running = nowRunning;
            setChanged();
            notifyNetwork();
            BlockState state = getBlockState();
            if (level != null && state.getValue(HorizontalMachineBlock.ACTIVE) != running) {
                level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, running), Block.UPDATE_ALL);
            }
        }
    }

    public void showStatus(Player player) {
        Component name = getBlockState().getBlock().getName();
        if (!running && fuel.isEmpty()) {
            Lang.warn(player, Lang.msg("engine.empty"));
            return;
        }
        Component line = Lang.msg("engine.running", fuel.getFluidAmount(), TANK_CAPACITY);
        if (optimized) {
            line = line.copy().append(Lang.msg("optimized"));
        }
        Lang.status(player, line.copy().append(" | ").append(kineticStatus(name)));
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side == getBlockState().getValue(HorizontalMachineBlock.FACING);
    }

    @Override
    public float getGeneratedSpeed() {
        return running ? SPEED : 0;
    }

    @Override
    public float getStressCapacity() {
        return CAPACITY * boost();
    }

    @Override
    public boolean isOptimized() {
        return optimized;
    }

    @Override
    public void setOptimized() {
        optimized = true;
        setChanged();
        notifyNetwork();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuel.writeToNBT(registries, new CompoundTag()));
        tag.putInt("BurnTicks", burnTicks);
        tag.putBoolean("Running", running);
        tag.putBoolean("Optimized", optimized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.readFromNBT(registries, tag.getCompound("Fuel"));
        burnTicks = tag.getInt("BurnTicks");
        running = tag.getBoolean("Running");
        optimized = tag.getBoolean("Optimized");
    }
}
