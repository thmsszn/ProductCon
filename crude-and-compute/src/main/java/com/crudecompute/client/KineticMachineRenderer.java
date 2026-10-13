package com.crudecompute.client;

import com.crudecompute.kinetic.DieselEngineBlockEntity;
import com.crudecompute.kinetic.GeneratorBlockEntity;
import com.crudecompute.kinetic.HandCrankBlock;
import com.crudecompute.kinetic.HandCrankBlockEntity;
import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.kinetic.WindmillBlockEntity;
import com.crudecompute.oil.RefineryBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Animated parts of the single-block kinetic machines (and the refinery's drive shafts). */
public class KineticMachineRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {
    public KineticMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float angle = be.getRenderAngle(partialTick);
        if (be instanceof HandCrankBlockEntity) {
            Direction facing = be.getBlockState().getValue(HandCrankBlock.FACING);
            poseStack.pushPose();
            RenderUtil.pointUpTo(poseStack, facing);
            RenderUtil.spinY(poseStack, angle);
            RenderUtil.renderModel(poseStack, buffers, ModModels.HAND_CRANK_HANDLE, light, overlay);
            poseStack.popPose();
        } else if (be instanceof WindmillBlockEntity) {
            Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
            poseStack.pushPose();
            RenderUtil.rotateToFacing(poseStack, facing);
            poseStack.translate(0.5, ModModels.WINDMILL_HUB_Y, ModModels.WINDMILL_HUB_Z);
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            poseStack.translate(-0.5, -ModModels.WINDMILL_HUB_Y, -ModModels.WINDMILL_HUB_Z);
            RenderUtil.renderModel(poseStack, buffers, ModModels.WINDMILL_ROTOR, light, overlay);
            poseStack.popPose();
            RenderUtil.renderShaftStub(poseStack, buffers, Direction.DOWN, angle, light, overlay);
        } else if (be instanceof DieselEngineBlockEntity) {
            Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
            poseStack.pushPose();
            RenderUtil.rotateToFacing(poseStack, facing);
            poseStack.translate(0.5, 0.5, 0);
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            poseStack.translate(-0.5, -0.5, 0);
            RenderUtil.renderModel(poseStack, buffers, ModModels.ENGINE_FLYWHEEL, light, overlay);
            poseStack.popPose();
            RenderUtil.renderShaftStub(poseStack, buffers, facing, angle, light, overlay);
        } else if (be instanceof GeneratorBlockEntity) {
            Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
            RenderUtil.renderShaft(poseStack, buffers, facing.getAxis(), angle, light, overlay);
        } else if (be instanceof RefineryBlockEntity) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (be.isConnectedTowards(dir)) {
                    RenderUtil.renderShaftStub(poseStack, buffers, dir, angle, light, overlay);
                }
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0);
    }
}
