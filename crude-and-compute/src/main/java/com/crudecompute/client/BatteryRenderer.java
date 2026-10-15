package com.crudecompute.client;

import com.crudecompute.datacenter.BatteryBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Charge gauge on the front display of the battery bank. */
public class BatteryRenderer implements BlockEntityRenderer<BatteryBlockEntity> {
    private static final int SEGMENTS = 14;
    private static final float X0 = 0.4f;
    private static final float X1 = 1.6f;
    private static final float Y0 = 0.44f;
    private static final float Y1 = 1.5f;
    private static final float Z = -1.004f;

    public BatteryRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BatteryBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float fill = be.getFill();
        int lit = Math.round(fill * SEGMENTS);
        if (fill > 0 && lit == 0) {
            lit = 1;
        }
        int color = fill < 0.1f ? 0xFFFF4030 : fill < 0.3f ? 0xFFFFB020 : 0xFF40FF80;
        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, be.getBlockState().getValue(HorizontalMachineBlock.FACING));
        float step = (Y1 - Y0) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float y0 = Y0 + i * step + 0.006f;
            float y1 = Y0 + (i + 1) * step - 0.006f;
            if (i < lit) {
                RenderUtil.glowQuad(poseStack, buffers, X0, y0, X1, y1, Z, color);
            } else {
                RenderUtil.colorQuad(poseStack, buffers, X0, y0, X1, y1, Z, 0xFF20262C, light);
            }
        }
        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BatteryBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(2.0);
    }
}
