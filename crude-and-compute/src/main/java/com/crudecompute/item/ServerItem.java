package com.crudecompute.item;

import com.crudecompute.hardware.Kind;
import com.crudecompute.hardware.Part;
import com.crudecompute.hardware.ServerConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** A rack server (1U / 2U / 4U). Its hardware configuration travels with the item. */
public class ServerItem extends Item {
    private final int sizeU;

    public ServerItem(Properties properties, int sizeU) {
        super(properties);
        this.sizeU = sizeU;
    }

    public int getSizeU() {
        return sizeU;
    }

    public static ItemStack make(Item item, ServerConfig cfg) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(cfg.save()));
        return stack;
    }

    public ServerConfig configOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty() || !data.contains("U")) {
            return new ServerConfig(sizeU);
        }
        ServerConfig cfg = ServerConfig.load(data.copyTag());
        cfg.sizeU = sizeU;
        return cfg;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ServerConfig cfg = configOf(stack);
        tooltip.add(Component.translatable("hw.item.size", sizeU).withStyle(ChatFormatting.GRAY));
        if (cfg.isEmpty()) {
            tooltip.add(Component.translatable("hw.item.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Part mb = cfg.moboPart();
        if (mb != null) {
            tooltip.add(Component.literal(mb.name).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal(cfg.cpus.size() + "x CPU | " + cfg.gpus.size() + "x GPU | " + cfg.ramGB() + " GB RAM | "
                + cfg.storageGB() / 1000 + " TB").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(cfg.count(Kind.PSU) + "x PSU " + cfg.psuRatedW() + " W | $" + String.format("%,d", cfg.totalPrice()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
