package com.crudecompute.client;

import com.crudecompute.kinetic.TowerBaseBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Spinning axle stubs leaving the tower base towards a connected shaft. */
public class TowerBaseRenderer implements BlockEntityRenderer<TowerBaseBlockEntity> {
    public TowerBaseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TowerBaseBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float angle = be.getRenderAngle(partialTick);
        for (Direction dir : Direction.values()) {
            if (be.isConnectedTowards(dir)) {
                RenderUtil.renderShaftStub(poseStack, buffers, dir, angle, light, overlay);
            }
        }
    }
}
