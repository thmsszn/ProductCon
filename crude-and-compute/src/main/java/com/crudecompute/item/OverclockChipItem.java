package com.crudecompute.item;

import com.crudecompute.multiblock.MultiblockPartBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.Optimizable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Installs an AI optimization into a machine: doubles its output. */
public class OverclockChipItem extends TooltipItem {
    public OverclockChipItem(Properties properties) {
        super(properties, "tooltip.crudecompute.ai_overclock_chip");
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.getBlock() instanceof MultiblockPartBlock
                ? MultiblockPartBlock.getMainBlockEntity(level, pos, state)
                : level.getBlockEntity(pos);
        if (!(be instanceof Optimizable optimizable)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Player player = context.getPlayer();
            if (optimizable.isOptimized()) {
                if (player != null) {
                    Lang.warn(player, Lang.msg("chip.already"));
                }
            } else {
                optimizable.setOptimized();
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
                if (player != null) {
                    Lang.status(player, Lang.msg("chip.installed"));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
