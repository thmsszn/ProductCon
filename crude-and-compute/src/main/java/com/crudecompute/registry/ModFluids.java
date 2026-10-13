package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CrudeCompute.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CrudeCompute.MODID);

    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_TYPE = FLUID_TYPES.register("crude_oil", () -> new FluidType(
            FluidType.Properties.create()
                    .descriptionId("fluid_type.crudecompute.crude_oil")
                    .density(900)
                    .viscosity(5000)
                    .motionScale(0.0018)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .fallDistanceModifier(0.2f)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<FluidType, FluidType> DIESEL_TYPE = FLUID_TYPES.register("diesel", () -> new FluidType(
            FluidType.Properties.create()
                    .descriptionId("fluid_type.crudecompute.diesel")
                    .density(830)
                    .viscosity(1500)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CRUDE_OIL =
            FLUIDS.register("crude_oil", () -> new BaseFlowingFluid.Source(ModFluids.crudeProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CRUDE_OIL_FLOWING =
            FLUIDS.register("flowing_crude_oil", () -> new BaseFlowingFluid.Flowing(ModFluids.crudeProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DIESEL =
            FLUIDS.register("diesel", () -> new BaseFlowingFluid.Source(ModFluids.dieselProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> DIESEL_FLOWING =
            FLUIDS.register("flowing_diesel", () -> new BaseFlowingFluid.Flowing(ModFluids.dieselProperties()));

    private static BaseFlowingFluid.Properties crude;
    private static BaseFlowingFluid.Properties diesel;

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties crudeProperties() {
        if (crude == null) {
            crude = new BaseFlowingFluid.Properties(CRUDE_OIL_TYPE, CRUDE_OIL, CRUDE_OIL_FLOWING)
                    .bucket(ModItems.CRUDE_OIL_BUCKET)
                    .block(ModBlocks.CRUDE_OIL)
                    .slopeFindDistance(2)
                    .levelDecreasePerBlock(2)
                    .tickRate(25);
        }
        return crude;
    }

    private static BaseFlowingFluid.Properties dieselProperties() {
        if (diesel == null) {
            diesel = new BaseFlowingFluid.Properties(DIESEL_TYPE, DIESEL, DIESEL_FLOWING)
                    .bucket(ModItems.DIESEL_BUCKET)
                    .block(ModBlocks.DIESEL)
                    .slopeFindDistance(3)
                    .levelDecreasePerBlock(1)
                    .tickRate(8);
        }
        return diesel;
    }
}
