package com.crudecompute.oil;

import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * 1 x 3 x 3 pumpjack. The controller is the back-bottom cell with the gearbox (rotation enters from the left or
 * right side), the wellhead is two cells in front of it: oil shale has to lie somewhere below the wellhead.
 */
public class PumpjackBlock extends MultiblockControllerBlock {
    public static final MapCodec<PumpjackBlock> CODEC = simpleCodec(PumpjackBlock::new);
    private static final List<int[]> PARTS = List.of(
            new int[]{0, 0, 1}, new int[]{0, 0, 2},
            new int[]{0, 1, 0}, new int[]{0, 1, 1}, new int[]{0, 1, 2},
            new int[]{0, 2, 0}, new int[]{0, 2, 1}, new int[]{0, 2, 2});

    public PumpjackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.PUMPJACK.get();
    }

    @Override
    public List<int[]> localParts() {
        return PARTS;
    }

    @Override
    public VoxelShape localShape(int right, int up, int forward) {
        return switch (up * 3 + forward) {
            case 0 -> Shapes.or(box(4, 0, 0, 12, 2, 16), box(4, 2, 3, 12, 13, 14), box(1, 4, 4, 15, 12, 12)); // gearbox
            case 1 -> Shapes.or(box(0, 0, 13, 16, 2, 16), box(4, 0, 0, 12, 2, 16), box(2, 0, 2, 14, 16, 14)); // A-frame foot
            case 2 -> Shapes.or(box(2, 0, 2, 14, 1, 14), box(5, 0, 5, 11, 10, 11), box(0, 3, 6, 16, 7, 10)); // wellhead
            case 3 -> box(1, 0, 6, 15, 16, 10); // pitman arms
            case 4 -> box(4, 0, 4, 12, 16, 12); // samson post
            case 5 -> box(6, 0, 6, 10, 16, 10); // polished rod
            case 6 -> box(1, 0, 4, 15, 9, 12); // beam end + equalizer
            case 7 -> box(5, 0, 0, 11, 9, 16); // beam + bearing
            default -> box(5, 0, 0, 11, 16, 16); // horsehead
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PumpjackBlockEntity pumpjack
                && FluidUtil.interactWithFluidHandler(player, hand, pumpjack.getOutput())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PumpjackBlockEntity pumpjack) {
            pumpjack.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
