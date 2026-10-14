package com.crudecompute.client;

import com.crudecompute.logistics.ConveyorBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Scrolls the belt cleats and draws the items riding on the belt. */
public class ConveyorRenderer implements BlockEntityRenderer<ConveyorBlockEntity> {
    public ConveyorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ConveyorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, be.getBlockState().getValue(HorizontalMachineBlock.FACING));
        // distance travelled = angle / 192, cleats repeat every quarter block
        float phase = (be.getRenderAngle(partialTick) / 192f) % 0.25f;
        if (phase < 0) {
            phase += 0.25f;
        }
        for (int k = -1; k < 4; k++) {
            float shift = -(phase + k * 0.25f) + 0.375f;
            if (0.5f + shift < 0.04f || 0.5f + shift > 0.96f) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(0, 0, shift);
            RenderUtil.renderModel(poseStack, buffers, ModModels.CONVEYOR_RIB, light, overlay);
            poseStack.popPose();
        }
        for (ConveyorBlockEntity.Entry entry : be.getItems()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.375 + 0.06, 1.0 - entry.progress);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            poseStack.scale(0.32f, 0.32f, 0.32f);
            Minecraft.getInstance().getItemRenderer().renderStatic(entry.stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, be.getLevel(), 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
