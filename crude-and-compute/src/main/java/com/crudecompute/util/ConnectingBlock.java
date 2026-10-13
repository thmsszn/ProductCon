package com.crudecompute.util;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pipes and cables: connect to their own kind and to anything offering the right capability. */
public abstract class ConnectingBlock extends MachineBlock {
    public static final Map<Direction, BooleanProperty> PROPS = PipeBlock.PROPERTY_BY_DIRECTION;
    private final VoxelShape[] shapes = new VoxelShape[64];

    protected ConnectingBlock(Properties properties, double coreRadiusPx, double armRadiusPx) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty prop : PROPS.values()) {
            state = state.setValue(prop, false);
        }
        registerDefaultState(state);
        double c0 = 8 - coreRadiusPx, c1 = 8 + coreRadiusPx, a0 = 8 - armRadiusPx, a1 = 8 + armRadiusPx;
        VoxelShape core = Block.box(c0, c0, c0, c1, c1, c1);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) == 0) {
                    continue;
                }
                VoxelShape arm = switch (dir) {
                    case DOWN -> Block.box(a0, 0, a0, a1, c0, a1);
                    case UP -> Block.box(a0, c1, a0, a1, 16, a1);
                    case NORTH -> Block.box(a0, a0, 0, a1, a1, c0);
                    case SOUTH -> Block.box(a0, a0, c1, a1, a1, 16);
                    case WEST -> Block.box(0, a0, a0, c0, a1, a1);
                    case EAST -> Block.box(c1, a0, a0, 16, a1, a1);
                };
                shape = Shapes.or(shape, arm);
            }
            shapes[mask] = shape.optimize();
        }
    }

    /** Whether this block should connect towards {@code dir}. */
    protected abstract boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPS.values().forEach(builder::add);
    }

    public BlockState connectionState(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPS.get(dir), canConnect(level, pos, dir));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connectionState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPS.get(dir), canConnect(level, pos, dir));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        BlockState updated = connectionState(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(PROPS.get(dir))) {
                mask |= 1 << dir.ordinal();
            }
        }
        return shapes[mask];
    }

    public static boolean isConnected(BlockState state, Direction dir) {
        return state.hasProperty(PROPS.get(dir)) && state.getValue(PROPS.get(dir));
    }
}
