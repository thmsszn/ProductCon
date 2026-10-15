package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CrudeCompute.MODID);

    /** Fans and PSUs of a running rack: broadband turbulence plus blade-pass whine, loopable. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RACK_HUM = SOUNDS.register("rack_hum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CrudeCompute.MODID, "rack_hum")));

    private ModSounds() {
    }
}
