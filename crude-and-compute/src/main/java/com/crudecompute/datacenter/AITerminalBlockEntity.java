package com.crudecompute.datacenter;

import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.Lang;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** Aggregates all server racks in range and turns their compute into trained AI models. */
public class AITerminalBlockEntity extends SyncedBlockEntity implements TickingBlockEntity {
    public static final int RANGE = 8;
    public static final int VERTICAL_RANGE = 4;
    /** PFLOP-seconds needed to train one model (8 GPUs ≈ 200 seconds). */
    public static final float TRAINING_COST = 4000f;

    private ItemStack drive = ItemStack.EMPTY;
    private float progress;
    private int racks;
    private int gpus;
    private float compute;
    private int power;
    private float maxTemp;

    private final IItemHandler driveSlot = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return drive;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!drive.isEmpty() || !stack.is(ModItems.EMPTY_MODEL_DRIVE.get())) {
                return stack;
            }
            if (!simulate) {
                setDrive(stack.copyWithCount(1));
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || !drive.is(ModItems.TRAINED_MODEL_DRIVE.get())) {
                return ItemStack.EMPTY;
            }
            ItemStack out = drive.copy();
            if (!simulate) {
                setDrive(ItemStack.EMPTY);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.EMPTY_MODEL_DRIVE.get());
        }
    };

    public AITerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AI_TERMINAL.get(), pos, state);
    }

    public IItemHandler getDriveSlot() {
        return driveSlot;
    }

    public ItemStack getDrive() {
        return drive;
    }

    public void setDrive(ItemStack stack) {
        drive = stack;
        progress = 0;
        sync();
    }

    public int getRacks() {
        return racks;
    }

    public int getGpus() {
        return gpus;
    }

    public float getComputeValue() {
        return compute;
    }

    public int getPower() {
        return power;
    }

    public float getMaxTemp() {
        return maxTemp;
    }

    /** Training progress 0..100, or -1 without a drive, 100 when the trained model is inside. */
    public int getProgressPercent() {
        if (drive.isEmpty()) {
            return -1;
        }
        if (drive.is(ModItems.TRAINED_MODEL_DRIVE.get())) {
            return 100;
        }
        return Math.min(99, (int) (progress * 100f / TRAINING_COST));
    }

    @Override
    public void serverTick() {
        if (level == null || level.getGameTime() % 20 != Math.floorMod(worldPosition.hashCode(), 20)) {
            return;
        }
        int r = 0;
        int g = 0;
        float c = 0;
        int p = 0;
        float t = 0;
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -VERTICAL_RANGE, -RANGE),
                worldPosition.offset(RANGE, VERTICAL_RANGE, RANGE))) {
            if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
                r++;
                g += rack.getGpus();
                c += rack.getCompute();
                p += rack.getPowerDraw();
                t = Math.max(t, rack.getTemperature());
            }
        }
        racks = r;
        gpus = g;
        compute = c;
        power = p;
        maxTemp = t;
        if (drive.is(ModItems.EMPTY_MODEL_DRIVE.get()) && compute > 0) {
            progress += compute;
            if (progress >= TRAINING_COST) {
                drive = new ItemStack(ModItems.TRAINED_MODEL_DRIVE.get());
                progress = 0;
            }
        }
        sync();
    }

    public void showStatus(Player player) {
        int percent = getProgressPercent();
        Component training;
        if (percent < 0) {
            training = Lang.msg("terminal.no_drive");
        } else if (percent >= 100) {
            training = Lang.msg("terminal.done");
        } else {
            training = Lang.msg("terminal.training", percent);
        }
        Lang.status(player, Lang.msg("terminal", racks, gpus, Lang.num(compute), training));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!drive.isEmpty()) {
            tag.put("Drive", drive.save(registries));
        }
        tag.putFloat("Progress", progress);
        tag.putInt("Racks", racks);
        tag.putInt("Gpus", gpus);
        tag.putFloat("Compute", compute);
        tag.putInt("Power", power);
        tag.putFloat("MaxTemp", maxTemp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drive = tag.contains("Drive") ? ItemStack.parseOptional(registries, tag.getCompound("Drive")) : ItemStack.EMPTY;
        progress = tag.getFloat("Progress");
        racks = tag.getInt("Racks");
        gpus = tag.getInt("Gpus");
        compute = tag.getFloat("Compute");
        power = tag.getInt("Power");
        maxTemp = tag.getFloat("MaxTemp");
    }
}
