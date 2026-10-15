package com.crudecompute.client;

import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/** The loud whine of a running server rack: looping, louder with more servers and faster fans. */
public class RackNoise extends AbstractTickableSoundInstance {
    private final ServerRackBlockEntity rack;
    private float targetVolume = 0.5f;

    private RackNoise(ServerRackBlockEntity rack) {
        super(ModSounds.RACK_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.rack = rack;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05f;
        this.x = rack.getBlockPos().getX() + 0.5;
        this.y = rack.getBlockPos().getY() + 1.0;
        this.z = rack.getBlockPos().getZ() + 0.5;
    }

    /** Starts the noise of the rack if it is not already playing; returns the instance to keep. */
    public static Object ensure(ServerRackBlockEntity rack, @Nullable Object current) {
        if (current instanceof RackNoise noise && !noise.isStopped()) {
            return noise;
        }
        RackNoise noise = new RackNoise(rack);
        Minecraft.getInstance().getSoundManager().play(noise);
        return noise;
    }

    @Override
    public void tick() {
        if (rack.isRemoved() || rack.getLevel() == null || !rack.isNoisy()) {
            stop();
            return;
        }
        targetVolume = rack.getNoiseLevel();
        volume += (targetVolume - volume) * 0.1f;
        pitch = 0.82f + 0.4f * rack.getFanDuty();
    }
}
