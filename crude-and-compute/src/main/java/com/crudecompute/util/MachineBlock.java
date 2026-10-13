package com.crudecompute.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Base for all blocks with a ticking block entity. */
public abstract class MachineBlock extends Block implements EntityBlock {
    protected MachineBlock(Properties properties) {
        super(properties);
    }

    protected abstract BlockEntityType<?> blockEntityType();

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTicker(level, type, blockEntityType());
    }

    @Nullable
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker(Level level, BlockEntityType<T> type, BlockEntityType<?> expected) {
        if (type != expected) {
            return null;
        }
        if (level.isClientSide) {
            return (l, p, s, be) -> {
                if (be instanceof TickingBlockEntity ticking) {
                    ticking.clientTick();
                }
            };
        }
        return (l, p, s, be) -> {
            if (be instanceof TickingBlockEntity ticking) {
                ticking.serverTick();
            }
        };
    }
}
