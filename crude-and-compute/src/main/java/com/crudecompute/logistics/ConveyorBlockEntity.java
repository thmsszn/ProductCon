package com.crudecompute.logistics;

import com.crudecompute.kinetic.KineticBlockEntity;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Holds up to four items on the belt; progress 0 = back edge, 1 = front edge. */
public class ConveyorBlockEntity extends KineticBlockEntity {
    public static final float IMPACT = 4f;
    public static final int MAX_ITEMS = 4;
    public static final float SPACING = 0.25f;
    /** Belt travel per tick per RPM. */
    public static final float PER_RPM = 1f / 640f;

    public static final class Entry {
        public ItemStack stack;
        public float progress;

        Entry(ItemStack stack, float progress) {
            this.stack = stack;
            this.progress = progress;
        }
    }

    private final List<Entry> items = new ArrayList<>();
    private int syncCooldown;

    /** Insertion from hoppers/other mods: one item at the back edge. */
    private final IItemHandler inserter = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !hasRoomAt(0f)) {
                return stack;
            }
            if (!simulate) {
                add(stack.copyWithCount(1), 0f);
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    };

    public ConveyorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONVEYOR.get(), pos, state);
    }

    public IItemHandler getInserter() {
        return inserter;
    }

    public List<Entry> getItems() {
        return items;
    }

    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (Entry e : items) {
            list.add(e.stack.copy());
        }
        return list;
    }

    private Direction facing() {
        return getBlockState().getValue(HorizontalMachineBlock.FACING);
    }

    public boolean hasRoomAt(float at) {
        if (items.size() >= MAX_ITEMS) {
            return false;
        }
        for (Entry e : items) {
            if (Math.abs(e.progress - at) < SPACING) {
                return false;
            }
        }
        return true;
    }

    public void add(ItemStack single, float at) {
        items.add(new Entry(single, at));
        items.sort((a, b) -> Float.compare(b.progress, a.progress));
        sync();
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        absorbDroppedItems();
        boolean changed = advance(true);
        if (!items.isEmpty() && isPowered()) {
            if (--syncCooldown <= 0) {
                syncCooldown = 2;
                sync();
            }
        } else if (changed) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        super.clientTick();
        advance(false);
    }

    /** Moves the items; on the server also hands over items at the front edge. Returns whether the list changed. */
    private boolean advance(boolean server) {
        if (items.isEmpty() || !isPowered()) {
            return false;
        }
        float move = speed * PER_RPM;
        boolean changed = false;
        float limit = 1f;
        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(i);
            e.progress = Math.min(limit, e.progress + move);
            if (server && e.progress >= 1f && handOver(e.stack)) {
                items.remove(i--);
                changed = true;
                continue;
            }
            limit = e.progress - SPACING;
        }
        return changed;
    }

    private boolean handOver(ItemStack stack) {
        if (level == null) {
            return false;
        }
        Direction facing = facing();
        BlockPos next = worldPosition.relative(facing);
        if (!level.isLoaded(next)) {
            return false;
        }
        if (level.getBlockEntity(next) instanceof ConveyorBlockEntity other) {
            if (other.facing() == facing.getOpposite() || !other.hasRoomAt(0f)) {
                return false;
            }
            other.add(stack.copy(), 0f);
            return true;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, next, facing.getOpposite());
        if (handler == null) {
            return false;
        }
        return ItemHandlerHelper.insertItem(handler, stack.copy(), false).isEmpty();
    }

    private void absorbDroppedItems() {
        if (level == null || level.getGameTime() % 4 != 0 || !hasRoomAt(0.5f)) {
            return;
        }
        AABB box = new AABB(worldPosition).setMinY(worldPosition.getY() + 0.3).setMaxY(worldPosition.getY() + 0.9);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) {
                add(stack.copyWithCount(1), 0.5f);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    entity.discard();
                }
                return;
            }
        }
    }

    public void showStatus(Player player) {
        Lang.status(player, Lang.msg("belt", items.size(), Lang.num(speed)));
    }

    @Override
    public boolean hasShaftTowards(Direction side) {
        Direction facing = facing();
        return side == facing.getClockWise() || side == facing.getCounterClockWise();
    }

    @Override
    public float getStressImpact() {
        return IMPACT;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Entry e : items) {
            CompoundTag entry = new CompoundTag();
            entry.put("Item", e.stack.save(registries));
            entry.putFloat("Progress", e.progress);
            list.add(entry);
        }
        tag.put("Belt", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        for (Tag t : tag.getList("Belt", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
            if (!stack.isEmpty()) {
                items.add(new Entry(stack, entry.getFloat("Progress")));
            }
        }
        items.sort((a, b) -> Float.compare(b.progress, a.progress));
    }
}
