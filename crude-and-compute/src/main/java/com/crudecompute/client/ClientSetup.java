package com.crudecompute.client;

import com.crudecompute.CrudeCompute;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModFluids;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = CrudeCompute.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final ResourceLocation WATER_OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.DIESEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.DIESEL_FLOWING.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SHAFT.get(), TransmissionRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GEARBOX.get(), TransmissionRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HAND_CRANK.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WINDMILL.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DIESEL_ENGINE.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GENERATOR.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REFINERY.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PUMPJACK.get(), PumpjackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SERVER_RACK.get(), ServerRackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COOLING_UNIT.get(), CoolingUnitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AI_TERMINAL.get(), AITerminalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OIL_TANK.get(), OilTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MECHANICAL_PUMP.get(), KineticMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY.get(), BatteryRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CHIP_FAB.get(), ChipFabRenderer::new);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation model : ModModels.ALL) {
            event.register(model);
        }
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new FluidLook(0xFF2E2219, new Vector3f(0.04f, 0.03f, 0.02f), 1.5f), ModFluids.CRUDE_OIL_TYPE.get());
        event.registerFluidType(new FluidLook(0xD8E0A82A, new Vector3f(0.55f, 0.42f, 0.1f), 5f), ModFluids.DIESEL_TYPE.get());
    }

    /** Re-tinted vanilla water textures plus thick fog when the camera is submerged. */
    private record FluidLook(int tint, Vector3f fogColor, float fogEnd) implements IClientFluidTypeExtensions {
        @Override
        public ResourceLocation getStillTexture() {
            return WATER_STILL;
        }

        @Override
        public ResourceLocation getFlowingTexture() {
            return WATER_FLOW;
        }

        @Override
        public ResourceLocation getOverlayTexture() {
            return WATER_OVERLAY;
        }

        @Override
        public int getTintColor() {
            return tint;
        }

        @Override
        public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
            return new Vector3f(fogColor);
        }

        @Override
        public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance,
                                    float farDistance, FogShape shape) {
            RenderSystem.setShaderFogStart(0f);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }
}
