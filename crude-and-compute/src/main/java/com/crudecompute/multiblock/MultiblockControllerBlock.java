package com.crudecompute.multiblock;

import com.crudecompute.registry.ModBlocks;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The "brain" block of a multi-block machine. It owns the block entity and renders the whole free-form model;
 * the other cells are filled with invisible {@link MultiblockPartBlock}s that forward interactions and shapes.
 * <p>
 * Part offsets are given in local coordinates: {@code right}, {@code up}, {@code forward} where forward is the
 * facing direction (the models are authored facing north, i.e. forward = -Z, right = +X).
 */
public abstract class MultiblockControllerBlock extends HorizontalMachineBlock {
    protected MultiblockControllerBlock(Properties properties) {
        super(properties);
    }

    /** Local (right, up, forward) offsets of all part cells, excluding the controller at (0, 0, 0). */
    public abstract List<int[]> localParts();

    /** Collision shape of a cell in local coordinates (authored facing north). */
    public abstract VoxelShape localShape(int right, int up, int forward);

    /** Machines extend in the direction the player looks. */
    @Override
    protected Direction facingForPlacement(BlockPlaceContext context) {
        return context.getHorizontalDirection();
    }

    public static BlockPos toWorld(BlockPos origin, Direction facing, int right, int up, int forward) {
        Direction r = facing.getClockWise();
        return origin.offset(
                facing.getStepX() * forward + r.getStepX() * right,
                up,
                facing.getStepZ() * forward + r.getStepZ() * right);
    }

    /** Converts a world offset (part - controller) back into local (right, up, forward). */
    public static int[] toLocal(Direction facing, int dx, int dy, int dz) {
        Direction r = facing.getClockWise();
        int forward = dx * facing.getStepX() + dz * facing.getStepZ();
        int right = dx * r.getStepX() + dz * r.getStepZ();
        return new int[]{right, dy, forward};
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = facingForPlacement(context);
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        for (int[] part : localParts()) {
            BlockPos p = toWorld(origin, facing, part[0], part[1], part[2]);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                if (context.getPlayer() != null && !level.isClientSide) {
                    Lang.warn(context.getPlayer(), Lang.msg("no_space"));
                }
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int[] part : localParts()) {
            BlockPos p = toWorld(pos, facing, part[0], part[1], part[2]);
            level.setBlock(p, MultiblockPartBlock.stateFor(ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(), p.subtract(pos)), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            beforeRemoved(state, level, pos);
            Direction facing = state.getValue(FACING);
            for (int[] part : localParts()) {
                BlockPos p = toWorld(pos, facing, part[0], part[1], part[2]);
                BlockState partState = level.getBlockState(p);
                if (partState.getBlock() instanceof MultiblockPartBlock && MultiblockPartBlock.getMainPos(p, partState).equals(pos)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Hook to drop inventory contents before the structure disappears. */
    protected void beforeRemoved(BlockState state, Level level, BlockPos pos) {
    }

    /** Shape of a cell given as world offset from the controller. */
    public VoxelShape getCellShape(BlockState mainState, int dx, int dy, int dz) {
        Direction facing = mainState.getValue(FACING);
        int[] local = toLocal(facing, dx, dy, dz);
        VoxelShape shape = localShape(local[0], local[1], local[2]);
        return com.crudecompute.util.ShapeUtil.rotate(shape, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getCellShape(state, 0, 0, 0);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state; // the parts would not follow
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    // ------------------------------------------------------------------ forwarded from part blocks
    public ItemInteractionResult useItemOnMain(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
        return useItemOn(stack, state, level, pos, player, hand, hit);
    }

    public InteractionResult useWithoutItemOnMain(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    protected static VoxelShape full() {
        return Shapes.block();
    }
}
