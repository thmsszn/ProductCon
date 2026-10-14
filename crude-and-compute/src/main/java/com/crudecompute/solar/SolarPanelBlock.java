package com.crudecompute.solar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A 2 m x 2 m panel: peak output = 4 m² x 1000 W/m² x efficiency. It only works next to an inverter (directly or in a chain). */
public class SolarPanelBlock extends Block {
    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(p -> new SolarPanelBlock(p, 0.15));
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 11, 16);

    private final double efficiency;

    public SolarPanelBlock(Properties properties, double efficiency) {
        super(properties);
        this.efficiency = efficiency;
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public double efficiency() {
        return efficiency;
    }

    /** Peak electrical output in watts under full sun. */
    public double peakWatts() {
        return 4.0 * 1000.0 * efficiency;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
