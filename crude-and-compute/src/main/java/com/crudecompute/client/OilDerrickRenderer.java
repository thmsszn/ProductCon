package com.crudecompute.client;

import com.crudecompute.oil.OilDerrickBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** The travelling block rises and falls on its cables while the kelly spins in the rotary table. */
public class OilDerrickRenderer implements BlockEntityRenderer<OilDerrickBlockEntity> {
    private static final float CX = 0.5f;
    private static final float CZ = -0.5f;
    private static final float KELLY_BOTTOM = 1.16f;
    private static final float CROWN = 6.75f;

    public OilDerrickRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OilDerrickBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
        float angle = be.getRenderAngle(partialTick);
        float blockY = 4.2f + 1.3f * Mth.sin((float) Math.toRadians(angle * 0.5f));

        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, facing);

        poseStack.pushPose();
        poseStack.translate(0, blockY, 0);
        RenderUtil.renderModel(poseStack, buffers, ModModels.DERRICK_BLOCK, light, overlay);
        poseStack.popPose();

        // kelly: from the rotary table up to the hook, spinning
        float kellyTop = blockY - 0.62f;
        poseStack.pushPose();
        poseStack.translate(CX, KELLY_BOTTOM, CZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * 2f));
        poseStack.scale(1f, kellyTop - KELLY_BOTTOM, 1f);
        RenderUtil.renderModel(poseStack, buffers, ModModels.DERRICK_KELLY, light, overlay);
        poseStack.popPose();

        // two cables from the crown block down to the travelling block
        for (float dx : new float[]{-0.08f, 0.08f}) {
            poseStack.pushPose();
            poseStack.translate(CX + dx, blockY + 0.12f, CZ);
            poseStack.scale(0.6f, CROWN - blockY - 0.12f, 0.6f);
            RenderUtil.renderModel(poseStack, buffers, ModModels.PUMPJACK_CABLE, light, overlay);
            poseStack.popPose();
        }
        poseStack.popPose();

        if (be.isConnectedTowards(facing.getOpposite())) {
            RenderUtil.renderShaftStub(poseStack, buffers, facing.getOpposite(), angle, light, overlay);
        }
    }

    @Override
    public AABB getRenderBoundingBox(OilDerrickBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 8, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(OilDerrickBlockEntity be) {
        return true;
    }
}
