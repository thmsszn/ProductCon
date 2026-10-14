package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Passes rotation between the tower above and the shafts leaving through the four sides (not the ground). */
public class TowerBaseBlockEntity extends KineticBlockEntity {
    public TowerBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWER_BASE.get(), pos, state);
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side != Direction.DOWN;
    }
}
