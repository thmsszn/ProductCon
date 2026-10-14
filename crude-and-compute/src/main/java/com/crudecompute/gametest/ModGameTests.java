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

    /** A powered and cooled rack with real servers delivers compute, and the terminal trains on it. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void datacenterTrainsModel(GameTestHelper helper) {
        BlockPos rackPos = new BlockPos(3, 1, 4);
        BlockState rackState = facing(ModBlocks.SERVER_RACK.get(), Direction.NORTH);
        helper.setBlock(rackPos, rackState);
        rackState.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(rackPos), helper.getLevel().getBlockState(helper.absolutePos(rackPos)),
                null, new ItemStack(ModItems.SERVER_RACK.get()));
        ServerRackBlockEntity rack = helper.getBlockEntity(rackPos);
        helper.assertTrue(rack.insert(com.crudecompute.hardware.ServerConfig.preset(4), -1) >= 0, "4U server should fit");
        helper.assertTrue(rack.insert(com.crudecompute.hardware.ServerConfig.preset(2), -1) >= 0, "2U server should fit");
        helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.COOLING_UNIT.get().defaultBlockState());
        BlockPos terminalPos = new BlockPos(3, 1, 2);
        helper.setBlock(terminalPos, facing(ModBlocks.AI_TERMINAL.get(), Direction.NORTH));
        AITerminalBlockEntity terminal = helper.getBlockEntity(terminalPos);
        terminal.setDrive(new ItemStack(ModItems.EMPTY_MODEL_DRIVE.get()));
        helper.onEachTick(() -> {
            IEnergyStorage rackEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(rackPos.above()), Direction.WEST);
            if (rackEnergy != null) {
                rackEnergy.receiveEnergy(16384, false);
            }
            IEnergyStorage coolerEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(new BlockPos(4, 1, 4)), Direction.UP);
            if (coolerEnergy != null) {
                coolerEnergy.receiveEnergy(1024, false);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rack.getStatus() == ServerRackBlockEntity.STATUS_ONLINE, "rack should be online, status " + rack.getStatus());
            helper.assertTrue(rack.getCompute() > 5f, "servers should deliver compute, got " + rack.getCompute());
            helper.assertTrue(terminal.getRacks() == 1 && terminal.getComputeValue() > 0 && terminal.getProgressPercent() >= 0,
                    "terminal should see the rack and train on its compute");
        });
    }

    /** Every shipped preset is a valid configuration; incompatible parts are rejected with the right reason. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hardwareCompatibility(GameTestHelper helper) {
        for (int u : new int[]{1, 2, 4}) {
            helper.assertTrue(com.crudecompute.hardware.ServerConfig.preset(u).validate() == null, "preset " + u + "U must be valid");
        }
        com.crudecompute.hardware.ServerConfig c = new com.crudecompute.hardware.ServerConfig(1);
        helper.assertTrue(err(c.install(cat("mb_4u_gpu"))) == com.crudecompute.hardware.HwError.MOBO_TOO_LARGE, "4U board must not fit a 1U chassis");
        helper.assertTrue(c.install(cat("mb_1u_epyc")) == null, "1U board fits");
        helper.assertTrue(err(c.install(cat("cpu_e2388"))) == com.crudecompute.hardware.HwError.CPU_SOCKET, "wrong socket");
        helper.assertTrue(c.install(cat("cpu_epyc32")) == null, "matching CPU installs");
        helper.assertTrue(err(c.install(cat("cpu_epyc32"))) == com.crudecompute.hardware.HwError.CPU_SLOTS, "second CPU on a 1P board");
        helper.assertTrue(err(c.install(cat("ram_d4_16"))) == com.crudecompute.hardware.HwError.RAM_TYPE, "DDR4 on DDR5 board");
        helper.assertTrue(err(c.install(cat("gpu_h80"))) == com.crudecompute.hardware.HwError.GPU_TOO_BIG, "2U GPU in a 1U chassis");
        com.crudecompute.hardware.ServerConfig w = new com.crudecompute.hardware.ServerConfig(2);
        helper.assertTrue(w.install(cat("mb_2u_ws")) == null && w.install(cat("cpu_i9")) == null && w.install(cat("psu_450")) == null, "base build");
        helper.assertTrue(err(w.install(cat("gpu_m48"))) == com.crudecompute.hardware.HwError.PSU_WEAK, "450 W PSU cannot power a 300 W GPU");
        helper.assertTrue(w.install(cat("psu_1600")) == null && w.install(cat("gpu_m48")) == null, "stronger PSU makes it work");
        helper.succeed();
    }

    private static com.crudecompute.hardware.Part cat(String id) {
        return com.crudecompute.hardware.Catalog.get(id);
    }

    private static com.crudecompute.hardware.HwError err(com.crudecompute.hardware.HwError.Problem p) {
        return p == null ? null : p.error();
    }

    /** More GPUs mean more power and heat; a weak cooler on a big server overheats it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void thermalSimulationReactsToHardware(GameTestHelper helper) {
        com.crudecompute.hardware.ServerConfig withGpu = com.crudecompute.hardware.ServerConfig.preset(2);
        com.crudecompute.hardware.ServerConfig noGpu = withGpu.copy();
        noGpu.gpus.clear();
        withGpu.mode = com.crudecompute.hardware.ServerConfig.MODE_FULL;
        noGpu.mode = com.crudecompute.hardware.ServerConfig.MODE_FULL;
        com.crudecompute.hardware.ServerState a = new com.crudecompute.hardware.ServerState();
        com.crudecompute.hardware.ServerState b = new com.crudecompute.hardware.ServerState();
        for (int i = 0; i < 900; i++) {
            com.crudecompute.hardware.HardwareSim.step(withGpu, a, 22, true, 1.0);
            com.crudecompute.hardware.HardwareSim.step(noGpu, b, 22, true, 1.0);
        }
        helper.assertTrue(a.inputW > b.inputW + 400, "GPUs must raise power draw: " + a.inputW + " vs " + b.inputW);
        helper.assertTrue(a.temp > b.temp + 5, "GPUs must raise temperature: " + a.temp + " vs " + b.temp);
        helper.assertTrue(a.compute > b.compute, "GPUs must add compute");

        com.crudecompute.hardware.ServerConfig weak = com.crudecompute.hardware.ServerConfig.preset(4);
        weak.cooler = "cool_air1";
        weak.mode = com.crudecompute.hardware.ServerConfig.MODE_FULL;
        com.crudecompute.hardware.ServerState hot = new com.crudecompute.hardware.ServerState();
        boolean everOverheated = false;
        for (int i = 0; i < 1500; i++) {
            com.crudecompute.hardware.HardwareSim.step(weak, hot, 22, true, 1.0);
            everOverheated |= hot.status == com.crudecompute.hardware.ServerState.OVERHEATED;
        }
        helper.assertTrue(everOverheated, "a 4U GPU server with a tiny air cooler must overheat");

        com.crudecompute.hardware.ServerState dark = new com.crudecompute.hardware.ServerState();
        com.crudecompute.hardware.HardwareSim.step(withGpu, dark, 22, false, 1.0);
        helper.assertTrue(dark.status == com.crudecompute.hardware.ServerState.NO_POWER && dark.compute == 0f, "no power means no compute");
        helper.succeed();
    }

    /** Solar output follows the time of day and only panels connected to an inverter count. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void solarFollowsDaytime(GameTestHelper helper) {
        BlockPos inverter = new BlockPos(3, 1, 3);
        helper.setBlock(inverter, facing(ModBlocks.INVERTER.get(), Direction.NORTH));
        helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        helper.setBlock(new BlockPos(5, 1, 3), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 4), ModBlocks.SOLAR_PANEL_MONO.get().defaultBlockState());
        helper.setBlock(new BlockPos(8, 1, 8), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        helper.getLevel().setDayTime(6000);
        java.util.concurrent.atomic.AtomicReference<Double> noon = new java.util.concurrent.atomic.AtomicReference<>(0.0);
        helper.runAfterDelay(45, () -> {
            com.crudecompute.solar.InverterBlockEntity inv = helper.getBlockEntity(inverter);
            noon.set(inv.getAcW());
            helper.assertTrue(inv.getPanels() == 3, "only the 3 connected panels count, found " + inv.getPanels());
            helper.getLevel().setDayTime(18000);
        });
        helper.succeedWhen(() -> {
            com.crudecompute.solar.InverterBlockEntity inv = helper.getBlockEntity(inverter);
            helper.assertTrue(noon.get() > 1500, "noon output should exceed 1.5 kW, was " + noon.get());
            helper.assertTrue(inv.getAcW() == 0.0, "no output at night, was " + inv.getAcW());
        });
    }

    /** A pump driven from above moves crude oil sideways from one tank into another. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pumpMovesFluidSideways(GameTestHelper helper) {
        BlockPos from = new BlockPos(2, 1, 4);
        BlockPos pump = new BlockPos(3, 1, 4);
        BlockPos to = new BlockPos(4, 1, 4);
        helper.setBlock(from, ModBlocks.OIL_TANK.get().defaultBlockState());
        helper.setBlock(pump, ModBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(com.crudecompute.oil.MechanicalPumpBlock.FACING, Direction.EAST));
        helper.setBlock(to, ModBlocks.OIL_TANK.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.GEARBOX.get().defaultBlockState());
        BlockPos engine = new BlockPos(3, 2, 5);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(helper, engine);
        com.crudecompute.oil.OilTankBlockEntity source = helper.getBlockEntity(from);
        source.getTank().fill(new FluidStack(ModFluids.CRUDE_OIL.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        helper.succeedWhen(() -> {
            com.crudecompute.oil.OilTankBlockEntity target = helper.getBlockEntity(to);
            helper.assertTrue(target.getFluid().getAmount() >= 1000, "pump should have moved at least a bucket, moved " + target.getFluid().getAmount());
        });
    }

    /** The battery charges through its sides and only discharges through its front, into a cable. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void batteryChargesAndDischargesFront(GameTestHelper helper) {
        BlockPos battery = new BlockPos(3, 1, 4);
        helper.setBlock(battery, facing(ModBlocks.BATTERY.get(), Direction.NORTH));
        BlockPos cable = new BlockPos(3, 1, 3);
        helper.setBlock(cable, ModBlocks.POWER_CABLE.get().defaultBlockState());
        IEnergyStorage side = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(battery), Direction.EAST);
        IEnergyStorage front = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(battery), Direction.NORTH);
        helper.assertTrue(side != null && front != null, "battery should expose energy on all sides");
        helper.assertTrue(side.receiveEnergy(50_000, false) == 8192, "side input is limited to 8192 FE per call");
        helper.assertTrue(front.receiveEnergy(1000, true) == 0 && side.extractEnergy(1000, true) == 0, "front is output only, sides input only");
        helper.succeedWhen(() -> {
            IEnergyStorage cableEnergy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(cable), Direction.UP);
            helper.assertTrue(cableEnergy != null && cableEnergy.getEnergyStored() > 0, "battery should discharge into the cable in front");
        });
    }

    /** Engine -> tower base -> three tower segments -> gearbox on top: the axle carries rotation up the tower. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void windTowerCarriesRotation(GameTestHelper helper) {
        BlockPos engine = new BlockPos(2, 1, 4);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 4), ModBlocks.TOWER_BASE.get().defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 4), ModBlocks.WIND_TOWER.get().defaultBlockState());
        }
        BlockPos top = new BlockPos(3, 5, 4);
        helper.setBlock(top, ModBlocks.GEARBOX.get().defaultBlockState());
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            KineticBlockEntity gearbox = helper.getBlockEntity(top);
            helper.assertTrue(gearbox.getSpeed() == DieselEngineBlockEntity.SPEED && !gearbox.isOverstressed(),
                    "tower should carry 64 RPM to the top, is " + gearbox.getSpeed());
        });
    }

    /** A wind turbine on a tower drives a generator at the foot of the tower. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void windTowerDrivesGenerator(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 4), ModBlocks.TOWER_BASE.get().defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 4), ModBlocks.WIND_TOWER.get().defaultBlockState());
        }
        helper.setBlock(new BlockPos(3, 5, 4), facing(ModBlocks.WINDMILL.get(), Direction.NORTH));
        BlockPos generator = new BlockPos(4, 1, 4);
        helper.setBlock(generator, facing(ModBlocks.GENERATOR.get(), Direction.EAST));
        helper.succeedWhen(() -> {
            GeneratorBlockEntity gen = helper.getBlockEntity(generator);
            helper.assertTrue(gen.getSpeed() > 0, "generator at the tower foot should spin");
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(generator), Direction.UP);
            helper.assertTrue(energy != null && energy.getEnergyStored() > 0, "wind farm should have produced energy");
        });
    }

    /** Battery -> three cables -> cooling unit: energy arrives, and the last cable ends in a plug on the machine. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cableChainDeliversPowerWithPlug(GameTestHelper helper) {
        BlockPos battery = new BlockPos(3, 1, 6);
        helper.setBlock(battery, facing(ModBlocks.BATTERY.get(), Direction.NORTH));
        com.crudecompute.datacenter.PowerCableBlock cableBlock = ModBlocks.POWER_CABLE.get();
        for (int z = 5; z >= 3; z--) {
            BlockPos pos = new BlockPos(3, 1, z);
            helper.setBlock(pos, cableBlock.connectionState(cableBlock.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
        }
        BlockPos cooler = new BlockPos(3, 1, 2);
        helper.setBlock(cooler, ModBlocks.COOLING_UNIT.get().defaultBlockState());
        // re-evaluate the connections now that every neighbour exists
        for (int z = 5; z >= 3; z--) {
            BlockPos pos = new BlockPos(3, 1, z);
            BlockPos abs = helper.absolutePos(pos);
            helper.getLevel().setBlock(abs, cableBlock.connectionState(helper.getLevel().getBlockState(abs), helper.getLevel(), abs), Block.UPDATE_ALL);
        }
        BlockState last = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertTrue(last.getValue(com.crudecompute.datacenter.PowerCableBlock.PLUGS.get(Direction.NORTH)), "cable next to the machine must end in a plug");
        BlockState middle = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 1, 4)));
        helper.assertFalse(middle.getValue(com.crudecompute.datacenter.PowerCableBlock.PLUGS.get(Direction.NORTH)), "cable-to-cable link has no plug");
        IEnergyStorage side = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(battery), Direction.EAST);
        side.receiveEnergy(8000, false);
        helper.succeedWhen(() -> {
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(cooler), Direction.SOUTH);
            helper.assertTrue(energy != null && energy.getEnergyStored() > 0, "cooling unit should receive power through the cables");
        });
    }

    /** Solar panels linked to the inverter through a DC cable are counted, and the inverter's front exposes its output. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void inverterCountsCabledPanels(GameTestHelper helper) {
        BlockPos inverter = new BlockPos(3, 1, 4);
        helper.setBlock(inverter, facing(ModBlocks.INVERTER.get(), Direction.NORTH));
        helper.setBlock(new BlockPos(3, 1, 5), ModBlocks.POWER_CABLE.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 6), ModBlocks.POWER_CABLE.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 7), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        helper.setBlock(new BlockPos(4, 1, 7), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        IEnergyStorage front = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(inverter), Direction.NORTH);
        IEnergyStorage back = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(inverter), Direction.SOUTH);
        helper.assertTrue(front != null && back == null, "inverter delivers through its front only");
        helper.succeedWhen(() -> {
            com.crudecompute.solar.InverterBlockEntity be = helper.getBlockEntity(inverter);
            helper.assertTrue(be.getPanels() == 2, "both panels behind the cable should be linked, found " + be.getPanels());
        });
    }

    /** The lithography machine turns wafer + plastic + gold nugget into two circuit boards. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void chipFabEtchesCircuitBoards(GameTestHelper helper) {
        BlockPos fab = new BlockPos(3, 1, 4);
        helper.setBlock(fab, facing(ModBlocks.CHIP_FAB.get(), Direction.NORTH));
        BlockPos engine = new BlockPos(3, 1, 5);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(helper, engine);
        net.neoforged.neoforge.items.IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(fab), Direction.UP);
        helper.assertTrue(handler != null, "chip fab should expose an item handler");
        for (ItemStack stack : new ItemStack[]{new ItemStack(ModItems.SILICON_WAFER.get(), 2), new ItemStack(ModItems.PLASTIC.get(), 2),
                new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 2)}) {
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack, false);
            helper.assertTrue(rest.isEmpty(), "inputs should be accepted: " + stack);
        }
        helper.succeedWhen(() -> {
            ItemStack out = handler.extractItem(3, 64, true);
            helper.assertTrue(out.is(ModItems.CIRCUIT_BOARD.get()) && out.getCount() >= 4, "two runs should yield 4 circuit boards, got " + out);
        });
    }

    /** Items put on one conveyor travel over a second belt and end up in a chest. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void conveyorCarriesItemsIntoChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 6), facing(ModBlocks.CONVEYOR_BELT.get(), Direction.NORTH));
        helper.setBlock(new BlockPos(3, 1, 5), facing(ModBlocks.CONVEYOR_BELT.get(), Direction.NORTH));
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.CHEST.defaultBlockState());
        helper.setBlock(new BlockPos(4, 1, 6), ModBlocks.GEARBOX.get().defaultBlockState());
        helper.setBlock(new BlockPos(4, 1, 5), ModBlocks.GEARBOX.get().defaultBlockState());
        BlockPos engine = new BlockPos(5, 1, 6);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.WEST));
        fuel(helper, engine);
        net.neoforged.neoforge.items.IItemHandler in = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(new BlockPos(3, 1, 6)), Direction.UP);
        helper.assertTrue(in != null && in.insertItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), false).isEmpty(), "belt should accept an item");
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(3, 1, 4));
            helper.assertTrue(chest.getItem(0).is(net.minecraft.world.item.Items.IRON_INGOT), "chest should have received the iron ingot");
        });
    }

    /** The derrick, driven from behind, drills crude oil out of the shale below its drill pipe. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void derrickDrillsCrude(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 1, 6);
        BlockState state = facing(ModBlocks.OIL_DERRICK.get(), Direction.NORTH);
        helper.setBlock(main, state);
        state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(main), helper.getLevel().getBlockState(helper.absolutePos(main)),
                null, new ItemStack(ModItems.OIL_DERRICK.get()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(4 + dx, 0, 5 + dz), ModBlocks.OIL_SHALE.get().defaultBlockState());
            }
        }
        BlockPos engine = new BlockPos(4, 1, 7);
        helper.setBlock(engine, facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(helper, engine);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.MULTIBLOCK_PART.get(), new BlockPos(4, 7, 5));
            com.crudecompute.oil.OilDerrickBlockEntity derrick = helper.getBlockEntity(main);
            helper.assertTrue(derrick.getSpeed() == 64f, "derrick should be driven at 64 RPM, is " + derrick.getSpeed());
            helper.assertTrue(derrick.getOutput().getFluidInTank(1).getAmount() > 50, "derrick should have drilled crude oil");
        });
    }
}
