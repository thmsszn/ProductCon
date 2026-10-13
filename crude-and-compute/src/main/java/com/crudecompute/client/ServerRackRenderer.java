package com.crudecompute.client;

import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Draws the installed GPU blades, their blinking status LEDs and the temperature display. */
public class ServerRackRenderer implements BlockEntityRenderer<ServerRackBlockEntity> {
    private final Font font;

    public ServerRackRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ServerRackBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        int status = be.getStatus();
        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, facing);
        for (int i = 0; i < be.getGpus(); i++) {
            poseStack.pushPose();
            poseStack.translate(0, i * ModModels.RACK_BAY_H, 0);
            RenderUtil.renderModel(poseStack, buffers, ModModels.GPU_BLADE, light, overlay);
            poseStack.popPose();
            float y = ModModels.RACK_BAY0 + i * ModModels.RACK_BAY_H;
            RenderUtil.glowQuad(poseStack, buffers, 0.24f, y + 0.07f, 0.28f, y + 0.11f, 0.079f, ledColor(status, time, i, 0));
            RenderUtil.glowQuad(poseStack, buffers, 0.3f, y + 0.07f, 0.34f, y + 0.11f, 0.079f, ledColor(status, time, i, 1));
            if (status == ServerRackBlockEntity.STATUS_ONLINE || status == ServerRackBlockEntity.STATUS_THROTTLED) {
                float load = 0.4f + 0.6f * (float) Math.abs(Math.sin((time + partialTick) * 0.07 + i * 1.7));
                RenderUtil.glowQuad(poseStack, buffers, 0.4f, y + 0.08f, 0.4f + 0.36f * load, y + 0.1f, 0.079f, 0xFF76B900);
            }
        }
        drawDisplay(be, poseStack, buffers);
        poseStack.popPose();
    }

    private static int ledColor(int status, long time, int slot, int led) {
        return switch (status) {
            case ServerRackBlockEntity.STATUS_ONLINE -> led == 0 ? 0xFF3CFF6E
                    : (((time / 3 + slot * 7L) * 2654435761L >> 7) & 3) == 0 ? 0xFF1A3A22 : 0xFF46C8FF;
            case ServerRackBlockEntity.STATUS_THROTTLED -> (time / 10 + slot) % 2 == 0 ? 0xFFFFB028 : 0xFF4A3410;
            case ServerRackBlockEntity.STATUS_OVERHEATED -> (time / 5) % 2 == 0 ? 0xFFFF3020 : 0xFF400C08;
            default -> 0xFF2A2A2E;
        };
    }

    private void drawDisplay(ServerRackBlockEntity be, PoseStack poseStack, MultiBufferSource buffers) {
        String text = String.format(Locale.ROOT, "%.0f°C  %d GPU", be.getTemperature(), be.getGpus());
        int color = switch (be.getStatus()) {
            case ServerRackBlockEntity.STATUS_ONLINE -> 0xFF7CFFB0;
            case ServerRackBlockEntity.STATUS_THROTTLED -> 0xFFFFC040;
            case ServerRackBlockEntity.STATUS_OVERHEATED, ServerRackBlockEntity.STATUS_NO_POWER -> 0xFFFF5040;
            default -> 0xFF8090A0;
        };
        poseStack.pushPose();
        // screen front face at z = 0.09, spanning x 0.12..0.88 and y 1.82..1.93; text faces north (-Z)
        poseStack.translate(0.85, 1.905, 0.088);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        float scale = 0.0075f;
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(text, 0, 0, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ServerRackBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.2, 0);
    }
}
