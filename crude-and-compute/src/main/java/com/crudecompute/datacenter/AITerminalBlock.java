package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.ShapeUtil;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Desk console with a curved monitor: monitors the datacenter and trains AI models on model drives. */
public class AITerminalBlock extends HorizontalMachineBlock {
    public static final MapCodec<AITerminalBlock> CODEC = simpleCodec(AITerminalBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontalMap(Shapes.or(
            Block.box(1, 0, 1, 15, 8, 15),
            Block.box(1, 8, 8, 15, 16, 11)));

    public AITerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasActiveProperty() {
        return false;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.AI_TERMINAL.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.EMPTY_MODEL_DRIVE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AITerminalBlockEntity terminal && terminal.getDrive().isEmpty()) {
            terminal.setDrive(stack.copyWithCount(1));
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && !player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AITerminalBlockEntity) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.crudecompute.menu.TerminalMenu(id, inv, pos),
                    net.minecraft.network.chat.Component.translatable("block.crudecompute.ai_terminal")), buf -> buf.writeBlockPos(pos));
        } else if (!level.isClientSide && level.getBlockEntity(pos) instanceof AITerminalBlockEntity terminal) {
            ItemStack drive = terminal.getDrive();
            if (!drive.isEmpty() && (player.isShiftKeyDown() || drive.is(ModItems.TRAINED_MODEL_DRIVE.get()))) {
                terminal.setDrive(ItemStack.EMPTY);
                ItemHandlerHelper.giveItemToPlayer(player, drive);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
            } else {
                terminal.showStatus(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AITerminalBlockEntity terminal) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, terminal.getDrive());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
