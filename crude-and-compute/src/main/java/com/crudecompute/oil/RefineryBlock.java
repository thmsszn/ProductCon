package com.crudecompute.oil;

import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Three blocks tall distillation column: crude oil in at the base, diesel out of the middle, plastic pellets below. */
public class RefineryBlock extends MultiblockControllerBlock {
    public static final MapCodec<RefineryBlock> CODEC = simpleCodec(RefineryBlock::new);
    private static final List<int[]> PARTS = List.of(new int[]{0, 1, 0}, new int[]{0, 2, 0});

    public RefineryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.REFINERY.get();
    }

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
        return up == 2 ? box(2, 0, 2, 14, 16, 14) : box(1, 0, 1, 15, 16, 15);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery
                && FluidUtil.interactWithFluidHandler(player, hand, refinery.getCombinedHandler())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery) {
            refinery.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void beforeRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery) {
            ItemStack plastic = refinery.getPlastic().getStackInSlot(0);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, plastic.copy());
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        // gas flare on top of the thin side pipe: model position (0.5, 3.0, 0.09) for a north-facing column
        Vec3 flare = com.crudecompute.kinetic.DieselEngineBlock.rotateLocal(state.getValue(FACING), 0.5, 3.02, 0.09);
        double x = pos.getX() + flare.x, y = pos.getY() + flare.y, z = pos.getZ() + flare.z;
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.15, z, 0, 0.04, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 3.05, pos.getZ() + 0.5, 0, 0.03, 0);
        }
    }
}
