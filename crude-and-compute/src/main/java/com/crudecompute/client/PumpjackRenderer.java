package com.crudecompute.client;

import static com.crudecompute.client.ModModels.PJ_BEAM_BACK;
import static com.crudecompute.client.ModModels.PJ_CARRIER_Y;
import static com.crudecompute.client.ModModels.PJ_CRANK_R;
import static com.crudecompute.client.ModModels.PJ_CRANK_Y;
import static com.crudecompute.client.ModModels.PJ_CRANK_Z;
import static com.crudecompute.client.ModModels.PJ_EQUALIZER_DROP;
import static com.crudecompute.client.ModModels.PJ_HEAD_R;
import static com.crudecompute.client.ModModels.PJ_PIVOT_Y;
import static com.crudecompute.client.ModModels.PJ_PIVOT_Z;

import com.crudecompute.oil.PumpjackBlockEntity;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Animates the pumpjack: the crank turns (geared down from the input shaft), the pitman arms push the walking
 * beam up and down and the horsehead lifts the polished rod out of the wellhead.
 */
public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity> {
    private static final float[] SIDES = {0.09f, 0.91f};
    private static final float[] CABLES = {0.46f, 0.54f};

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PumpjackBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalMachineBlock.FACING);
        float shaftAngle = be.getRenderAngle(partialTick);
        float phi = (float) Math.toRadians(shaftAngle * ModModels.PJ_GEAR_RATIO);
        float sinPhi = Mth.sin(phi);
        float cosPhi = Mth.cos(phi);
        // walking beam angle: the equalizer follows the vertical travel of the crank pin
        float alpha = (float) Math.asin(Mth.clamp(PJ_CRANK_R / PJ_BEAM_BACK * sinPhi, -1f, 1f));
        float rodLift = PJ_HEAD_R * alpha;

        poseStack.pushPose();
        RenderUtil.rotateToFacing(poseStack, facing);

        // crank + counterweights
        poseStack.pushPose();
        poseStack.translate(0, PJ_CRANK_Y, PJ_CRANK_Z);
        poseStack.mulPose(Axis.XP.rotation(phi));
        poseStack.translate(0, -PJ_CRANK_Y, -PJ_CRANK_Z);
        RenderUtil.renderModel(poseStack, buffers, ModModels.PUMPJACK_CRANK, light, overlay);
        poseStack.popPose();

        // walking beam + horsehead
        poseStack.pushPose();
        poseStack.translate(0, PJ_PIVOT_Y, PJ_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotation(alpha));
        poseStack.translate(0, -PJ_PIVOT_Y, -PJ_PIVOT_Z);
        RenderUtil.renderModel(poseStack, buffers, ModModels.PUMPJACK_BEAM, light, overlay);
        poseStack.popPose();

        // pitman arms from the crank pins to the equalizer
        float pinY = PJ_CRANK_Y - PJ_CRANK_R * sinPhi;
        float pinZ = PJ_CRANK_Z + PJ_CRANK_R * cosPhi;
        float relY = -PJ_EQUALIZER_DROP;
        float relZ = PJ_BEAM_BACK;
        float endY = PJ_PIVOT_Y + relY * Mth.cos(alpha) - relZ * Mth.sin(alpha);
        float endZ = PJ_PIVOT_Z + relY * Mth.sin(alpha) + relZ * Mth.cos(alpha);
        for (float x : SIDES) {
            rod(poseStack, buffers, ModModels.PUMPJACK_PITMAN, x, pinY, pinZ, endY, endZ, light, overlay);
        }

        // bridle cables + polished rod
        float carrierY = PJ_CARRIER_Y + rodLift;
        for (float x : CABLES) {
            rod(poseStack, buffers, ModModels.PUMPJACK_CABLE, x, carrierY, -1.5f, PJ_PIVOT_Y, -1.5f, light, overlay);
        }
        poseStack.pushPose();
        poseStack.translate(0, rodLift, 0);
        RenderUtil.renderModel(poseStack, buffers, ModModels.PUMPJACK_ROD, light, overlay);
        poseStack.popPose();

        poseStack.popPose();

        // input shafts on the gearbox sides
        for (Direction dir : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            if (be.isConnectedTowards(dir)) {
                RenderUtil.renderShaftStub(poseStack, buffers, dir, shaftAngle, light, overlay);
            }
        }
    }

    /** Stretches a unit rod model (along +Y) between two points in the y/z plane at the given x. */
    private static void rod(PoseStack poseStack, MultiBufferSource buffers, ModelResourceLocation model, float x,
                            float y0, float z0, float y1, float z1, int light, int overlay) {
        float dy = y1 - y0;
        float dz = z1 - z0;
        float length = Mth.sqrt(dy * dy + dz * dz);
        if (length < 1.0e-4f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y0, z0);
        poseStack.mulPose(Axis.XP.rotation((float) Math.atan2(dz, dy)));
        poseStack.scale(1f, length, 1f);
        RenderUtil.renderModel(poseStack, buffers, model, light, overlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 3.5, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(PumpjackBlockEntity be) {
        return true;
    }
}
