package com.crudecompute.client;

import com.crudecompute.kinetic.TransmissionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Spinning shafts, and the six shaft stubs of a gearbox. */
public class TransmissionRenderer implements BlockEntityRenderer<TransmissionBlockEntity> {
    public TransmissionRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TransmissionBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        float angle = be.getRenderAngle(partialTick);
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            RenderUtil.renderShaft(poseStack, buffers, state.getValue(BlockStateProperties.AXIS), angle, light, overlay);
            return;
        }
        for (Direction dir : Direction.values()) {
            RenderUtil.renderShaftStub(poseStack, buffers, dir, angle, light, overlay);
        }
    }
}
