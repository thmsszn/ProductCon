package de.localai.baumeister;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaumeisterClient implements ClientModInitializer {
    public static final String MOD_ID = "ki_baumeister";
    public static final Logger LOG = LoggerFactory.getLogger("KI-Baumeister");
    /** Virtuelle Threads für Netzwerk und Plan-Berechnung – blockieren nie den Spiel-Thread. */
    public static final ExecutorService EXECUTOR =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ki-baumeister-", 0).factory());

    private static KeyMapping openKey;
    private static KeyMapping corner1Key;
    private static KeyMapping corner2Key;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.open", InputConstants.KEY_K, category));
        // Standardmäßig nicht belegt; in den Steuerungs-Einstellungen frei zuweisbar.
        corner1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.corner1", -1, category));
        corner2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.corner2", -1, category));

        ClientTickEvents.END_CLIENT_TICK.register(BaumeisterClient::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "progress"), ProgressHud::extract);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(Session::reset));
        BuildManager.register();
        LOG.info("KI-Baumeister geladen – Taste K öffnet das Menü.");
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        while (openKey.consumeClick()) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new BuilderScreen());
        }
        while (corner1Key.consumeClick()) {
            Session.captureCorner(true);
            mc.player.sendOverlayMessage(Component.literal(Session.status()));
        }
        while (corner2Key.consumeClick()) {
            Session.captureCorner(false);
            mc.player.sendOverlayMessage(Component.literal(Session.status()));
        }
        WorldPreview.tick(mc);
        ProgressHud.tick();
    }
}
