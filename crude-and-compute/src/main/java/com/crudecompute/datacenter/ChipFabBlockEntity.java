package com.crudecompute.datacenter;

import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.Optimizable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Slots: 0 silicon wafers, 1 plastic, 2 gold nuggets, 3 circuit boards (output). One run consumes one of each
 * input and produces two circuit boards. Hoppers can insert into the input slots and pull from the output.
 */
public class ChipFabBlockEntity extends KineticBlockEntity implements Optimizable {
    public static final float IMPACT = 64f;
    public static final float WORK = 160f;
    public static final int OUTPUT_PER_RUN = 2;
    private static final int WAFER = 0;
    private static final int PLASTIC = 1;
    private static final int GOLD = 2;
    private static final int OUTPUT = 3;

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case WAFER -> stack.is(ModItems.SILICON_WAFER.get());
                case PLASTIC -> stack.is(ModItems.PLASTIC.get());
                case GOLD -> stack.is(Items.GOLD_NUGGET);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == WAFER) {
                boolean wafer = !getStackInSlot(WAFER).isEmpty();
                if (wafer != hasWaferSynced) {
                    hasWaferSynced = wafer;
                    sync();
                }
            }
        }
    };
    /** Automation view: inputs can be inserted, only the output can be extracted. */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return 4;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == OUTPUT ? stack : items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? items.extractItem(OUTPUT, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT && items.isItemValid(slot, stack);
        }
    };

    private float progress;
    private boolean working;
    private boolean hasWaferSynced;
    private boolean optimized;

    public ChipFabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHIP_FAB.get(), pos, state);
    }

    public IItemHandler getAutomation() {
        return automation;
    }

    public boolean hasWafer() {
        return !items.getStackInSlot(WAFER).isEmpty();
    }

    public boolean isWorking() {
        return working;
    }

    public float getProgress() {
        return progress / WORK;
    }

    public boolean accepts(ItemStack stack) {
        return items.isItemValid(WAFER, stack) || items.isItemValid(PLASTIC, stack) || items.isItemValid(GOLD, stack);
    }

    /** Inserts into whichever input slot fits; returns the rest. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack;
        for (int slot = WAFER; slot <= GOLD && !rest.isEmpty(); slot++) {
            rest = items.insertItem(slot, rest, false);
        }
        return rest;
    }

    public ItemStack takeOutput() {
        return items.extractItem(OUTPUT, 64, false);
    }

    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                list.add(items.getStackInSlot(i).copy());
            }
        }
        return list;
    }

    private boolean hasInputs() {
        return !items.getStackInSlot(WAFER).isEmpty() && !items.getStackInSlot(PLASTIC).isEmpty()
                && !items.getStackInSlot(GOLD).isEmpty()
                && items.getStackInSlot(OUTPUT).getCount() + OUTPUT_PER_RUN <= ModItems.CIRCUIT_BOARD.get().getDefaultMaxStackSize();
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        boolean nowWorking = isPowered() && hasInputs();
        if (nowWorking) {
            progress += speed / 32f * boost();
            if (progress >= WORK) {
                progress = 0;
                items.extractItem(WAFER, 1, false);
                items.extractItem(PLASTIC, 1, false);
                items.extractItem(GOLD, 1, false);
                ItemStack out = items.getStackInSlot(OUTPUT);
                items.setStackInSlot(OUTPUT, new ItemStack(ModItems.CIRCUIT_BOARD.get(), out.getCount() + OUTPUT_PER_RUN));
            }
        } else if (!hasInputs()) {
            progress = 0;
        }
        if (nowWorking != working) {
            working = nowWorking;
            sync();
            BlockState state = getBlockState();
            if (state.getValue(HorizontalMachineBlock.ACTIVE) != working) {
                level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
            }
        }
    }

    public void showStatus(Player player) {
        if (!isPowered()) {
            Lang.warn(player, kineticStatus(getBlockState().getBlock().getName()));
            return;
        }
        if (!hasInputs() && items.getStackInSlot(OUTPUT).isEmpty()) {
            Lang.warn(player, Lang.msg("chip_fab.missing"));
            return;
        }
        Component line = Lang.msg("chip_fab", Lang.num(speed), items.getStackInSlot(WAFER).getCount(), items.getStackInSlot(PLASTIC).getCount(),
                items.getStackInSlot(GOLD).getCount(), items.getStackInSlot(OUTPUT).getCount(), Math.round(getProgress() * 100));
        if (optimized) {
            line = line.copy().append(Lang.msg("optimized"));
        }
        Lang.status(player, line);
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        return side == Direction.DOWN || side == getBlockState().getValue(HorizontalMachineBlock.FACING).getOpposite();
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
        tag.put("Items", items.serializeNBT(registries));
        tag.putFloat("Progress", progress);
        tag.putBoolean("Working", working);
        tag.putBoolean("Optimized", optimized);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        progress = tag.getFloat("Progress");
        working = tag.getBoolean("Working");
        optimized = tag.getBoolean("Optimized");
        hasWaferSynced = hasWafer();
    }
}
