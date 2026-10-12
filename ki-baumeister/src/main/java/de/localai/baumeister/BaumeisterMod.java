package de.localai.baumeister;

import java.util.function.BiConsumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Gemeinsamer Einstiegspunkt (Client und integrierter Server): registriert die Baumeister-Axt.
 * Die Auswahl selbst lebt auf dem Client; der Server bricht nur das Abbauen mit der Axt ab.
 */
public final class BaumeisterMod implements ModInitializer {
    public static final String MOD_ID = "ki_baumeister";

    public static final ResourceKey<Item> AXE_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "baumeister_axt"));
    public static final Item BAUMEISTER_AXT = Registry.register(BuiltInRegistries.ITEM, AXE_KEY,
        new BuilderAxeItem(new Item.Properties().setId(AXE_KEY).stacksTo(1).rarity(Rarity.EPIC)));

    /** Wird vom Client gesetzt: (erste Ecke?, Position). Auf einem reinen Server passiert nichts. */
    public static volatile BiConsumer<Boolean, BlockPos> cornerHandler = (first, pos) -> {};
    /** Wird vom Client gesetzt: öffnet das Baumeister-Menü. */
    public static volatile Runnable menuHandler = () -> {};

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(output -> output.accept(BAUMEISTER_AXT));

        // Linksklick mit der Axt: Ecke 1 setzen statt abbauen (auf Client und Server abbrechen)
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!player.getItemInHand(hand).is(item -> item.value() == BAUMEISTER_AXT)) return InteractionResult.PASS;
            if (level.isClientSide()) cornerHandler.accept(true, pos.immutable());
            return InteractionResult.SUCCESS;
        });
    }
}
