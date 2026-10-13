package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Shafts (one axis) and gearboxes (all six sides) just pass rotation on. */
public class TransmissionBlockEntity extends KineticBlockEntity {
    public TransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static TransmissionBlockEntity shaft(BlockPos pos, BlockState state) {
        return new TransmissionBlockEntity(ModBlockEntities.SHAFT.get(), pos, state);
    }

    public static TransmissionBlockEntity gearbox(BlockPos pos, BlockState state) {
        return new TransmissionBlockEntity(ModBlockEntities.GEARBOX.get(), pos, state);
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return side.getAxis() == state.getValue(BlockStateProperties.AXIS);
        }
        return true;
    }
}
