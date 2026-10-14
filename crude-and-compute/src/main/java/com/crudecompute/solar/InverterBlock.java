package com.crudecompute.solar;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class InverterBlock extends HorizontalMachineBlock {
    public static final MapCodec<InverterBlock> CODEC = simpleCodec(InverterBlock::new);

    public InverterBlock(Properties properties) {
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
        return ModBlockEntities.INVERTER.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InverterBlockEntity inverter) {
            inverter.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
