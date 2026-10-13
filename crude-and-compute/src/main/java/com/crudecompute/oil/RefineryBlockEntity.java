package com.crudecompute.oil;

import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModFluids;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.FluidHandlers;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.Optimizable;
import com.crudecompute.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class RefineryBlockEntity extends KineticBlockEntity implements MultiblockCapabilities, Optimizable {
    public static final float IMPACT = 96f;
    public static final int TANK_CAPACITY = 8000;
    public static final int BATCH_CRUDE = 10;
    public static final int BATCH_DIESEL = 6;
    public static final int CRUDE_PER_PLASTIC = 200;

    private final FluidTank input = new FluidTank(TANK_CAPACITY, stack -> stack.is(ModFluids.CRUDE_OIL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank output = new FluidTank(TANK_CAPACITY, stack -> stack.is(ModFluids.DIESEL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler plastic = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IFluidHandler combined = FluidHandlers.combined(input, output);
    private final IFluidHandler dieselOut = FluidHandlers.outputOnly(output);
    private final IItemHandler plasticOut = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return plastic.getStackInSlot(0);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return plastic.extractItem(0, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private float progress;
    private int plasticCounter;
    private int activeTicks;
    private boolean optimized;

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
    }

    public IFluidHandler getCombinedHandler() {
        return combined;
    }

    public ItemStackHandler getPlastic() {
        return plastic;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (isPowered()) {
            progress = Math.min(progress + speed / 128f * boost(), 8f);
        }
        while (progress >= 1f && canProcess()) {
            progress -= 1f;
            input.drain(BATCH_CRUDE, IFluidHandler.FluidAction.EXECUTE);
            output.fill(new FluidStack(ModFluids.DIESEL.get(), BATCH_DIESEL), IFluidHandler.FluidAction.EXECUTE);
            plasticCounter += BATCH_CRUDE;
            if (plasticCounter >= CRUDE_PER_PLASTIC) {
                plasticCounter -= CRUDE_PER_PLASTIC;
                plastic.insertItem(0, new ItemStack(ModItems.PLASTIC.get()), false);
            }
            activeTicks = 20;
        }
        if (!canProcess()) {
            progress = Math.min(progress, 1f);
        }
        if (!output.isEmpty()) {
            BlockPos middle = worldPosition.above();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                Transfer.pushFluid(level, middle, dir, output, 200);
            }
        }
        boolean active = activeTicks > 0;
        if (activeTicks > 0) {
            activeTicks--;
        }
        BlockState state = getBlockState();
        if (state.getValue(HorizontalMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private boolean canProcess() {
        boolean plasticRoom = plasticCounter + BATCH_CRUDE < CRUDE_PER_PLASTIC
                || plastic.getStackInSlot(0).getCount() < plastic.getSlotLimit(0);
        return input.getFluidAmount() >= BATCH_CRUDE && output.getSpace() >= BATCH_DIESEL && plasticRoom;
    }

    public void showStatus(Player player) {
        if (overstressed || speed <= 0) {
            Lang.warn(player, kineticStatus(getBlockState().getBlock().getName()));
            return;
        }
        Component line = Lang.msg("refinery", Lang.num(speed), input.getFluidAmount(), output.getFluidAmount(),
                plastic.getStackInSlot(0).getCount());
        if (optimized) {
            line = line.copy().append(Lang.msg("optimized"));
        }
        Lang.status(player, line);
    }

    @Nullable
    @Override
    public IFluidHandler getFluidHandler(int right, int up, int forward, @Nullable Direction side) {
        return switch (up) {
            case 0 -> combined;
            case 1 -> dieselOut;
            default -> null;
        };
    }

    @Nullable
    @Override
    public IItemHandler getItemHandler(int right, int up, int forward, @Nullable Direction side) {
        return up == 0 ? plasticOut : null;
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side.getAxis().isHorizontal();
    }

    @Override
    public float getStressImpact() {
        return IMPACT;
    }

    @Override
    public boolean isOptimized() {
        return optimized;
    }

    @Override
    public void setOptimized() {
        optimized = true;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("Output", output.writeToNBT(registries, new CompoundTag()));
        tag.put("Plastic", plastic.serializeNBT(registries));
        tag.putFloat("Progress", progress);
        tag.putInt("PlasticCounter", plasticCounter);
        tag.putBoolean("Optimized", optimized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.readFromNBT(registries, tag.getCompound("Input"));
        output.readFromNBT(registries, tag.getCompound("Output"));
        plastic.deserializeNBT(registries, tag.getCompound("Plastic"));
        progress = tag.getFloat("Progress");
        plasticCounter = tag.getInt("PlasticCounter");
        optimized = tag.getBoolean("Optimized");
    }
}
