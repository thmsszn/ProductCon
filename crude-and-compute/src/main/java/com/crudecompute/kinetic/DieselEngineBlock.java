package com.crudecompute.kinetic;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.ShapeUtil;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Burns diesel and drives the network through the flywheel on its front side. */
public class DieselEngineBlock extends HorizontalMachineBlock {
    public static final MapCodec<DieselEngineBlock> CODEC = simpleCodec(DieselEngineBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontalMap(Shapes.or(
            Block.box(1, 0, 0, 15, 2, 16),
            Block.box(3, 1, 2, 13, 14, 16),
            Block.box(5, 14, 3, 11, 16, 15)));

    public DieselEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.DIESEL_ENGINE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DieselEngineBlockEntity engine) {
            engine.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        // exhaust stack sits at model position (0.76, 1.0, 0.9) for a north-facing engine
        Vec3 stack = rotateLocal(state.getValue(FACING), 0.76, 1.0, 0.9);
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + stack.x, pos.getY() + stack.y, pos.getZ() + stack.z, 0, 0.05, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + stack.x, pos.getY() + stack.y + 0.05, pos.getZ() + stack.z, 0, 0.03, 0);
        }
        if (random.nextInt(30) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.MINECART_RIDING,
                    SoundSource.BLOCKS, 0.15f, 0.6f, false);
        }
    }

    /** Rotates a model-space point (authored facing north) to the given facing. */
    public static Vec3 rotateLocal(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case EAST -> new Vec3(1 - z, y, x);
            case WEST -> new Vec3(z, y, 1 - x);
            default -> new Vec3(x, y, z);
        };
    }
}
