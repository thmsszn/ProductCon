package com.crudecompute.datacenter;

import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 2 x 2 x 2 battery bank: any cable plugged into any of its terminals charges or discharges it. */
public class BatteryBlock extends MultiblockControllerBlock {
    public static final MapCodec<BatteryBlock> CODEC = simpleCodec(BatteryBlock::new);
    private static final List<int[]> PARTS = new ArrayList<>();

    static {
        for (int r = 0; r < 2; r++) {
            for (int u = 0; u < 2; u++) {
                for (int f = 0; f < 2; f++) {
                    if (r + u + f > 0) {
                        PARTS.add(new int[]{r, u, f});
                    }
                }
            }
        }
    }

    public BatteryBlock(Properties properties) {
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
        return ModBlockEntities.BATTERY.get();
    }

    /** The front panel faces the player. */
    @Override
    protected Direction facingForPlacement(BlockPlaceContext context) {
        return context.getHorizontalDirection().getOpposite();
    }

    @Override
    public List<int[]> localParts() {
        return PARTS;
    }

    @Override
    public VoxelShape localShape(int right, int up, int forward) {
        return full();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            battery.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            return Math.round(battery.getFill() * 15f);
        }
        return 0;
    }
}
