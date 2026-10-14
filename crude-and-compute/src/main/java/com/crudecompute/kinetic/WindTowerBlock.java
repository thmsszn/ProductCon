package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.MachineBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One segment of a wind turbine tower: a steel tube with an axle running through its core.
 * Stack segments to lift a {@link WindmillBlock} into stronger wind; the rotation is carried down to the tower base.
 */
public class WindTowerBlock extends RotatedPillarBlock implements EntityBlock {
    public static final MapCodec<WindTowerBlock> CODEC = simpleCodec(WindTowerBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public WindTowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<WindTowerBlock> codec() {
        return CODEC;
    }

    /** Towers always stand upright. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return KineticBlocks.showStatus(level, pos, player, getName());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return TransmissionBlockEntity.tower(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return MachineBlock.createTicker(level, type, ModBlockEntities.WIND_TOWER.get());
    }
}
