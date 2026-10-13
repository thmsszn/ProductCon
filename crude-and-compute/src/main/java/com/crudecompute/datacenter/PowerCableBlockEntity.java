package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.ConnectingBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.TickingBlockEntity;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class PowerCableBlockEntity extends BlockEntity implements TickingBlockEntity {
    public static final int CAPACITY = 4096;
    public static final int RATE = 4096;

    private final ModEnergyStorage energy = new ModEnergyStorage(CAPACITY, RATE, RATE, this::setChanged);
    private int rotation;

    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CABLE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return energy;
    }

    @Override
    public void serverTick() {
        if (level == null || energy.getEnergyStored() <= 0) {
            return;
        }
        BlockState state = getBlockState();
        Direction[] dirs = Direction.values();
        rotation = (rotation + 1) % dirs.length;
        for (int i = 0; i < dirs.length && energy.getEnergyStored() > 0; i++) {
            Direction dir = dirs[(i + rotation) % dirs.length];
            if (!ConnectingBlock.isConnected(state, dir)) {
                continue;
            }
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof PowerCableBlockEntity cable) {
                int diff = energy.getEnergyStored() - cable.energy.getEnergyStored();
                if (diff > 1) {
                    int moved = cable.energy.addInternal(diff / 2);
                    energy.removeInternal(moved);
                }
            } else {
                Transfer.pushEnergy(level, worldPosition, dir, energy, RATE);
            }
        }
    }

    public void showStatus(Player player) {
        Lang.status(player, Lang.msg("cable", energy.getEnergyStored(), CAPACITY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
    }
}
