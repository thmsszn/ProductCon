package com.crudecompute.oil;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.ConnectingBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Copper pipe that equalises fluid with neighbouring pipes and feeds it into tanks and machines. */
public class FluidPipeBlock extends ConnectingBlock {
    public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);

    public FluidPipeBlock(Properties properties) {
        super(properties, 3.5, 2.5);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.FLUID_PIPE.get();
    }

    @Override
    protected boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (level.getBlockState(other).getBlock() instanceof FluidPipeBlock) {
            return true;
        }
        return level instanceof Level real && real.isLoaded(other)
                && real.getCapability(Capabilities.FluidHandler.BLOCK, other, dir.getOpposite()) != null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            pipe.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
