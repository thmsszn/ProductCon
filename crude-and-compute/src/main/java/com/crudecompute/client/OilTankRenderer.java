package com.crudecompute.client;

import com.crudecompute.oil.OilTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shows the fill level in the vertical sight glass of the oil tank. */
public class OilTankRenderer implements BlockEntityRenderer<OilTankBlockEntity> {
    private static final float GLASS_BOTTOM = 0.185f;
    private static final float GLASS_TOP = 0.775f;

    public OilTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OilTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fluid = be.getFluid();
        if (fluid.isEmpty()) {
            return;
        }
        float fill = Math.min(1f, fluid.getAmount() / (float) OilTankBlockEntity.CAPACITY);
        int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) | 0xFF000000;
        float top = GLASS_BOTTOM + (GLASS_TOP - GLASS_BOTTOM) * fill;
        RenderUtil.colorQuad(poseStack, buffers, 0.462f, GLASS_BOTTOM, 0.538f, top, 0.0435f, tint, light);
    }
}
