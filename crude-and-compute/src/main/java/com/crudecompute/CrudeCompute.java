package com.crudecompute;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CrudeCompute.MODID)
public class CrudeCompute {
    public static final String MODID = "crudecompute";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrudeCompute(IEventBus modBus) {
        LOGGER.info("Crude & Compute loading");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
