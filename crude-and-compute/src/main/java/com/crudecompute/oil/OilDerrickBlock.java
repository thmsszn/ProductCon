package com.crudecompute.oil;

import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * 3 x 3 footprint, 7 blocks tall lattice tower. The controller is the middle cell of the back edge, the drill pipe
 * runs through the centre of the footprint (right 0, forward 1). Rotation enters from behind.
 */
public class OilDerrickBlock extends MultiblockControllerBlock {
    public static final MapCodec<OilDerrickBlock> CODEC = simpleCodec(OilDerrickBlock::new);
    private static final List<int[]> PARTS = new ArrayList<>();

    static {
        for (int right = -1; right <= 1; right++) {
            for (int forward = 0; forward <= 2; forward++) {
                if (right != 0 || forward != 0) {
                    PARTS.add(new int[]{right, 0, forward});
                }
            }
        }
        for (int up = 1; up <= 6; up++) {
            PARTS.add(new int[]{0, up, 1});
        }
    }

    public OilDerrickBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.OIL_DERRICK.get();
    }

    @Override
    public List<int[]> localParts() {
        return PARTS;
    }

    @Override
    public VoxelShape localShape(int right, int up, int forward) {
        return up == 0 ? box(0, 0, 0, 16, 16, 16) : box(6, 0, 6, 10, 16, 10);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof OilDerrickBlockEntity derrick
                && FluidUtil.interactWithFluidHandler(player, hand, derrick.getOutput())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof OilDerrickBlockEntity derrick) {
            derrick.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
