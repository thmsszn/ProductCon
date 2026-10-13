package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import com.crudecompute.datacenter.AITerminalBlock;
import com.crudecompute.datacenter.CoolingUnitBlock;
import com.crudecompute.datacenter.PowerCableBlock;
import com.crudecompute.datacenter.ServerRackBlock;
import com.crudecompute.kinetic.DieselEngineBlock;
import com.crudecompute.kinetic.GearboxBlock;
import com.crudecompute.kinetic.GeneratorBlock;
import com.crudecompute.kinetic.HandCrankBlock;
import com.crudecompute.kinetic.ShaftBlock;
import com.crudecompute.kinetic.WindmillBlock;
import com.crudecompute.multiblock.MultiblockPartBlock;
import com.crudecompute.oil.FluidPipeBlock;
import com.crudecompute.oil.OilTankBlock;
import com.crudecompute.oil.PumpjackBlock;
import com.crudecompute.oil.RefineryBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CrudeCompute.MODID);

    public static final DeferredBlock<Block> OIL_SHALE = BLOCKS.registerSimpleBlock("oil_shale",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5f, 4f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));

    public static final DeferredBlock<LiquidBlock> CRUDE_OIL = BLOCKS.register("crude_oil",
            () -> new LiquidBlock(ModFluids.CRUDE_OIL.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_BLACK)));
    public static final DeferredBlock<LiquidBlock> DIESEL = BLOCKS.register("diesel",
            () -> new LiquidBlock(ModFluids.DIESEL.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_YELLOW)));

    public static final DeferredBlock<ShaftBlock> SHAFT = BLOCKS.registerBlock("shaft", ShaftBlock::new, machine().strength(1.5f));
    public static final DeferredBlock<GearboxBlock> GEARBOX = BLOCKS.registerBlock("gearbox", GearboxBlock::new, machine());
    public static final DeferredBlock<HandCrankBlock> HAND_CRANK = BLOCKS.registerBlock("hand_crank", HandCrankBlock::new,
            machine().strength(1.5f).sound(SoundType.WOOD));
    public static final DeferredBlock<WindmillBlock> WINDMILL = BLOCKS.registerBlock("windmill", WindmillBlock::new, machine());
    public static final DeferredBlock<DieselEngineBlock> DIESEL_ENGINE = BLOCKS.registerBlock("diesel_engine", DieselEngineBlock::new, machine());
    public static final DeferredBlock<GeneratorBlock> GENERATOR = BLOCKS.registerBlock("generator", GeneratorBlock::new, machine());

    public static final DeferredBlock<PumpjackBlock> PUMPJACK = BLOCKS.registerBlock("pumpjack", PumpjackBlock::new, machine());
    public static final DeferredBlock<RefineryBlock> REFINERY = BLOCKS.registerBlock("refinery", RefineryBlock::new, machine());
    public static final DeferredBlock<FluidPipeBlock> FLUID_PIPE = BLOCKS.registerBlock("fluid_pipe", FluidPipeBlock::new,
            machine().strength(1.5f).sound(SoundType.COPPER));
    public static final DeferredBlock<OilTankBlock> OIL_TANK = BLOCKS.registerBlock("oil_tank", OilTankBlock::new, machine());

    public static final DeferredBlock<PowerCableBlock> POWER_CABLE = BLOCKS.registerBlock("power_cable", PowerCableBlock::new,
            machine().strength(0.8f).sound(SoundType.WOOL));
    public static final DeferredBlock<ServerRackBlock> SERVER_RACK = BLOCKS.registerBlock("server_rack", ServerRackBlock::new,
            machine().lightLevel(state -> state.getValue(ServerRackBlock.ACTIVE) ? 6 : 0));
    public static final DeferredBlock<CoolingUnitBlock> COOLING_UNIT = BLOCKS.registerBlock("cooling_unit", CoolingUnitBlock::new, machine());
    public static final DeferredBlock<AITerminalBlock> AI_TERMINAL = BLOCKS.registerBlock("ai_terminal", AITerminalBlock::new,
            machine().lightLevel(state -> 7));

    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.registerBlock("multiblock_part", MultiblockPartBlock::new,
            machine().noLootTable());

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .isRedstoneConductor((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK);
    }
}
