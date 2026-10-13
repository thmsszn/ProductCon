package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.datacenter.CoolingUnitBlockEntity;
import com.crudecompute.datacenter.PowerCableBlockEntity;
import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.kinetic.DieselEngineBlockEntity;
import com.crudecompute.kinetic.GeneratorBlockEntity;
import com.crudecompute.kinetic.HandCrankBlockEntity;
import com.crudecompute.kinetic.TransmissionBlockEntity;
import com.crudecompute.kinetic.WindmillBlockEntity;
import com.crudecompute.oil.FluidPipeBlockEntity;
import com.crudecompute.oil.OilTankBlockEntity;
import com.crudecompute.oil.PumpjackBlockEntity;
import com.crudecompute.oil.RefineryBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CrudeCompute.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmissionBlockEntity>> SHAFT =
            register("shaft", TransmissionBlockEntity::shaft, ModBlocks.SHAFT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmissionBlockEntity>> GEARBOX =
            register("gearbox", TransmissionBlockEntity::gearbox, ModBlocks.GEARBOX);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HandCrankBlockEntity>> HAND_CRANK =
            register("hand_crank", HandCrankBlockEntity::new, ModBlocks.HAND_CRANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindmillBlockEntity>> WINDMILL =
            register("windmill", WindmillBlockEntity::new, ModBlocks.WINDMILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DieselEngineBlockEntity>> DIESEL_ENGINE =
            register("diesel_engine", DieselEngineBlockEntity::new, ModBlocks.DIESEL_ENGINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR =
            register("generator", GeneratorBlockEntity::new, ModBlocks.GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK =
            register("pumpjack", PumpjackBlockEntity::new, ModBlocks.PUMPJACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY =
            register("refinery", RefineryBlockEntity::new, ModBlocks.REFINERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE =
            register("fluid_pipe", FluidPipeBlockEntity::new, ModBlocks.FLUID_PIPE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilTankBlockEntity>> OIL_TANK =
            register("oil_tank", OilTankBlockEntity::new, ModBlocks.OIL_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerCableBlockEntity>> POWER_CABLE =
            register("power_cable", PowerCableBlockEntity::new, ModBlocks.POWER_CABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerRackBlockEntity>> SERVER_RACK =
            register("server_rack", ServerRackBlockEntity::new, ModBlocks.SERVER_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolingUnitBlockEntity>> COOLING_UNIT =
            register("cooling_unit", CoolingUnitBlockEntity::new, ModBlocks.COOLING_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AITerminalBlockEntity>> AI_TERMINAL =
            register("ai_terminal", AITerminalBlockEntity::new, ModBlocks.AI_TERMINAL);

    private ModBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
