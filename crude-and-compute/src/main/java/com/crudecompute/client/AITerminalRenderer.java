package com.crudecompute.client;

import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Live datacenter dashboard on the curved monitor of the AI terminal. */
public class AITerminalRenderer implements BlockEntityRenderer<AITerminalBlockEntity> {
    private static final int TITLE = 0xFF59D8FF;
    private static final int TEXT = 0xFFB8F5C8;
    private static final int WARN = 0xFFFF7050;
    private static final int ACCENT = 0xFFFFD060;
    private static final float SCALE = 0.0052f;
    private static final int MAX_WIDTH = 140;

    private final Font font;

    public AITerminalRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(AITerminalBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, facing);
        // text plane just in front of the curved screen (centre z = 0.6, edges curve forward)
        poseStack.translate(0.865, 0.935, 0.566);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(SCALE, -SCALE, SCALE);

        int y = 0;
        y = line(poseStack, buffers, Component.translatable("screen.crudecompute.title").getString(), y, TITLE);
        y = line(poseStack, buffers, Component.translatable("screen.crudecompute.racks", be.getRacks(), be.getGpus()).getString(), y, TEXT);
        y = line(poseStack, buffers, Component.translatable("screen.crudecompute.compute", Lang.num(be.getComputeValue())).getString(), y,
                be.getComputeValue() > 0 ? TEXT : WARN);
        y = line(poseStack, buffers, Component.translatable("screen.crudecompute.power", be.getPower(), Lang.num(be.getMaxTemp())).getString(), y,
                be.getMaxTemp() >= 80 ? WARN : TEXT);

        int percent = be.getProgressPercent();
        String training;
        int trainingColor = ACCENT;
        if (percent < 0) {
            training = Component.translatable("screen.crudecompute.no_drive").getString();
        } else if (percent >= 100) {
            training = Component.translatable("screen.crudecompute.done").getString();
            trainingColor = (time / 10) % 2 == 0 ? 0xFF7CFF8C : 0xFF3C8C4C;
        } else {
            training = Component.translatable("screen.crudecompute.training", percent).getString();
        }
        y = line(poseStack, buffers, training, y, trainingColor);
        if (percent >= 0 && percent < 100) {
            int filled = Math.round(percent / 100f * 24);
            String bar = "[" + "|".repeat(filled) + ".".repeat(24 - filled) + "]";
            y = line(poseStack, buffers, bar, y, ACCENT);
        }
        String chatter = be.getComputeValue() > 0
                ? Component.translatable("screen.crudecompute.chatter." + ((time / 60) % 6)).getString() + ((time / 8) % 2 == 0 ? "_" : "")
                : Component.translatable("screen.crudecompute.offline").getString();
        line(poseStack, buffers, chatter, y, be.getComputeValue() > 0 ? 0xFF8FA8C0 : WARN);
        poseStack.popPose();
    }

    private int line(PoseStack poseStack, MultiBufferSource buffers, String text, int y, int color) {
        String clipped = font.plainSubstrByWidth(text, MAX_WIDTH);
        font.drawInBatch(clipped, 0, y, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        return y + 11;
    }
}
