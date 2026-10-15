package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.ConnectingBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Rubber insulated cable carrying Forge Energy. */
public class PowerCableBlock extends ConnectingBlock {
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

    /** Per side: true when the cable ends in a plug (towards a machine) instead of continuing into another cable. */
    public static final Map<Direction, BooleanProperty> PLUGS = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values()) {
            PLUGS.put(dir, BooleanProperty.create("plug_" + dir.getSerializedName()));
        }
    }

    public PowerCableBlock(Properties properties) {
        super(properties, 2, 1.5);
        BlockState state = defaultBlockState();
        for (BooleanProperty plug : PLUGS.values()) {
            state = state.setValue(plug, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        PLUGS.values().forEach(builder::add);
    }

    @Override
    protected BlockState applyDirection(BlockState state, LevelAccessor level, BlockPos pos, Direction dir) {
        boolean connected = canConnect(level, pos, dir);
        boolean plug = connected && !(level.getBlockState(pos.relative(dir)).getBlock() instanceof PowerCableBlock);
        return state.setValue(PROPS.get(dir), connected).setValue(PLUGS.get(dir), plug);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> blockEntityType() {
        return ModBlockEntities.POWER_CABLE.get();
    }

    @Override
    protected boolean canConnect(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        Block block = level.getBlockState(other).getBlock();
        // cables also plug into solar panels and the inverter (DC side) so panel fields can be wired up
        if (block instanceof PowerCableBlock || block instanceof com.crudecompute.solar.SolarPanelBlock
                || block instanceof com.crudecompute.solar.InverterBlock) {
            return true;
        }
        return level instanceof Level real && real.isLoaded(other)
                && real.getCapability(Capabilities.EnergyStorage.BLOCK, other, dir.getOpposite()) != null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        com.crudecompute.power.PowerGrid.add(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            com.crudecompute.power.PowerGrid.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        com.crudecompute.power.PowerGrid.markDirty(level);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerCableBlockEntity cable) {
            cable.showStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
