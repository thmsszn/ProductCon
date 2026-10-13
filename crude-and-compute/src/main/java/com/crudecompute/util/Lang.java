package com.crudecompute.util;

import com.crudecompute.CrudeCompute;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;

/** Translation and formatting helpers. */
public final class Lang {
    private Lang() {
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message." + CrudeCompute.MODID + "." + key, args);
    }

    public static String num(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.format(Locale.ROOT, "%d", Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static Component fluid(FluidStack stack) {
        return stack.isEmpty() ? Component.literal("-") : stack.getHoverName();
    }

    /** Status line in the action bar above the hotbar. */
    public static void status(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
    }

    public static void warn(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }
}
