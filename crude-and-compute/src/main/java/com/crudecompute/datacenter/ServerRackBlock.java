package com.crudecompute.datacenter;

import com.crudecompute.item.ServerItem;
import com.crudecompute.menu.RackMenu;
import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.Lang;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two blocks tall 14U rack. Right-click with a server to mount it, with an empty hand to open the management UI. */
public class ServerRackBlock extends MultiblockControllerBlock {
    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);
    private static final List<int[]> PARTS = List.of(new int[]{0, 1, 0});

    public ServerRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.SERVER_RACK.get();
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
        return box(0.5, 0, 0.5, 15.5, 16, 15.5);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ServerItem item)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            // the U the player is pointing at (0-based from the bottom), taking the server's own height into account
            double relY = hit.getLocation().y - pos.getY();
            int pointed = (int) Math.floor((relY - ServerRackBlockEntity.U0) / ServerRackBlockEntity.U_H);
            int u = rack.insert(item.configOf(stack), pointed);
            if (u >= 0) {
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.8f);
            } else {
                Lang.warn(player, Component.translatable("hw.err.no_space", item.getSizeU()));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            if (player.isShiftKeyDown()) {
                rack.showStatus(player);
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new RackMenu(id, inv, pos), Component.translatable("block.crudecompute.server_rack")),
                        buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void beforeRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            for (ServerRackBlockEntity.Slot slot : rack.getSlots()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        ServerItem.make(ModItems.serverItem(slot.cfg.sizeU), slot.cfg));
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack && rack.getStatus() == ServerRackBlockEntity.STATUS_OVERHEATED
                && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 2.05,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.04, 0);
        }
    }
}
