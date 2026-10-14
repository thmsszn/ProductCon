package com.crudecompute.registry;

import com.crudecompute.CrudeCompute;
import com.crudecompute.menu.RackMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CrudeCompute.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RackMenu>> RACK = MENUS.register("rack", () -> IMenuTypeExtension.create(RackMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.crudecompute.menu.TerminalMenu>> TERMINAL =
            MENUS.register("terminal", () -> IMenuTypeExtension.create(com.crudecompute.menu.TerminalMenu::new));

    private ModMenus() {
    }
}
