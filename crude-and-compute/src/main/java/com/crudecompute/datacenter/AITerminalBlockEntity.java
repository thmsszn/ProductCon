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

    // power dashboard (all values per second)
    public static final int HISTORY = 120;
    public static final int DASH_RANGE = 16;
    private float solarKw;
    private float consumptionKw;
    private float coolingKw;
    private int panels;
    private int batteryStored;
    private int batteryCapacity;
    private float inletAvg;
    private int[] histProd = new int[HISTORY];
    private int[] histCons = new int[HISTORY];
    private int[] histTemp = new int[HISTORY];

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
        float inlet = 0;
        float prodKw = 0;
        float consFe = 0;
        float coolFe = 0;
        int pan = 0;
        int stored = 0;
        int cap = 0;
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-DASH_RANGE, -VERTICAL_RANGE, -DASH_RANGE),
                worldPosition.offset(DASH_RANGE, VERTICAL_RANGE, DASH_RANGE))) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ServerRackBlockEntity rack) {
                r++;
                g += rack.getGpus();
                c += rack.getCompute();
                p += rack.getPowerDraw();
                consFe += rack.getPowerDraw();
                t = Math.max(t, rack.getTemperature());
                inlet += rack.getInlet();
            } else if (be instanceof CoolingUnitBlockEntity cooler && cooler.isRunning()) {
                coolFe += CoolingUnitBlockEntity.FE_PER_TICK;
            } else if (be instanceof com.crudecompute.solar.InverterBlockEntity inverter) {
                prodKw += inverter.getAcW() / 1000.0;
                pan += inverter.getPanels();
            } else if (be instanceof BatteryBlockEntity battery) {
                stored += battery.getStoredEnergy();
                cap += BatteryBlockEntity.CAPACITY;
            }
        }
        racks = r;
        gpus = g;
        compute = c;
        power = p;
        maxTemp = t;
        inletAvg = r > 0 ? inlet / r : 0;
        solarKw = prodKw;
        consumptionKw = (float) ((consFe + coolFe) * 10.0 / 1000.0);
        coolingKw = (float) (coolFe * 10.0 / 1000.0);
        panels = pan;
        batteryStored = stored;
        batteryCapacity = cap;
        push(histProd, Math.round(prodKw * 10));
        push(histCons, Math.round(consumptionKw * 10));
        push(histTemp, Math.round(t * 10));
        if (drive.is(ModItems.EMPTY_MODEL_DRIVE.get()) && compute > 0) {
            progress += compute;
            if (progress >= TRAINING_COST) {
                drive = new ItemStack(ModItems.TRAINED_MODEL_DRIVE.get());
                progress = 0;
            }
        }
        sync();
    }

    private static void push(int[] arr, int value) {
        System.arraycopy(arr, 1, arr, 0, arr.length - 1);
        arr[arr.length - 1] = value;
    }

    public float getSolarKw() {
        return solarKw;
    }

    public float getConsumptionKw() {
        return consumptionKw;
    }

    public float getCoolingKw() {
        return coolingKw;
    }

    public int getPanels() {
        return panels;
    }

    public int getBatteryStored() {
        return batteryStored;
    }

    public int getBatteryCapacity() {
        return batteryCapacity;
    }

    public float getInletAvg() {
        return inletAvg;
    }

    public int[] getHistProd() {
        return histProd;
    }

    public int[] getHistCons() {
        return histCons;
    }

    public int[] getHistTemp() {
        return histTemp;
    }

    /** Power usage effectiveness: total facility power / IT power. */
    public float getPue() {
        float it = consumptionKw - coolingKw;
        return it > 0.01f ? consumptionKw / it : 0f;
    }

    public ItemStack takeDrive() {
        ItemStack out = drive;
        if (!out.isEmpty()) {
            setDrive(ItemStack.EMPTY);
        }
        return out;
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

    private static int[] fit(int[] in) {
        int[] out = new int[HISTORY];
        System.arraycopy(in, 0, out, HISTORY - Math.min(in.length, HISTORY), Math.min(in.length, HISTORY));
        return out;
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
        tag.putFloat("Solar", solarKw);
        tag.putFloat("Cons", consumptionKw);
        tag.putFloat("Cool", coolingKw);
        tag.putInt("Panels", panels);
        tag.putInt("Batt", batteryStored);
        tag.putInt("BattCap", batteryCapacity);
        tag.putFloat("InletAvg", inletAvg);
        tag.putIntArray("HProd", histProd);
        tag.putIntArray("HCons", histCons);
        tag.putIntArray("HTemp", histTemp);
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
        solarKw = tag.getFloat("Solar");
        consumptionKw = tag.getFloat("Cons");
        coolingKw = tag.getFloat("Cool");
        panels = tag.getInt("Panels");
        batteryStored = tag.getInt("Batt");
        batteryCapacity = tag.getInt("BattCap");
        inletAvg = tag.getFloat("InletAvg");
        histProd = fit(tag.getIntArray("HProd"));
        histCons = fit(tag.getIntArray("HCons"));
        histTemp = fit(tag.getIntArray("HTemp"));
    }
}
