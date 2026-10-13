package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.ShapeUtil;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Wind turbine: sits on top of a vertical shaft and drives it. */
public class WindmillBlock extends HorizontalMachineBlock {
    public static final MapCodec<WindmillBlock> CODEC = simpleCodec(WindmillBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontalMap(Shapes.or(
            Block.box(5, 0, 5, 11, 10, 11),
            Block.box(5, 9, 4, 11, 15, 16)));

    public WindmillBlock(Properties properties) {
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
        return ModBlockEntities.WINDMILL.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WindmillBlockEntity windmill) {
            windmill.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
