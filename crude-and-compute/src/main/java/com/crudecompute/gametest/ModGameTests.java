package com.crudecompute.gametest;

import com.crudecompute.CrudeCompute;
import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.kinetic.DieselEngineBlockEntity;
import com.crudecompute.kinetic.GeneratorBlockEntity;
import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.kinetic.ShaftBlock;
import com.crudecompute.oil.PumpjackBlockEntity;
import com.crudecompute.registry.ModBlocks;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.HorizontalMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CrudeCompute.MODID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    private static BlockState facing(Block block, Direction dir) {
        return block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, dir);
    }

    private static BlockState shaftX() {
        return ModBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X);
    }

    private static void fuel(GameTestHelper helper, BlockPos engine) {
        DieselEngineBlockEntity be = helper.getBlockEntity(engine);
        be.getFuelHandler().fill(new FluidStack(ModFluids.DIESEL.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
    }

    /** Engine -> shaft -> shaft -> generator: the generator spins at engine speed and produces FE. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void engineDrivesGenerator(GameTestHelper helper) {
        BlockPos engine = new BlockPos(1, 2, 4);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.EAST));
        helper.setBlock(new BlockPos(2, 2, 4), shaftX());
        helper.setBlock(new BlockPos(3, 2, 4), shaftX());
        BlockPos generator = new BlockPos(4, 2, 4);
        helper.setBlock(generator, facing(ModBlocks.GENERATOR.get(), Direction.EAST));
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            GeneratorBlockEntity gen = helper.getBlockEntity(generator);
            helper.assertTrue(gen.getSpeed() == DieselEngineBlockEntity.SPEED, "generator should spin at 64 RPM, is " + gen.getSpeed());
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(generator), Direction.UP);
            helper.assertTrue(energy != null && energy.getEnergyStored() > 0, "generator should have produced energy");
        });
    }

    /** Eight generators (8 x 128 SU) use exactly the 1024 SU of one engine: everything still turns. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eightGeneratorsRunAtCapacity(GameTestHelper helper) {
        BlockPos engine = new BlockPos(0, 1, 4);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.EAST));
        for (int x = 1; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), facing(ModBlocks.GENERATOR.get(), Direction.EAST));
        }
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            KineticBlockEntity last = helper.getBlockEntity(new BlockPos(8, 1, 4));
            helper.assertTrue(last.getSpeed() == 64f && !last.isOverstressed(), "8 generators must run at 64 RPM, speed " + last.getSpeed());
        });
    }

    /** A ninth generator (1152 SU > 1024 SU) overstresses the network and everything stops. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ninthGeneratorOverstresses(GameTestHelper helper) {
        BlockPos engine = new BlockPos(0, 1, 4);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.EAST));
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), facing(ModBlocks.GENERATOR.get(), Direction.EAST));
        }
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            DieselEngineBlockEntity source = helper.getBlockEntity(engine);
            helper.assertTrue(source.getGeneratedSpeed() > 0, "engine should be running");
            KineticBlockEntity first = helper.getBlockEntity(new BlockPos(1, 1, 4));
            KineticBlockEntity ninth = helper.getBlockEntity(new BlockPos(9, 1, 4));
            helper.assertTrue(first.isOverstressed() && ninth.isOverstressed() && first.getSpeed() == 0f,
                    "9 x 128 SU > 1024 SU must overstress the whole network");
        });
    }

    /** A pumpjack over oil shale, driven by an engine, fills its tank with crude oil. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pumpjackProducesCrude(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 2, 6);
        BlockState state = facing(ModBlocks.PUMPJACK.get(), Direction.NORTH);
        helper.setBlock(main, state);
        state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(main), helper.getLevel().getBlockState(helper.absolutePos(main)),
                null, new ItemStack(ModItems.PUMPJACK.get()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(4 + dx, 1, 4 + dz), ModBlocks.OIL_SHALE.get().defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(5, 2, 6), shaftX());
        BlockPos engine = new BlockPos(6, 2, 6);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.WEST));
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.MULTIBLOCK_PART.get(), new BlockPos(4, 4, 4));
            PumpjackBlockEntity pumpjack = helper.getBlockEntity(main);
            helper.assertTrue(pumpjack.getSpeed() == 64f, "pumpjack should be driven at 64 RPM, is " + pumpjack.getSpeed());
            IFluidHandler out = pumpjack.getOutput();
            helper.assertTrue(out.getFluidInTank(1).getAmount() > 20, "pumpjack should have pumped crude oil");
        });
    }

    /** Breaking any part of a multi-block machine removes the whole structure. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void breakingPartRemovesMachine(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 1, 4);
        BlockState state = facing(ModBlocks.REFINERY.get(), Direction.SOUTH);
        helper.setBlock(main, state);
        state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(main), helper.getLevel().getBlockState(helper.absolutePos(main)),
                null, new ItemStack(ModItems.REFINERY.get()));
        helper.assertBlockPresent(ModBlocks.MULTIBLOCK_PART.get(), new BlockPos(4, 3, 4));
        helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 3, 4)), false);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModBlocks.REFINERY.get(), main);
            helper.assertBlockNotPresent(ModBlocks.MULTIBLOCK_PART.get(), new BlockPos(4, 2, 4));
        });
    }

    /** A powered and cooled rack with GPUs delivers compute, and the terminal trains on it. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void datacenterTrainsModel(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(3, 1, 4);
        BlockState rackState = facing(ModBlocks.SERVER_RACK.get(), Direction.NORTH);
        helper.setBlock(rackPos, rackState);
        rackState.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(rackPos), helper.getLevel().getBlockState(helper.absolutePos(rackPos)),
                null, new ItemStack(ModItems.SERVER_RACK.get()));
        ServerRackBlockEntity rack = helper.getBlockEntity(rackPos);
        for (int i = 0; i < 4; i++) {
            rack.addGpu();
        }
        helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.COOLING_UNIT.get().defaultBlockState());
        BlockPos terminalPos = new BlockPos(3, 1, 2);
        helper.setBlock(terminalPos, facing(ModBlocks.AI_TERMINAL.get(), Direction.NORTH));
        AITerminalBlockEntity terminal = helper.getBlockEntity(terminalPos);
        terminal.setDrive(new ItemStack(ModItems.EMPTY_MODEL_DRIVE.get()));
        helper.onEachTick(() -> {
            IEnergyStorage rackEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(rackPos.above()), Direction.WEST);
            if (rackEnergy != null) {
                rackEnergy.receiveEnergy(8192, false);
            }
            IEnergyStorage coolerEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(new BlockPos(4, 1, 4)), Direction.UP);
            if (coolerEnergy != null) {
                coolerEnergy.receiveEnergy(1024, false);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rack.getStatus() == ServerRackBlockEntity.STATUS_ONLINE, "rack should be online, status " + rack.getStatus());
            helper.assertTrue(rack.getCompute() == 4 * ServerRackBlockEntity.PFLOPS_PER_GPU, "4 GPUs should deliver 10 PFLOPS");
            helper.assertTrue(terminal.getRacks() == 1 && terminal.getComputeValue() > 0 && terminal.getProgressPercent() >= 0,
                    "terminal should see the rack and train on its compute");
        });
    }
}
