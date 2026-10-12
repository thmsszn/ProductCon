package de.localai.baumeister;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baumeister-Axt: Linksklick auf einen Block = Ecke 1, Rechtsklick auf einen Block = Ecke 2,
 * Rechtsklick in die Luft = Baumeister-Menü öffnen. Baut nie Blöcke ab.
 */
public final class BuilderAxeItem extends Item {
    public BuilderAxeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) BaumeisterMod.cornerHandler.accept(false, ctx.getClickedPos().immutable());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) BaumeisterMod.menuHandler.run();
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
        TooltipFlag flag) {
        lines.accept(Component.translatable("item.ki_baumeister.baumeister_axt.tip1").withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("item.ki_baumeister.baumeister_axt.tip2").withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("item.ki_baumeister.baumeister_axt.tip3").withStyle(ChatFormatting.GRAY));
    }
}
