package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class CoolingUnitBlockEntity extends SyncedBlockEntity implements TickingBlockEntity {
    public static final int FE_PER_TICK = 16;
    /** Degrees per second removed, shared between all racks in range. */
    public static final float COOLING_POWER = 6f;
    public static final int RANGE = 2;

    private final ModEnergyStorage energy = new ModEnergyStorage(8000, 1024, 0, this::setChanged);
    private boolean running;
    private int racksInRange;

    // client animation
    public float fanAngle;
    public float prevFanAngle;
    private float fanSpeed;

    public CoolingUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOLING_UNIT.get(), pos, state);
    }

    public boolean isRunning() {
        return running;
    }

    public IEnergyStorage getEnergy() {
        return energy;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        boolean nowRunning = energy.getEnergyStored() >= FE_PER_TICK;
        if (nowRunning) {
            energy.removeInternal(FE_PER_TICK);
        }
        if (nowRunning != running) {
            running = nowRunning;
            BlockState state = getBlockState();
            if (state.getValue(CoolingUnitBlock.ACTIVE) != running) {
                level.setBlock(worldPosition, state.setValue(CoolingUnitBlock.ACTIVE, running), Block.UPDATE_CLIENTS);
            }
        }
        if (level.getGameTime() % 20 == Math.floorMod(worldPosition.hashCode(), 20)) {
            racksInRange = countRacks();
        }
    }

    @Override
    public void clientTick() {
        boolean active = getBlockState().getValue(CoolingUnitBlock.ACTIVE);
        fanSpeed += ((active ? 24f : 0f) - fanSpeed) * 0.05f;
        prevFanAngle = fanAngle;
        fanAngle += fanSpeed;
        if (fanAngle > 3600f) {
            fanAngle -= 3600f;
            prevFanAngle -= 3600f;
        }
    }

    private int countRacks() {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE - 1, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (level.getBlockEntity(p) instanceof ServerRackBlockEntity) {
                count++;
            }
        }
        return count;
    }

    /** Cooling this unit provides to a single rack. */
    public float coolingShare() {
        return running ? COOLING_POWER / Math.max(1, racksInRange) : 0f;
    }

    /** Sum of the cooling of all units in range of the rack at {@code rackPos}. */
    public static float coolingFor(Level level, BlockPos rackPos) {
        float total = 0;
        for (BlockPos p : BlockPos.betweenClosed(rackPos.offset(-RANGE, -RANGE, -RANGE), rackPos.offset(RANGE, RANGE + 1, RANGE))) {
            if (level.getBlockEntity(p) instanceof CoolingUnitBlockEntity cooler) {
                total += cooler.coolingShare();
            }
        }
        return total;
    }

    public void showStatus(Player player) {
        Lang.status(player, Lang.msg("cooler", Lang.msg(running ? "cooler.on" : "cooler.off"), racksInRange));
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
