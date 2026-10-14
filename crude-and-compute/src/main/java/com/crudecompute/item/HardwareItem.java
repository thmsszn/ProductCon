package com.crudecompute.item;

import com.crudecompute.hardware.Part;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** One concrete hardware component (a CPU, a RAM module, a mainboard, ...). Installed through the rack screen. */
public class HardwareItem extends Item {
    private final Part part;

    public HardwareItem(Properties properties, Part part) {
        super(properties);
        this.part = part;
    }

    public Part getPart() {
        return part;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(part.name);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(part.kind.key()).withStyle(ChatFormatting.AQUA));
        List<String> lines = part.specLines();
        for (int i = 0; i < lines.size() - 1; i++) {
            tooltip.add(Component.literal(lines.get(i)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("hw.item.install_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("$" + String.format(Locale.ROOT, "%,d", part.price)).withStyle(ChatFormatting.DARK_GREEN));
    }
}
