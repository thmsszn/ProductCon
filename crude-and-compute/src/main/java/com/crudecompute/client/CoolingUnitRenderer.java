package com.crudecompute.client;

import com.crudecompute.datacenter.CoolingUnitBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Spins the big top fan of the cooling unit (spins up and down smoothly). */
public class CoolingUnitRenderer implements BlockEntityRenderer<CoolingUnitBlockEntity> {
    public CoolingUnitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoolingUnitBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float angle = be.prevFanAngle + (be.fanAngle - be.prevFanAngle) * partialTick;
        poseStack.pushPose();
        RenderUtil.spinY(poseStack, angle);
        RenderUtil.renderModel(poseStack, buffers, ModModels.COOLING_FAN, light, overlay);
        poseStack.popPose();
    }
}
