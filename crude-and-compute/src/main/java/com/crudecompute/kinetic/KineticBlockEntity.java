package com.crudecompute.kinetic;

import com.crudecompute.util.Lang;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that is part of a rotation network (shafts, gearboxes, engines, machines).
 * <p>
 * Every connected group of kinetic blocks forms a network that is solved by {@link KineticNetworkManager}:
 * the network spins at the speed of its fastest source, unless the summed stress impact of all consumers
 * exceeds the summed stress capacity of all sources - then it is overstressed and stops (like in Create).
 */
public abstract class KineticBlockEntity extends SyncedBlockEntity implements TickingBlockEntity {
    /** Degrees per tick at 1 RPM: 360° / (60 s * 20 ticks). */
    public static final float DEG_PER_TICK_PER_RPM = 0.3f;

    protected float speed;
    protected boolean overstressed;
    protected float networkCapacity;
    protected float networkStress;
    private boolean unloading;

    // client-side animation state
    public float angle;
    public float prevAngle;

    protected KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Whether a shaft/axle leaves this block through the given face. */
    public abstract boolean hasShaftTowards(Direction side);

    /** RPM this block drives the network with (0 for non-sources). */
    public float getGeneratedSpeed() {
        return 0;
    }

    /** Stress units this source provides while running. */
    public float getStressCapacity() {
        return 0;
    }

    /** Stress units this consumer needs. */
    public float getStressImpact() {
        return 0;
    }

    /** Visual speed multiplier for the renderer (e.g. gear reduction). */
    public float getVisualRatio() {
        return 1f;
    }

    public float getSpeed() {
        return speed;
    }

    public boolean isOverstressed() {
        return overstressed;
    }

    public boolean isPowered() {
        return speed > 0 && !overstressed;
    }

    /** Called by the network solver. */
    void applyNetwork(float newSpeed, boolean newOverstressed, float capacity, float stress) {
        this.networkCapacity = capacity;
        this.networkStress = stress;
        if (Math.abs(newSpeed - speed) > 0.01f || newOverstressed != overstressed) {
            float old = speed;
            speed = newSpeed;
            overstressed = newOverstressed;
            onSpeedChanged(old);
            sync();
        }
    }

    protected void onSpeedChanged(float previousSpeed) {
    }

    /** Ask the solver to recompute the network this block belongs to. */
    public void notifyNetwork() {
        if (level != null && !level.isClientSide) {
            KineticNetworkManager.markDirty(level, worldPosition);
        }
    }

    /** Client: is there a matching kinetic block on that side (used to render connection stubs)? */
    public boolean isConnectedTowards(Direction side) {
        if (level == null || !hasShaftTowards(side)) {
            return false;
        }
        return level.getBlockEntity(worldPosition.relative(side)) instanceof KineticBlockEntity other
                && other.hasShaftTowards(side.getOpposite());
    }

    @Override
    public void clientTick() {
        prevAngle = angle;
        angle += speed * DEG_PER_TICK_PER_RPM * getVisualRatio();
        if (angle > 36000f) {
            angle -= 36000f;
            prevAngle -= 36000f;
        }
    }

    /** Interpolated rotation angle in degrees. */
    public float getRenderAngle(float partialTick) {
        return prevAngle + (angle - prevAngle) * partialTick;
    }

    public Component kineticStatus(Component name) {
        if (overstressed) {
            return Lang.msg("overstressed", name, Lang.num(networkStress), Lang.num(networkCapacity));
        }
        return Lang.msg("kinetic", name, Lang.num(speed), Lang.num(networkStress), Lang.num(networkCapacity));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        notifyNetwork();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide && !unloading) {
            for (Direction dir : Direction.values()) {
                KineticNetworkManager.markDirty(level, worldPosition.relative(dir));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Speed", speed);
        tag.putBoolean("Overstressed", overstressed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speed = tag.getFloat("Speed");
        overstressed = tag.getBoolean("Overstressed");
    }
}
