package com.crudecompute.client;

import com.crudecompute.CrudeCompute;
import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.kinetic.DieselEngineBlockEntity;
import com.crudecompute.kinetic.ShaftBlock;
import com.crudecompute.oil.RefineryBlockEntity;
import com.crudecompute.registry.ModBlocks;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.HorizontalMachineBlock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Development helper (only active with {@code -Dcrudecompute.showcase=true}, see the {@code showcaseClient} run):
 * creates a flat creative world, builds a scene with every machine, takes screenshots from several camera
 * positions and quits. Used by CI to verify that all free-form models load and render.
 */
@EventBusSubscriber(modid = CrudeCompute.MODID, value = Dist.CLIENT)
public final class ShowcaseAutomation {
    private static final boolean ENABLED = Boolean.getBoolean("crudecompute.showcase");
    private static final long TIMEOUT_MS = 8 * 60 * 1000L;

    /** Camera shots: name, camera x/y/z (relative to the scene origin, eye height), look-at x/y/z. */
    private static final double[][] SHOTS = {
            {-6.5, 6, -8, 3, 0.5, 1},
            {-3.6, 3.2, -6.4, 0, 1.6, -1},
            {4.6, 2.6, -1.6, 9, 1, 6},
            {9.2, 1.75, 1.75, 9.5, 0.8, 3.6},
            {1.2, 4.2, -9.2, -1, 2, -3},
            {9.0, 4.0, -7.5, 4.8, 2.6, -3},
            {2.3, 2.1, -0.4, 5, 0.6, 3.6},
            {-7.2, 3.0, -7.4, -4, 1.4, -4},
            {16.5, 3.0, 3.5, 13, 0.4, -1.5},
            {3.5, 4.0, -11.5, 10, 3.6, -4},
    };
    private static final String[] SHOT_NAMES = {"uebersicht", "pferdekopfpumpe", "rechenzentrum", "ki_terminal", "raffinerie", "windrad", "chipfabrik_akku", "pumpe", "foerderband", "bohrturm"};

    private static int stage;
    private static int timer;
    private static int shot;
    private static long startMs;
    private static BlockPos origin = BlockPos.ZERO;
    private static final List<String> REPORT = new ArrayList<>();

