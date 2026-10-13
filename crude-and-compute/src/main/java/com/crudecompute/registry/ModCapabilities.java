package com.crudecompute.registry;

import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.multiblock.MultiblockControllerBlock;
import com.crudecompute.multiblock.MultiblockPartBlock;
import com.crudecompute.util.HorizontalMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Exposes fluid, energy and item handlers of all machines to pipes, cables, hoppers and other mods. */
public final class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        // simple blocks
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FLUID_PIPE.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.OIL_TANK.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.DIESEL_ENGINE.get(), (be, side) -> be.getFuelHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CABLE.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.COOLING_UNIT.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.AI_TERMINAL.get(), (be, side) -> be.getDriveSlot());

        // multi-block controllers (cell 0, 0, 0)
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.PUMPJACK.get(), (be, side) -> be.getFluidHandler(0, 0, 0, side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.REFINERY.get(), (be, side) -> be.getFluidHandler(0, 0, 0, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.REFINERY.get(), (be, side) -> be.getItemHandler(0, 0, 0, side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SERVER_RACK.get(), (be, side) -> be.getEnergyStorage(0, 0, 0, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SERVER_RACK.get(), (be, side) -> be.getItemHandler(0, 0, 0, side));

        // multi-block parts forward to their controller
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            Cell cell = cell(level, pos, state);
            return cell == null ? null : cell.provider.getFluidHandler(cell.right, cell.up, cell.forward, side);
        }, ModBlocks.MULTIBLOCK_PART.get());
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> {
            Cell cell = cell(level, pos, state);
            return cell == null ? null : cell.provider.getEnergyStorage(cell.right, cell.up, cell.forward, side);
        }, ModBlocks.MULTIBLOCK_PART.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            Cell cell = cell(level, pos, state);
            return cell == null ? null : cell.provider.getItemHandler(cell.right, cell.up, cell.forward, side);
        }, ModBlocks.MULTIBLOCK_PART.get());
    }

    private record Cell(MultiblockCapabilities provider, int right, int up, int forward) {
    }

    @Nullable
    private static Cell cell(Level level, BlockPos pos, BlockState state) {
        BlockPos main = MultiblockPartBlock.getMainPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (!(mainState.getBlock() instanceof MultiblockControllerBlock)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(main);
        if (!(be instanceof MultiblockCapabilities provider)) {
            return null;
        }
        Direction facing = mainState.getValue(HorizontalMachineBlock.FACING);
        Vec3i offset = MultiblockPartBlock.getOffset(state);
        int[] local = MultiblockControllerBlock.toLocal(facing, offset.getX(), offset.getY(), offset.getZ());
        return new Cell(provider, local[0], local[1], local[2]);
    }
}
