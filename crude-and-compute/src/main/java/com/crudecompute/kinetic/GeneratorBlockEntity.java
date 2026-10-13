package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlockEntity extends KineticBlockEntity implements Optimizable {
    public static final float IMPACT = 128f;
    /** FE produced per tick per RPM. */
    public static final int FE_PER_RPM = 4;
    public static final int BUFFER = 40_000;
    public static final int MAX_OUTPUT = 4096;

    private final ModEnergyStorage energy = new ModEnergyStorage(BUFFER, 0, MAX_OUTPUT, this::setChanged);
    private boolean optimized;
    private int lastProduction;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state);
    }

    @Nullable
    public IEnergyStorage getEnergy(@Nullable Direction side) {
        Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
        if (side != null && side.getAxis() == facing.getAxis()) {
            return null;
        }
        return energy;
    }

    public int getProduction() {
        return isPowered() ? Math.round(speed * FE_PER_RPM * boost()) : 0;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        lastProduction = getProduction();
        energy.addInternal(lastProduction);
        Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() != facing.getAxis()) {
                Transfer.pushEnergy(level, worldPosition, dir, energy, MAX_OUTPUT);
            }
        }
    }

    @Override
    protected void onSpeedChanged(float previousSpeed) {
        BlockState state = getBlockState();
        boolean active = isPowered();
        if (level != null && state.getValue(HorizontalMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    public void showStatus(Player player) {
        Component line = Lang.msg("generator", Lang.num(speed), getProduction(), energy.getEnergyStored(), BUFFER);
        if (optimized) {
            line = line.copy().append(Lang.msg("optimized"));
        }
        if (overstressed) {
            Lang.warn(player, kineticStatus(getBlockState().getBlock().getName()));
        } else {
            Lang.status(player, line);
        }
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side.getAxis() == getBlockState().getValue(HorizontalMachineBlock.FACING).getAxis();
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
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Optimized", optimized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        optimized = tag.getBoolean("Optimized");
    }
}
