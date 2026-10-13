package com.crudecompute.item;

import com.crudecompute.CrudeCompute;
import com.crudecompute.kinetic.KineticNetworkManager;
import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Create-style wrench: right-click rotates our machines, sneak + right-click picks them up. */
public class WrenchItem extends TooltipItem {
    public WrenchItem(Properties properties) {
        super(properties, "tooltip.crudecompute.wrench");
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!isOurs(state) || player == null) {
            return InteractionResult.PASS;
        }
        if (state.getBlock() instanceof MultiblockPartBlock) {
            pos = MultiblockPartBlock.getMainPos(pos, state);
            state = level.getBlockState(pos);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            pickUp((ServerLevel) level, pos, state, player);
        } else if (!(state.getBlock() instanceof MultiblockControllerBlock)) {
            BlockState rotated = rotate(state, context.getClickedFace());
            if (rotated != state) {
                level.setBlock(pos, rotated, Block.UPDATE_ALL);
                KineticNetworkManager.markDirty(level, pos);
                for (Direction dir : Direction.values()) {
                    KineticNetworkManager.markDirty(level, pos.relative(dir));
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isOurs(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(CrudeCompute.MODID)
                && !(state.getBlock() instanceof LiquidBlock);
    }

    private static BlockState rotate(BlockState state, Direction clickedFace) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction current = state.getValue(BlockStateProperties.FACING);
            Direction next = Direction.values()[(current.ordinal() + 1) % Direction.values().length];
            return state.setValue(BlockStateProperties.FACING, next);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            Direction.Axis next = Direction.Axis.values()[(axis.ordinal() + 1) % 3];
            return state.setValue(BlockStateProperties.AXIS, next);
        }
        return state;
    }

    private static void pickUp(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (state.is(com.crudecompute.registry.ModBlocks.OIL_SHALE.get())) {
            return;
        }
        if (!player.isCreative()) {
            for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem())) {
                ItemHandlerHelper.giveItemToPlayer(player, drop);
            }
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.0f);
    }
}
