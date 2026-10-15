package com.crudecompute;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Right-clicking one of our machines shows its status, which used to swallow the click when the player held a block -
 * so towers, shafts, cables and pipes could not be stacked or extended. With a block in hand the click now places it.
 */
@EventBusSubscriber(modid = CrudeCompute.MODID)
public final class PlacementFix {
    private PlacementFix() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof BlockItem
                && BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).getNamespace().equals(CrudeCompute.MODID)) {
            event.setUseBlock(TriState.FALSE);
        }
    }
}
