package com.crudecompute.client;

import com.crudecompute.datacenter.ChipFabBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** The gantry scans the laser head over the wafer while the machine is working. */
public class ChipFabRenderer implements BlockEntityRenderer<ChipFabBlockEntity> {
    private static final float PARK_Z = 0.8f;
    private static final float SCAN = 0.13f;

    public ChipFabRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ChipFabBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        boolean working = be.isWorking();
        float x = working ? 0.5f + SCAN * Mth.sin(time * 0.23f) : 0.5f;
        float z = working ? 0.5f + SCAN * Mth.sin(time * 0.071f + 1.3f) : PARK_Z;

        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, be.getBlockState().getValue(HorizontalMachineBlock.FACING));
        if (be.hasWafer()) {
            RenderUtil.renderModel(poseStack, buffers, ModModels.CHIP_FAB_WAFER, light, overlay);
        }
        poseStack.pushPose();
        poseStack.translate(0, 0, z - 0.5f);
        RenderUtil.renderModel(poseStack, buffers, ModModels.CHIP_FAB_BRIDGE, light, overlay);
        poseStack.translate(x - 0.5f, 0, 0);
        RenderUtil.renderModel(poseStack, buffers, ModModels.CHIP_FAB_HEAD, light, overlay);
        poseStack.popPose();
        if (working && be.hasWafer()) {
            int alpha = 0xC0 + (int) (0x3F * Math.abs(Mth.sin(time * 0.9f)));
            RenderUtil.glowColumn(poseStack, buffers, x, z, 0.006f, 0.503f, 0.585f, (alpha << 24) | 0xFF2A1A);
        }
        poseStack.popPose();
    }
}
