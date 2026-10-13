package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class WindmillBlockEntity extends KineticBlockEntity {
    public static final float CAPACITY = 256f;

    private float windSpeed;

    public WindmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINDMILL.get(), pos, state);
    }

    @Override
    public void serverTick() {
        if (level == null || level.getGameTime() % 40 != Math.floorMod(worldPosition.hashCode(), 40)) {
            return;
        }
        float wind = computeWind();
        if (wind != windSpeed) {
            windSpeed = wind;
            setChanged();
            notifyNetwork();
        }
    }

    /** 16 RPM at sea level, up to 32 RPM high up; storms add more. Needs open sky. */
    private float computeWind() {
        if (level == null || !level.canSeeSky(worldPosition.above())) {
            return 0;
        }
        int height = worldPosition.getY() - level.getSeaLevel();
        float wind = 16f + Math.max(0, Math.min(16, height / 6f));
        if (level.isThundering()) {
            wind += 8;
        } else if (level.isRaining()) {
            wind += 4;
        }
        return (float) Math.floor(wind);
    }

    public void showStatus(Player player) {
        if (windSpeed <= 0) {
            Lang.warn(player, Lang.msg("windmill.blocked"));
        } else {
            Lang.status(player, kineticStatus(Lang.msg("windmill", Lang.num(windSpeed))));
        }
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side == Direction.DOWN;
    }

    @Override
    public float getGeneratedSpeed() {
        return windSpeed;
    }

    @Override
    public float getStressCapacity() {
        return CAPACITY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Wind", windSpeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        windSpeed = tag.getFloat("Wind");
    }
}
