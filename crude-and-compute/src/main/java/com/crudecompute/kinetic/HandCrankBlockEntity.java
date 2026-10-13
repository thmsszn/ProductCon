package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class HandCrankBlockEntity extends KineticBlockEntity {
    public static final float SPEED = 32f;
    public static final float CAPACITY = 64f;
    private static final int TICKS_PER_CRANK = 40;
    private static final int MAX_TICKS = 160;

    private int crankTicks;

    public HandCrankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAND_CRANK.get(), pos, state);
    }

    public void crank() {
        boolean wasRunning = crankTicks > 0;
        crankTicks = Math.min(MAX_TICKS, crankTicks + TICKS_PER_CRANK);
        setChanged();
        if (!wasRunning) {
            notifyNetwork();
        }
    }

    @Override
    public void serverTick() {
        if (crankTicks > 0) {
            crankTicks--;
            if (crankTicks == 0) {
                setChanged();
                notifyNetwork();
            }
        }
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side == getBlockState().getValue(HandCrankBlock.FACING).getOpposite();
    }

    @Override
    public float getGeneratedSpeed() {
        return crankTicks > 0 ? SPEED : 0;
    }

    @Override
    public float getStressCapacity() {
        return CAPACITY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CrankTicks", crankTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        crankTicks = tag.getInt("CrankTicks");
    }
}
