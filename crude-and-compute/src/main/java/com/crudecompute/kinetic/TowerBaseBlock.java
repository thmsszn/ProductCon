package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.MachineBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Concrete foundation of a wind tower: turns the tower axle towards any horizontal side (to the generator). */
public class TowerBaseBlock extends MachineBlock {
    public static final MapCodec<TowerBaseBlock> CODEC = simpleCodec(TowerBaseBlock::new);

    public TowerBaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.TOWER_BASE.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return KineticBlocks.showStatus(level, pos, player, getName());
    }
}
