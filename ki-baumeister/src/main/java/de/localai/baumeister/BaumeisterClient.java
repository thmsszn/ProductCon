package de.localai.baumeister;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
    private static int hudTicks;
    private static boolean wasBusy;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.open", InputConstants.KEY_K, category));
        // Standardmäßig nicht belegt; in den Steuerungs-Einstellungen frei zuweisbar.
        corner1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.corner1", -1, category));
        corner2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ki_baumeister.corner2", -1, category));

        ClientTickEvents.END_CLIENT_TICK.register(BaumeisterClient::tick);
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
        showHudProgress(mc);
    }

    /** Fortschritt in der Aktionsleiste, solange das Menü geschlossen ist (inkl. Abschlussmeldung). */
    private static void showHudProgress(Minecraft mc) {
        BuildManager.Progress p = BuildManager.progress();
        OllamaClient.Generation gen = Session.generation;
        boolean busy = p.running() || gen != null;
        boolean finished = wasBusy && !busy;
        wasBusy = busy;
        if (mc.gui.screen() instanceof BuilderScreen) return;
        if (finished) {
            String msg = gen == null && !p.message().isEmpty() && Session.tone() == Session.Tone.BUSY ? p.message() : Session.status();
            mc.player.sendOverlayMessage(Component.literal("§e" + msg));
            return;
        }
        if (++hudTicks % 10 != 0) return;
        if (p.running()) {
            mc.player.sendOverlayMessage(Component.literal("§a" + p.message() + " " + Math.round(p.fraction() * 100) + " %"));
        } else if (gen != null) {
            mc.player.sendOverlayMessage(Component.literal("§bKI-Baumeister: Plan wird erstellt … " + gen.elements() + " Elemente"));
        }
    }
}
