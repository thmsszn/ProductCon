package com.crudecompute.solar;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.TickingBlockEntity;
import com.crudecompute.util.Transfer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** Collects all solar panels touching it (flood fill through panels), converts DC to AC at 96 % and feeds its front. */
public class InverterBlockEntity extends BlockEntity implements TickingBlockEntity {
    public static final double EFFICIENCY = 0.96;
    public static final int MAX_PANELS = 128;
    public static final int RATE = 4096;

    private final ModEnergyStorage buffer = new ModEnergyStorage(50_000, 0, 0, this::setChanged);
    private int panels;
    private double peakW;
    private float sun;
    private double dcW;
    private double acW;
    private double carry;

    public InverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INVERTER.get(), pos, state);
    }

    /** AC output: only the front face (where the cable plugs in) delivers energy. */
    @Nullable
    public IEnergyStorage getOutput(@Nullable Direction side) {
        if (side != null && side != getBlockState().getValue(HorizontalMachineBlock.FACING)) {
            return null;
        }
        return output;
    }

    private final IEnergyStorage output = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int out = Math.min(Math.min(RATE, toExtract), buffer.getEnergyStored());
            if (!simulate) {
                buffer.removeInternal(out);
            }
            return out;
        }

        @Override
        public int getEnergyStored() {
            return buffer.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return buffer.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public int getPanels() {
        return panels;
    }

    public float getSun() {
        return sun;
    }

    public double getDcW() {
        return dcW;
    }

    public double getAcW() {
        return acW;
    }

    public int getStored() {
        return buffer.getEnergyStored();
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (level.getGameTime() % 20 == Math.floorMod(worldPosition.hashCode(), 20)) {
            scanPanels();
        }
        sun = SunUtil.irradiance(level);
        dcW = peakW * sun;
        acW = dcW * EFFICIENCY;
        carry += acW / 10.0; // 10 W = 1 FE/t
        int whole = (int) carry;
        if (whole > 0) {
            carry -= whole;
            buffer.addInternal(whole);
        }
        Transfer.pushEnergy(level, worldPosition, getBlockState().getValue(HorizontalMachineBlock.FACING), buffer, RATE);
    }

    private void scanPanels() {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            queue.add(worldPosition.relative(d));
        }
        int count = 0;
        double peak = 0;
        // panels touching each other or linked through DC cables belong to this inverter
        while (!queue.isEmpty() && count < MAX_PANELS && seen.size() < MAX_PANELS * 4) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !level.isLoaded(p)) {
                continue;
            }
            net.minecraft.world.level.block.Block block = level.getBlockState(p).getBlock();
            if (block instanceof com.crudecompute.datacenter.PowerCableBlock) {
                for (Direction d : Direction.values()) {
                    queue.add(p.relative(d));
                }
                continue;
            }
            if (!(block instanceof SolarPanelBlock panel)) {
                continue;
            }
            count++;
            if (SunUtil.exposed(level, p)) {
                peak += panel.peakWatts();
            }
            for (Direction d : Direction.values()) {
                queue.add(p.relative(d));
            }
        }
        panels = count;
        peakW = peak;
    }

    public void showStatus(Player player) {
        Component line = Lang.msg("inverter", panels, Math.round(sun * 100), Lang.num(dcW / 1000.0), Lang.num(acW / 1000.0), buffer.getEnergyStored());
        if (panels == 0) {
            Lang.warn(player, line);
        } else {
            Lang.status(player, line);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", buffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.setEnergy(tag.getInt("Energy"));
    }
}