    private ShowcaseAutomation() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (startMs == 0) {
            startMs = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - startMs > TIMEOUT_MS) {
            REPORT.add("TIMEOUT in stage " + stage);
            finish(mc);
            return;
        }
        timer++;
        switch (stage) {
            case 0 -> {
                if (mc.getOverlay() == null && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
                    checkModels(mc);
                    mc.options.onboardAccessibility = false;
                    mc.options.hideGui = true;
                    mc.options.renderDistance().set(6);
                    mc.createWorldOpenFlows().createFreshLevel("showcase",
                            new LevelSettings("showcase", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(1234L, false, false),
                            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                            mc.screen);
                    stage = 1;
                    timer = 0;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && timer > 40) {
                    MinecraftServer server = mc.getSingleplayerServer();
                    origin = mc.player.blockPosition().offset(2, 0, 4);
                    server.execute(() -> buildScene(server));
                    stage = 2;
                    timer = 0;
                }
            }
            case 2 -> {
                keepPowered(mc);
                if (timer == 1) {
                    teleport(mc, SHOTS[shot]);
                }
                if (timer == (shot == 0 ? 160 : 90)) {
                    String name = String.format("showcase_%d_%s.png", shot + 1, SHOT_NAMES[shot]);
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> REPORT.add("screenshot: " + msg.getString()));
                    shot++;
                    timer = 0;
                    if (shot >= SHOTS.length) {
                        stage = 3;
                        timer = 0;
                    }
                }
            }
            case 3 -> {
                keepPowered(mc);
                guiShots(mc);
            }
            case 4 -> {
                if (timer > 20) {
                    finish(mc);
                }
            }
            default -> {
            }
        }
    }

    private static int guiShot;

    /** Opens the rack and terminal screens and photographs them: rack view, server detail, power view, dashboard. */
    private static void guiShots(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        String[] names = {"gui_rack", "gui_server", "gui_energie", "gui_terminal"};
        if (server == null || guiShot >= names.length) {
            stage = 4;
            timer = 0;
            return;
        }
        BlockPos rack = origin.offset(8, 0, 6);
        BlockPos terminal = origin.offset(9, 0, 3);
        if (timer == 1) {
            switch (guiShot) {
                case 0 -> server.execute(() -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.crudecompute.menu.RackMenu(id, inv, rack),
                                net.minecraft.network.chat.Component.translatable("block.crudecompute.server_rack")), buf -> buf.writeBlockPos(rack));
                    }
                });
                case 1 -> {
                    if (mc.screen instanceof RackScreen screen) {
                        screen.showcaseSelect(0);
                    }
                }
                case 2 -> {
                    if (mc.screen instanceof RackScreen screen) {
                        screen.showcaseEnergy();
                    }
                }
                default -> server.execute(() -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.crudecompute.menu.TerminalMenu(id, inv, terminal),
                                net.minecraft.network.chat.Component.translatable("block.crudecompute.ai_terminal")), buf -> buf.writeBlockPos(terminal));
                    }
                });
            }
        }
        if (guiShot == 3 && timer == 5 && mc.screen instanceof RackScreen) {
            mc.setScreen(null);
        }
        if (timer == (guiShot == 0 || guiShot == 3 ? 70 : 45)) {
            String name = String.format("showcase_%d_%s.png", SHOTS.length + guiShot + 1, names[guiShot]);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> REPORT.add("screenshot: " + msg.getString()));
            guiShot++;
            timer = 0;
        }
    }

    private static void checkModels(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        int problems = 0;
        for (ModelResourceLocation location : ModModels.ALL) {
            if (mc.getModelManager().getModel(location) == missing) {
                REPORT.add("MISSING standalone model " + location);
                problems++;
            }
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(CrudeCompute.MODID)) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (mc.getBlockRenderer().getBlockModel(state) == missing) {
                    REPORT.add("MISSING block model " + state);
                    problems++;
                    break;
                }
            }
            ItemStack stack = new ItemStack(block);
            if (!stack.isEmpty() && mc.getItemRenderer().getModel(stack, null, null, 0) == missing) {
                REPORT.add("MISSING item model " + BuiltInRegistries.BLOCK.getKey(block));
                problems++;
            }
        }
        for (var item : ModItems.ITEMS.getEntries()) {
            ItemStack stack = new ItemStack(item.get());
            if (mc.getItemRenderer().getModel(stack, null, null, 0) == missing) {
                REPORT.add("MISSING item model " + item.getId());
                problems++;
            }
        }
        REPORT.add("MODEL CHECK: " + problems + " problem(s)");
        CrudeCompute.LOGGER.info("[showcase] model check finished with {} problem(s)", problems);
    }

    private static void teleport(Minecraft mc, double[] s) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        double cx = origin.getX() + s[0], cy = origin.getY() + s[1], cz = origin.getZ() + s[2];
        double tx = origin.getX() + s[3], ty = origin.getY() + s[4], tz = origin.getZ() + s[5];
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(cx, cy - player.getEyeHeight(), cz, yaw, pitch);
            }
        });
    }

    private static void feedBelt(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, origin.offset(13, 0, 0), Direction.UP);
        if (handler != null) {
            handler.insertItem(0, new ItemStack(ModItems.CIRCUIT_BOARD.get()), false);
        }
    }

    private static void keepPowered(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (timer % 25 == 0) {
            server.execute(() -> feedBelt(server));
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(6, 0, 2), origin.offset(12, 1, 8))) {
                IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, Direction.UP);
                if (energy != null && energy.canReceive()) {
                    energy.receiveEnergy(4096, false);
                }
            }
        });
    }

    // ------------------------------------------------------------------ scene
    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static BlockState facing(Block block, Direction dir) {
        return block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, dir);
    }

    private static void multiblock(ServerLevel level, BlockPos pos, BlockState state, Block block) {
        set(level, pos, state);
        block.setPlacedBy(level, pos, level.getBlockState(pos), null, new ItemStack(block));
    }

    private static void buildScene(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.setDayTime(6000);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(12000, 0, false, false);
        BlockPos o = origin;

        // concrete floor so the scene reads well
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-8, -1, -8), o.offset(14, -1, 10))) {
            set(level, p, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        }
        // oil shale under the wellhead
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-1, -3, -3), o.offset(1, -1, -1))) {
            set(level, p, ModBlocks.OIL_SHALE.get().defaultBlockState());
        }
        // pumpjack driven by a diesel engine
        multiblock(level, o, facing(ModBlocks.PUMPJACK.get(), Direction.NORTH), ModBlocks.PUMPJACK.get());
        set(level, o.offset(1, 0, 0), ModBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        set(level, o.offset(2, 0, 0), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.WEST));
        fuel(level, o.offset(2, 0, 0));
        // crude pipeline to the refinery, diesel back into a tank
        set(level, o.offset(-1, 0, -2), ModBlocks.FLUID_PIPE.get().defaultBlockState());
        set(level, o.offset(-2, 0, -2), ModBlocks.FLUID_PIPE.get().defaultBlockState());
        multiblock(level, o.offset(-3, 0, -2), facing(ModBlocks.REFINERY.get(), Direction.EAST), ModBlocks.REFINERY.get());
        set(level, o.offset(-3, 0, -1), ModBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        set(level, o.offset(-3, 0, 0), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(level, o.offset(-3, 0, 0));
        set(level, o.offset(-3, 1, -3), ModBlocks.FLUID_PIPE.get().defaultBlockState());
        set(level, o.offset(-3, 0, -3), ModBlocks.FLUID_PIPE.get().defaultBlockState());
        set(level, o.offset(-3, 0, -4), ModBlocks.OIL_TANK.get().defaultBlockState());
        set(level, o.offset(-5, 0, -2), ModBlocks.OIL_TANK.get().defaultBlockState());
        if (level.getBlockEntity(o.offset(-3, 0, -2)) instanceof RefineryBlockEntity refinery) {
            refinery.getCombinedHandler().fill(new FluidStack(ModFluids.CRUDE_OIL.get(), 8000), IFluidHandler.FluidAction.EXECUTE);
        }
        // a crude oil pool and a hand crank on a gearbox
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-7, -1, 3), o.offset(-5, -1, 5))) {
            set(level, p, ModFluids.CRUDE_OIL.get().defaultFluidState().createLegacyBlock());
        }
        set(level, o.offset(-3, 0, 3), ModBlocks.GEARBOX.get().defaultBlockState());
        set(level, o.offset(-3, 1, 3), ModBlocks.HAND_CRANK.get().defaultBlockState());
        set(level, o.offset(-2, 0, 3), ModBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        // wind turbine on a proper tower: foundation, tube segments, generator at the foot, plugged cables to a battery
        set(level, o.offset(4, 0, -3), ModBlocks.TOWER_BASE.get().defaultBlockState());
        for (int y = 1; y < 5; y++) {
            set(level, o.offset(4, y, -3), ModBlocks.WIND_TOWER.get().defaultBlockState());
        }
        set(level, o.offset(4, 5, -3), facing(ModBlocks.WINDMILL.get(), Direction.NORTH));
        set(level, o.offset(5, 0, -3), facing(ModBlocks.GENERATOR.get(), Direction.EAST));
        set(level, o.offset(6, 0, -3), facing(ModBlocks.BATTERY.get(), Direction.NORTH));
        for (int x = 5; x <= 6; x++) {
            BlockPos cable = o.offset(x, 1, -3);
            set(level, cable, ModBlocks.POWER_CABLE.get().connectionState(ModBlocks.POWER_CABLE.get().defaultBlockState(), level, cable));
        }
        // datacenter: engine -> generators -> cables -> racks + cooling, terminal in front
        set(level, o.offset(6, 0, 2), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.SOUTH));
        fuel(level, o.offset(6, 0, 2));
        set(level, o.offset(6, 0, 3), facing(ModBlocks.GENERATOR.get(), Direction.SOUTH));
        set(level, o.offset(6, 0, 4), facing(ModBlocks.GENERATOR.get(), Direction.SOUTH));
        for (int x = 7; x <= 11; x++) {
            set(level, o.offset(x, 0, 5), ModBlocks.POWER_CABLE.get().defaultBlockState());
        }
        set(level, o.offset(7, 0, 4), ModBlocks.POWER_CABLE.get().defaultBlockState());
        int[][] fill = {{4, 2, 1, 1}, {2, 2, 2}, {4, 1, 1, 1, 2}};
        for (int i = 0; i < 3; i++) {
            BlockPos rack = o.offset(8 + i, 0, 6);
            multiblock(level, rack, facing(ModBlocks.SERVER_RACK.get(), Direction.NORTH), ModBlocks.SERVER_RACK.get());
            if (level.getBlockEntity(rack) instanceof ServerRackBlockEntity be) {
                for (int size : fill[i]) {
                    be.insert(com.crudecompute.hardware.ServerConfig.preset(size), -1);
                }
            }
        }
        // solar field with inverter feeding the battery bank
        set(level, o.offset(8, 0, 9), facing(ModBlocks.INVERTER.get(), Direction.NORTH));
        for (int dx = 0; dx < 4; dx++) {
            set(level, o.offset(8 + dx, 0, 10), ModBlocks.SOLAR_PANEL_MONO.get().defaultBlockState());
            set(level, o.offset(8 + dx, 0, 11), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        }
        set(level, o.offset(9, 0, 9), ModBlocks.SOLAR_PANEL_MONO.get().defaultBlockState());
        set(level, o.offset(7, 0, 6), ModBlocks.COOLING_UNIT.get().defaultBlockState());
        set(level, o.offset(11, 0, 6), ModBlocks.COOLING_UNIT.get().defaultBlockState());
        set(level, o.offset(9, 0, 7), ModBlocks.COOLING_UNIT.get().defaultBlockState());
        set(level, o.offset(9, 0, 3), facing(ModBlocks.AI_TERMINAL.get(), Direction.NORTH));
        if (level.getBlockEntity(o.offset(9, 0, 3)) instanceof AITerminalBlockEntity terminal) {
            terminal.setDrive(new ItemStack(ModItems.EMPTY_MODEL_DRIVE.get()));
        }
        // update 1.1: wind-driven pump between two tanks, lithography machine, battery bank
        set(level, o.offset(-4, 0, -4), ModBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(com.crudecompute.oil.MechanicalPumpBlock.FACING, Direction.WEST));
        set(level, o.offset(-5, 0, -4), ModBlocks.OIL_TANK.get().defaultBlockState());
        for (int y = 1; y < 3; y++) {
            set(level, o.offset(-4, y, -4), ModBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Y));
        }
        set(level, o.offset(-4, 3, -4), facing(ModBlocks.WINDMILL.get(), Direction.NORTH));
        if (level.getBlockEntity(o.offset(-3, 0, -4)) instanceof com.crudecompute.oil.OilTankBlockEntity tank) {
            tank.getTank().fill(new FluidStack(ModFluids.DIESEL.get(), 12000), IFluidHandler.FluidAction.EXECUTE);
        }
        set(level, o.offset(4, 0, 2), facing(ModBlocks.CHIP_FAB.get(), Direction.NORTH));
        set(level, o.offset(4, 0, 3), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(level, o.offset(4, 0, 3));
        if (level.getBlockEntity(o.offset(4, 0, 2)) instanceof com.crudecompute.datacenter.ChipFabBlockEntity fab) {
            fab.insert(new ItemStack(ModItems.SILICON_WAFER.get(), 32));
            fab.insert(new ItemStack(ModItems.PLASTIC.get(), 32));
            fab.insert(new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 32));
        }
        set(level, o.offset(6, 0, 5), facing(ModBlocks.BATTERY.get(), Direction.WEST));
        // update 1.2: oil derrick on its own shale field, conveyor belt line
        for (BlockPos p : BlockPos.betweenClosed(o.offset(9, -1, -5), o.offset(11, -1, -3))) {
            set(level, p, ModBlocks.OIL_SHALE.get().defaultBlockState());
        }
        multiblock(level, o.offset(10, 0, -3), facing(ModBlocks.OIL_DERRICK.get(), Direction.NORTH), ModBlocks.OIL_DERRICK.get());
        set(level, o.offset(10, 0, -2), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(level, o.offset(10, 0, -2));
        set(level, o.offset(12, 0, 1), facing(ModBlocks.DIESEL_ENGINE.get(), Direction.NORTH));
        fuel(level, o.offset(12, 0, 1));
        for (int z = 0; z >= -3; z--) {
            set(level, o.offset(12, 0, z), ModBlocks.GEARBOX.get().defaultBlockState());
            set(level, o.offset(13, 0, z), facing(ModBlocks.CONVEYOR_BELT.get(), Direction.NORTH));
        }
        // every placed cable re-evaluates its sides now that all neighbours exist (arms and plugs)
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-8, 0, -8), o.offset(14, 3, 10))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof com.crudecompute.datacenter.PowerCableBlock cable) {
                level.setBlock(p, cable.connectionState(state, level, p), Block.UPDATE_ALL);
            }
        }
        REPORT.add("scene built at " + o.toShortString());
    }

    private static void fuel(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DieselEngineBlockEntity engine) {
            engine.getFuelHandler().fill(new FluidStack(ModFluids.DIESEL.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        try {
            Path report = mc.gameDirectory.toPath().resolve("showcase-report.txt");
            Files.write(report, REPORT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CrudeCompute.LOGGER.error("[showcase] could not write report", e);
        }
        for (String line : REPORT) {
            CrudeCompute.LOGGER.info("[showcase] {}", line);
        }
        mc.stop();
    }
}
