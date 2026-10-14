package com.crudecompute.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible filler cell of a multi-block machine. Its state stores the offset from the controller
 * (ox/oz are shifted by +2, oy is the height above the controller).
 */
public class MultiblockPartBlock extends Block {
    public static final MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);
    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 4);
    public static final IntegerProperty OY = IntegerProperty.create("oy", 0, 6);
    public static final IntegerProperty OZ = IntegerProperty.create("oz", 0, 4);

    public MultiblockPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OX, 2).setValue(OY, 1).setValue(OZ, 2));
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OX, OY, OZ);
    }

    public static BlockState stateFor(BlockState base, Vec3i offset) {
        return base.setValue(OX, offset.getX() + 2).setValue(OY, offset.getY()).setValue(OZ, offset.getZ() + 2);
    }

    public static Vec3i getOffset(BlockState state) {
        return new Vec3i(state.getValue(OX) - 2, state.getValue(OY), state.getValue(OZ) - 2);
    }

    public static BlockPos getMainPos(BlockPos partPos, BlockState state) {
        return partPos.subtract(getOffset(state));
    }

    @Nullable
    public static BlockEntity getMainBlockEntity(BlockGetter level, BlockPos partPos, BlockState state) {
        return level.getBlockEntity(getMainPos(partPos, state));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos main = getMainPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (mainState.getBlock() instanceof MultiblockControllerBlock controller) {
            Vec3i o = getOffset(state);
            return controller.getCellShape(mainState, o.getX(), o.getY(), o.getZ());
        }
        return Shapes.block();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos main = getMainPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (mainState.getBlock() instanceof MultiblockControllerBlock controller) {
            return controller.useItemOnMain(stack, mainState, level, main, player, hand, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos main = getMainPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (mainState.getBlock() instanceof MultiblockControllerBlock controller) {
            return controller.useWithoutItemOnMain(mainState, level, main, player, hit);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = getMainPos(pos, state);
        if (!level.isClientSide && player.isCreative() && level.getBlockState(main).getBlock() instanceof MultiblockControllerBlock) {
            // creative: remove the whole machine without dropping it
            level.removeBlock(main, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos main = getMainPos(pos, state);
            if (level.getBlockState(main).getBlock() instanceof MultiblockControllerBlock) {
                level.destroyBlock(main, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockState mainState = level.getBlockState(getMainPos(pos, state));
        if (mainState.getBlock() instanceof MultiblockControllerBlock controller) {
            return new ItemStack(controller);
        }
        return ItemStack.EMPTY;
    }
}
