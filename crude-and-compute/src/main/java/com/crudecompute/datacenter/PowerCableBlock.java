package com.crudecompute.datacenter;

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

/** Rubber insulated cable carrying Forge Energy. */
public class PowerCableBlock extends ConnectingBlock {
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

    public PowerCableBlock(Properties properties) {
        super(properties, 2, 1.5);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.POWER_CABLE.get();
    }

    @Override
    protected boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (level.getBlockState(other).getBlock() instanceof PowerCableBlock) {
            return true;
        }
        return level instanceof Level real && real.isLoaded(other)
                && real.getCapability(Capabilities.EnergyStorage.BLOCK, other, dir.getOpposite()) != null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerCableBlockEntity cable) {
            cable.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
