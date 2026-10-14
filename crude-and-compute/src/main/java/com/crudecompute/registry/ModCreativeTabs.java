package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrudeCompute.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crudecompute"))
            .icon(() -> new ItemStack(ModItems.PUMPJACK.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.WRENCH.get());
                output.accept(ModItems.SHAFT.get());
                output.accept(ModItems.GEARBOX.get());
                output.accept(ModItems.HAND_CRANK.get());
                output.accept(ModItems.WINDMILL.get());
                output.accept(ModItems.DIESEL_ENGINE.get());
                output.accept(ModItems.GENERATOR.get());
                output.accept(ModItems.OIL_SHALE.get());
                output.accept(ModItems.PUMPJACK.get());
                output.accept(ModItems.OIL_DERRICK.get());
                output.accept(ModItems.CONVEYOR_BELT.get());
                output.accept(ModItems.REFINERY.get());
                output.accept(ModItems.FLUID_PIPE.get());
                output.accept(ModItems.OIL_TANK.get());
                output.accept(ModItems.MECHANICAL_PUMP.get());
                output.accept(ModItems.CRUDE_OIL_BUCKET.get());
                output.accept(ModItems.DIESEL_BUCKET.get());
                output.accept(ModItems.PLASTIC.get());
                output.accept(ModItems.POWER_CABLE.get());
                output.accept(ModItems.BATTERY.get());
                output.accept(ModItems.SILICON.get());
                output.accept(ModItems.SILICON_WAFER.get());
                output.accept(ModItems.CIRCUIT_BOARD.get());
                output.accept(ModItems.CHIP_FAB.get());
                output.accept(ModItems.GPU.get());
                output.accept(ModItems.SERVER_RACK.get());
                output.accept(ModItems.COOLING_UNIT.get());
                output.accept(ModItems.AI_TERMINAL.get());
                output.accept(ModItems.EMPTY_MODEL_DRIVE.get());
                output.accept(ModItems.TRAINED_MODEL_DRIVE.get());
                output.accept(ModItems.AI_OVERCLOCK_CHIP.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
