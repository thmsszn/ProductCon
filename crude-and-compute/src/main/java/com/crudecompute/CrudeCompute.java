package com.crudecompute;

import com.crudecompute.kinetic.KineticNetworkManager;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModBlocks;
import com.crudecompute.registry.ModCapabilities;
import com.crudecompute.registry.ModCreativeTabs;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Crude &amp; Compute: Create-style rotating machinery, an oil industry and AI datacenter equipment.
 */
@Mod(CrudeCompute.MODID)
public class CrudeCompute {
    public static final String MODID = "crudecompute";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrudeCompute(IEventBus modBus) {
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        com.crudecompute.registry.ModMenus.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        com.crudecompute.registry.ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModCapabilities::register);

        NeoForge.EVENT_BUS.addListener(KineticNetworkManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(KineticNetworkManager::onServerStopped);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
