package com.crudecompute.client;

import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.datacenter.ServerRackBlockEntity.Slot;
import com.crudecompute.hardware.ServerState;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Draws the mounted servers with their live status LEDs and the rack's summary display. */
public class ServerRackRenderer implements BlockEntityRenderer<ServerRackBlockEntity> {
    private final Font font;

    public ServerRackRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ServerRackBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, facing);
        for (Slot slot : be.getSlots()) {
            float y = ServerRackBlockEntity.U0 + slot.u * ServerRackBlockEntity.U_H;
            poseStack.pushPose();
            poseStack.translate(0, y, 0);
            ModelResourceLocation model = switch (slot.cfg.sizeU) {
                case 1 -> ModModels.SERVER_1U;
                case 2 -> ModModels.SERVER_2U;
                default -> ModModels.SERVER_4U;
            };
            RenderUtil.renderModel(poseStack, buffers, model, light, overlay);
            drawLeds(slot, poseStack, buffers, y, slot.cfg.sizeU * ServerRackBlockEntity.U_H, time);
            poseStack.popPose();
        }
        drawDisplay(be, poseStack, buffers);
        drawFans(be, partialTick, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

    /** The slim fans of the rear fan bay: they sit in the roof openings and blow upwards. */
    private void drawFans(ServerRackBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float angle = net.minecraft.util.Mth.lerp(partialTick, be.prevFanAngle, be.fanAngle);
        for (int i = 0; i < ServerRackBlockEntity.FAN_SLOTS; i++) {
            int type = be.getFan(i);
            if (type == 0) {
                continue;
            }
            boolean hp = type == ServerRackBlockEntity.FAN_HIGH;
            poseStack.pushPose();
            poseStack.translate(0.275f + i * 0.15f - 0.5f, 1.945f, 0.69f - 0.5f);
            RenderUtil.renderModel(poseStack, buffers, hp ? ModModels.RACK_FAN_FRAME_HP : ModModels.RACK_FAN_FRAME, light, overlay);
            RenderUtil.spinY(poseStack, angle * (hp ? 1.4f : 1f));
            RenderUtil.renderModel(poseStack, buffers, hp ? ModModels.RACK_FAN_ROTOR_HP : ModModels.RACK_FAN_ROTOR, light, overlay);
            poseStack.popPose();
        }
    }

    /** Power / activity / alert LED on the front of a server (front plane at z = 0.114). */
    private void drawLeds(Slot slot, PoseStack poseStack, MultiBufferSource buffers, float baseY, float height, long time) {
        int status = slot.st.status;
        float ly = height - 0.04f;
        int power = switch (status) {
            case ServerState.OFF -> 0xFF2A2A2E;
            case ServerState.NO_POWER -> 0xFF401010;
            default -> 0xFF3CFF6E;
        };
        boolean running = status == ServerState.ONLINE || status == ServerState.THROTTLED;
        int activity = running && ((time / 2 + slot.u * 5L) * 2654435761L >> 6 & 3) != 0 ? 0xFF46C8FF : 0xFF10222C;
        int alert = switch (status) {
            case ServerState.THROTTLED, ServerState.NO_BOOT -> (time / 10) % 2 == 0 ? 0xFFFFB028 : 0xFF40300A;
            case ServerState.OVERHEATED -> (time / 5) % 2 == 0 ? 0xFFFF3020 : 0xFF400C08;
            default -> 0xFF1E1E22;
        };
        RenderUtil.glowQuad(poseStack, buffers, 0.256f, ly, 0.28f, ly + 0.022f, 0.1135f, power);
        RenderUtil.glowQuad(poseStack, buffers, 0.223f, ly, 0.248f, ly + 0.022f, 0.1135f, activity);
        RenderUtil.glowQuad(poseStack, buffers, 0.191f, ly, 0.215f, ly + 0.022f, 0.1135f, alert);
    }

    private void drawDisplay(ServerRackBlockEntity be, PoseStack poseStack, MultiBufferSource buffers) {
        String text = String.format(Locale.ROOT, "%.0f°C  %dU/%d  %.1f kW", be.getTemperature(), be.usedU(), ServerRackBlockEntity.RACK_U, be.getHeatW() / 1000f);
        int color = switch (be.getStatus()) {
            case ServerRackBlockEntity.STATUS_ONLINE -> 0xFF7CFFB0;
            case ServerRackBlockEntity.STATUS_THROTTLED -> 0xFFFFC040;
            case ServerRackBlockEntity.STATUS_OVERHEATED, ServerRackBlockEntity.STATUS_NO_POWER -> 0xFFFF5040;
            default -> 0xFF8090A0;
        };
        poseStack.pushPose();
        poseStack.translate(0.8, 1.93, 0.085);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        float scale = 0.0068f;
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(text, 0, 0, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ServerRackBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.2, 0);
    }
}
