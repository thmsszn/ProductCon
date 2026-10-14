package com.crudecompute.client;

import com.crudecompute.CrudeCompute;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;

/** Pose and model helpers shared by all block entity renderers. */
public final class RenderUtil {
    private RenderUtil() {
    }

    public static void renderModel(PoseStack poseStack, MultiBufferSource buffers, ModelResourceLocation location, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(poseStack.last(), consumer, null, model, 1f, 1f, 1f, light, overlay);
    }

    /** Rotation applied by the blockstate files for a horizontally facing model authored facing north. */
    public static float modelYRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    /** Rotates model space (authored facing north) around the block centre to the given facing. */
    public static void rotateToFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-modelYRot(facing)));
        poseStack.translate(-0.5, 0, -0.5);
    }

    /** Like {@link #rotateToFacing} but also handles up/down (model authored facing north). */
    public static void rotateNorthTo(PoseStack poseStack, Direction facing) {
        if (facing.getAxis().isHorizontal()) {
            rotateToFacing(poseStack, facing);
            return;
        }
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? 90 : -90));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    /** Full-bright vertical beam (thin box without caps), e.g. a laser. */
    public static void glowColumn(PoseStack poseStack, MultiBufferSource buffers, float cx, float cz, float half, float y0, float y1, int argb) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CrudeCompute.id("block/led_white"));
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        float x0 = cx - half, x1 = cx + half, z0 = cz - half, z1 = cz + half;
        float[][][] faces = {
                {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {0, 0, -1}},
                {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {0, 0, 1}},
                {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {-1, 0, 0}},
                {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {1, 0, 0}},
        };
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[][] uv = {{sprite.getU1(), sprite.getV0()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU0(), sprite.getV1()}, {sprite.getU0(), sprite.getV0()}};
        for (float[][] face : faces) {
            float[] n = face[4];
            for (int i = 0; i < 4; i++) {
                vc.addVertex(pose, face[i][0], face[i][1], face[i][2]).setColor(r, g, b, a).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, n[0], n[1], n[2]);
            }
        }
    }

    /** Rotates around the block centre so that the model's +Y axis points towards {@code dir}. */
    public static void pointUpTo(PoseStack poseStack, Direction dir) {
        poseStack.translate(0.5, 0.5, 0.5);
        switch (dir) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            default -> {
            }
        }
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    /** Spins around the vertical axis through the block centre. */
    public static void spinY(PoseStack poseStack, float degrees) {
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
        poseStack.translate(-0.5, 0, -0.5);
    }

    /** Renders a half shaft from the block centre to the face {@code dir}, spinning with the network. */
    public static void renderShaftStub(PoseStack poseStack, MultiBufferSource buffers, Direction dir, float angle, int light, int overlay) {
        poseStack.pushPose();
        pointUpTo(poseStack, dir);
        spinY(poseStack, dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? angle : -angle);
        renderModel(poseStack, buffers, ModModels.SHAFT_HALF, light, overlay);
        poseStack.popPose();
    }

    /** Renders a full shaft along {@code axis}. */
    public static void renderShaft(PoseStack poseStack, MultiBufferSource buffers, Direction.Axis axis, float angle, int light, int overlay) {
        poseStack.pushPose();
        switch (axis) {
            case X -> pointUpTo(poseStack, Direction.EAST);
            case Z -> pointUpTo(poseStack, Direction.SOUTH);
            default -> {
            }
        }
        spinY(poseStack, angle);
        renderModel(poseStack, buffers, ModModels.SHAFT, light, overlay);
        poseStack.popPose();
    }

    /** A full-bright coloured quad facing -Z (model space), e.g. an LED. */
    public static void glowQuad(PoseStack poseStack, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb) {
        colorQuad(poseStack, buffers, x0, y0, x1, y1, z, argb, LightTexture.FULL_BRIGHT);
    }

    /** A flat coloured quad facing -Z (model space) with the given light. */
    public static void colorQuad(PoseStack poseStack, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CrudeCompute.id("block/led_white"));
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        int overlay = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, x1, y1, z).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x1, y0, z).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x0, y0, z).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x0, y1, z).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
}
