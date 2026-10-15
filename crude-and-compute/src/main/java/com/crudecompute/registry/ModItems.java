package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import com.crudecompute.item.OverclockChipItem;
import com.crudecompute.item.TooltipBlockItem;
import com.crudecompute.item.TooltipItem;
import com.crudecompute.item.WrenchItem;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrudeCompute.MODID);

    // ------------------------------------------------------------ block items
    public static final DeferredItem<BlockItem> OIL_SHALE = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_SHALE);
    public static final DeferredItem<BlockItem> SHAFT = blockItem("shaft", ModBlocks.SHAFT);
    public static final DeferredItem<BlockItem> GEARBOX = blockItem("gearbox", ModBlocks.GEARBOX);
    public static final DeferredItem<BlockItem> HAND_CRANK = blockItem("hand_crank", ModBlocks.HAND_CRANK);
    public static final DeferredItem<BlockItem> WINDMILL = blockItem("windmill", ModBlocks.WINDMILL);
    public static final DeferredItem<BlockItem> WIND_TOWER = blockItem("wind_tower", ModBlocks.WIND_TOWER);
    public static final DeferredItem<BlockItem> TOWER_BASE = blockItem("tower_base", ModBlocks.TOWER_BASE);
    public static final DeferredItem<BlockItem> DIESEL_ENGINE = blockItem("diesel_engine", ModBlocks.DIESEL_ENGINE);
    public static final DeferredItem<BlockItem> GENERATOR = blockItem("generator", ModBlocks.GENERATOR);
    public static final DeferredItem<BlockItem> PUMPJACK = blockItem("pumpjack", ModBlocks.PUMPJACK);
    public static final DeferredItem<BlockItem> REFINERY = blockItem("refinery", ModBlocks.REFINERY);
    public static final DeferredItem<BlockItem> FLUID_PIPE = blockItem("fluid_pipe", ModBlocks.FLUID_PIPE);
    public static final DeferredItem<BlockItem> OIL_TANK = blockItem("oil_tank", ModBlocks.OIL_TANK);
    public static final DeferredItem<BlockItem> POWER_CABLE = blockItem("power_cable", ModBlocks.POWER_CABLE);
    public static final DeferredItem<BlockItem> SERVER_RACK = blockItem("server_rack", ModBlocks.SERVER_RACK);
    public static final DeferredItem<BlockItem> COOLING_UNIT = blockItem("cooling_unit", ModBlocks.COOLING_UNIT);
    public static final DeferredItem<BlockItem> AI_TERMINAL = blockItem("ai_terminal", ModBlocks.AI_TERMINAL);
    public static final DeferredItem<BlockItem> MECHANICAL_PUMP = blockItem("mechanical_pump", ModBlocks.MECHANICAL_PUMP);
    public static final DeferredItem<BlockItem> CONVEYOR_BELT = blockItem("conveyor_belt", ModBlocks.CONVEYOR_BELT);
    public static final DeferredItem<BlockItem> OIL_DERRICK = blockItem("oil_derrick", ModBlocks.OIL_DERRICK);
    public static final DeferredItem<BlockItem> SOLAR_PANEL = blockItem("solar_panel", ModBlocks.SOLAR_PANEL);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_MONO = blockItem("solar_panel_mono", ModBlocks.SOLAR_PANEL_MONO);
    public static final DeferredItem<BlockItem> INVERTER = blockItem("inverter", ModBlocks.INVERTER);
    public static final DeferredItem<BlockItem> BATTERY = blockItem("battery", ModBlocks.BATTERY);
    public static final DeferredItem<BlockItem> CHIP_FAB = blockItem("chip_fab", ModBlocks.CHIP_FAB);

    // ------------------------------------------------------------ items
    public static final DeferredItem<Item> CRUDE_OIL_BUCKET = ITEMS.register("crude_oil_bucket",
            () -> new BucketItem(ModFluids.CRUDE_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<Item> DIESEL_BUCKET = ITEMS.register("diesel_bucket",
            () -> new BucketItem(ModFluids.DIESEL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PLASTIC = ITEMS.registerSimpleItem("plastic");
    public static final DeferredItem<Item> SILICON = ITEMS.registerSimpleItem("silicon");
    public static final DeferredItem<Item> SILICON_WAFER = ITEMS.registerSimpleItem("silicon_wafer");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> GPU = ITEMS.register("gpu",
            () -> new TooltipItem(new Item.Properties().rarity(Rarity.UNCOMMON), "tooltip.crudecompute.gpu"));
    public static final DeferredItem<Item> EMPTY_MODEL_DRIVE = ITEMS.register("empty_model_drive",
            () -> new TooltipItem(new Item.Properties().stacksTo(16), "tooltip.crudecompute.empty_model_drive"));
    public static final DeferredItem<Item> TRAINED_MODEL_DRIVE = ITEMS.register("trained_model_drive",
            () -> new TooltipItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "tooltip.crudecompute.trained_model_drive"));
    public static final DeferredItem<Item> AI_OVERCLOCK_CHIP = ITEMS.register("ai_overclock_chip",
            () -> new OverclockChipItem(new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> RACK_FAN = ITEMS.register("rack_fan",
            () -> new TooltipItem(new Item.Properties().stacksTo(16), "tooltip.crudecompute.rack_fan"));
    public static final DeferredItem<Item> RACK_FAN_HP = ITEMS.register("rack_fan_hp",
            () -> new TooltipItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "tooltip.crudecompute.rack_fan_hp"));

    public static final DeferredItem<Item> SERVER_1U = ITEMS.register("server_1u", () -> new com.crudecompute.item.ServerItem(new Item.Properties().stacksTo(1), 1));
    public static final DeferredItem<Item> SERVER_2U = ITEMS.register("server_2u", () -> new com.crudecompute.item.ServerItem(new Item.Properties().stacksTo(1), 2));
    public static final DeferredItem<Item> SERVER_4U = ITEMS.register("server_4u", () -> new com.crudecompute.item.ServerItem(new Item.Properties().stacksTo(1), 4));

    public static Item serverItem(int sizeU) {
        return sizeU == 1 ? SERVER_1U.get() : sizeU == 2 ? SERVER_2U.get() : SERVER_4U.get();
    }

    /** One item per catalog part: id = "hw_" + part id. */
    public static final java.util.Map<String, DeferredItem<Item>> PARTS = new java.util.LinkedHashMap<>();

    static {
        for (com.crudecompute.hardware.Part part : com.crudecompute.hardware.Catalog.ALL) {
            PARTS.put(part.id, ITEMS.register("hw_" + part.id,
                    () -> new com.crudecompute.item.HardwareItem(new Item.Properties().stacksTo(16), part)));
        }
    }

    public static Item partItem(com.crudecompute.hardware.Part part) {
        return PARTS.get(part.id).get();
    }

    private ModItems() {
    }

    private static DeferredItem<BlockItem> blockItem(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, () -> new TooltipBlockItem(block.get(), new Item.Properties(), "tooltip.crudecompute." + name));
    }
}
