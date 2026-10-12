package de.localai.baumeister;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class BaumeisterClient implements ClientModInitializer {
    public static volatile BlockPlan pendingPlan;
    public static volatile String status = "Bereit. Ollama muss lokal laufen.";
    public static BlockPos3 cornerA;
    public static BlockPos3 cornerB;
    private KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ki_baumeister", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.ki_baumeister.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null) client.setScreen(new BuilderScreen());
            }
        });
    }

    public static void captureCorner(boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos3 pos = new BlockPos3(mc.player.blockPosition().getX(),
            mc.player.blockPosition().getY(), mc.player.blockPosition().getZ());
        if (first) cornerA = pos; else cornerB = pos;
        status = (first ? "Ecke 1" : "Ecke 2") + " gesetzt: " + pos.x() + ", " + pos.y() + ", " + pos.z();
    }
}
