package de.localai.baumeister;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

public final class BuilderScreen extends Screen {
    private EditBox promptField;
    private EditBox modelField;
    private String message = "Setze beide Ecken an deiner Spielerposition.";
    private boolean generating = false;

    public BuilderScreen() { super(Component.literal("KI-Baumeister")); }

    @Override protected void init() {
        int cx = width / 2;
        promptField = new EditBox(font, cx-155, 44, 310, 22, Component.literal("Bauauftrag"));
        promptField.setMaxLength(500);
        promptField.setValue("Ein kleines modernes Haus aus Quarz und dunklem Holz");
        addRenderableWidget(promptField);
        modelField = new EditBox(font, cx-155, 72, 310, 22, Component.literal("Ollama-Modell"));
        modelField.setValue("qwen2.5:14b");
        modelField.setMaxLength(80);
        addRenderableWidget(modelField);
        addRenderableWidget(Button.builder(Component.literal("Ecke 1 hier setzen"), b -> {
            BaumeisterClient.captureCorner(true); message = BaumeisterClient.status;
        }).bounds(cx-155, 102, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Ecke 2 hier setzen"), b -> {
            BaumeisterClient.captureCorner(false); message = BaumeisterClient.status;
        }).bounds(cx+5, 102, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("KI-Bauplan erstellen"), b -> generate())
            .bounds(cx-155, 132, 310, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Bestätigen und bauen"), b -> buildPlan())
            .bounds(cx-155, 160, 310, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
            .bounds(cx-155, height-30, 310, 22).build());
    }

    private void generate() {
        if (generating) return;
        if (BaumeisterClient.cornerA == null || BaumeisterClient.cornerB == null) {
            message = "Bitte zuerst beide Ecken setzen."; return;
        }
        int w=Math.abs(BaumeisterClient.cornerB.x()-BaumeisterClient.cornerA.x())+1;
        int h=Math.abs(BaumeisterClient.cornerB.y()-BaumeisterClient.cornerA.y())+1;
        int d=Math.abs(BaumeisterClient.cornerB.z()-BaumeisterClient.cornerA.z())+1;
        if ((long)w*h*d > 10000 || w>48 || h>32 || d>48) {
            message = "Bereich zu groß: maximal 48×32×48 und 10.000 Raumblöcke."; return;
        }
        generating = true; message = "KI erstellt Bauplan …";
        OllamaPlanner.create(promptField.getValue(), w, h, d, modelField.getValue().trim())
            .whenComplete((plan, error) -> Minecraft.getInstance().execute(() -> {
                generating = false;
                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    message = "Fehler: " + cause.getMessage() + " — läuft Ollama?";
                } else {
                    BaumeisterClient.pendingPlan = plan;
                    message = plan.summary() + " | " + plan.blocks().size() + " Blöcke bereit.";
                }
            }));
    }

    private void buildPlan() {
        Minecraft mc = Minecraft.getInstance();
        if (BaumeisterClient.pendingPlan == null || BaumeisterClient.cornerA == null || BaumeisterClient.cornerB == null) {
            message = "Erst einen Bauplan erstellen."; return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { message = "Nur in einer Einzelspielerwelt nutzbar."; return; }
        BlockPlan plan = BaumeisterClient.pendingPlan;
        BlockPos3 a=BaumeisterClient.cornerA, b=BaumeisterClient.cornerB;
        int minX=Math.min(a.x(),b.x()), minY=Math.min(a.y(),b.y()), minZ=Math.min(a.z(),b.z());
        ServerLevel level=server.overworld();
        server.execute(() -> {
            int done=0;
            for (PlannedBlock pb : plan.blocks()) {
                var id=ResourceLocation.tryParse(pb.block());
                if (id == null) continue;
                Block block=BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                if (block == null) continue;
                level.setBlock(new BlockPos(minX+pb.x(), minY+pb.y(), minZ+pb.z()), block.defaultBlockState(), 3);
                done++;
            }
            BaumeisterClient.status = "Fertig: " + done + " Blöcke gesetzt.";
        });
        message = "Bauauftrag an die Einzelspielerwelt gesendet.";
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width/2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(message), width/2, 190, 0xDDDDDD);
        graphics.drawCenteredString(font, Component.literal("Nur Einzelspieler. Vorher Welt sichern!"), width/2, 207, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
