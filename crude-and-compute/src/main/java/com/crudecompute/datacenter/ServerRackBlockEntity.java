package com.crudecompute.datacenter;

import com.crudecompute.multiblock.MultiblockCapabilities;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.HorizontalMachineBlock;
import com.crudecompute.util.Lang;
import com.crudecompute.util.ModEnergyStorage;
import com.crudecompute.util.SyncedBlockEntity;
import com.crudecompute.util.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Holds up to 8 GPUs. Each GPU draws {@link #FE_PER_GPU} FE/t, delivers {@link #PFLOPS_PER_GPU} PFLOPS and heats the
 * rack up. Cooling units nearby remove heat; above {@link #THROTTLE_TEMP} the rack throttles, above
 * {@link #SHUTDOWN_TEMP} it shuts down until it has cooled to {@link #RESTART_TEMP}.
 */
public class ServerRackBlockEntity extends SyncedBlockEntity implements TickingBlockEntity, MultiblockCapabilities {
    public static final int MAX_GPUS = 8;
    public static final int FE_PER_GPU = 64;
    public static final float PFLOPS_PER_GPU = 2.5f;
    public static final float HEAT_PER_GPU = 1.5f;
    public static final float AMBIENT = 22f;
    public static final float THROTTLE_TEMP = 80f;
    public static final float SHUTDOWN_TEMP = 100f;
    public static final float RESTART_TEMP = 50f;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_THROTTLED = 2;
    public static final int STATUS_OVERHEATED = 3;
    public static final int STATUS_NO_POWER = 4;

    private final ModEnergyStorage energy = new ModEnergyStorage(64_000, 8192, 0, this::setChanged);
    private int gpus;
    private float temperature = AMBIENT;
    private boolean overheated;
    private boolean powered;
    private float cooling;
    private int status;

    private final IItemHandler gpuSlot = new IItemHandler() {
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
            if (!stack.is(ModItems.GPU.get()) || gpus >= MAX_GPUS) {
                return stack;
            }
            if (!simulate) {
                addGpu();
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
            return stack.is(ModItems.GPU.get());
        }
    };

    public ServerRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVER_RACK.get(), pos, state);
    }

    public int getGpus() {
        return gpus;
    }

    public float getTemperature() {
        return temperature;
    }

    public int getStatus() {
        return status;
    }

    /** Current compute output in PFLOPS. */
    public float getCompute() {
        return switch (status) {
            case STATUS_ONLINE -> gpus * PFLOPS_PER_GPU;
            case STATUS_THROTTLED -> gpus * PFLOPS_PER_GPU * 0.5f;
            default -> 0f;
        };
    }

    public int getPowerDraw() {
        return status == STATUS_ONLINE || status == STATUS_THROTTLED ? gpus * FE_PER_GPU : 0;
    }

    public boolean addGpu() {
        if (gpus >= MAX_GPUS) {
            return false;
        }
        gpus++;
        sync();
        return true;
    }

    public boolean removeGpu() {
        if (gpus <= 0) {
            return false;
        }
        gpus--;
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        int needed = gpus * FE_PER_GPU;
        if (status == STATUS_THROTTLED) {
            needed /= 2;
        }
        powered = gpus > 0 && !overheated && energy.getEnergyStored() >= needed;
        if (powered) {
            energy.removeInternal(needed);
        }
        if (level.getGameTime() % 20 == Math.floorMod(worldPosition.hashCode(), 20)) {
            thermalUpdate();
        }
    }

    /** Once per second: heat balance and status. */
    private void thermalUpdate() {
        cooling = CoolingUnitBlockEntity.coolingFor(level, worldPosition);
        float heat = powered ? gpus * HEAT_PER_GPU * (temperature >= THROTTLE_TEMP ? 0.5f : 1f) : 0f;
        float passive = (temperature - AMBIENT) * 0.05f;
        temperature = Math.max(AMBIENT - 2f, temperature + heat - passive - cooling);
        if (overheated && temperature < RESTART_TEMP) {
            overheated = false;
        } else if (temperature >= SHUTDOWN_TEMP) {
            overheated = true;
        }
        int newStatus;
        if (gpus == 0) {
            newStatus = STATUS_IDLE;
        } else if (overheated) {
            newStatus = STATUS_OVERHEATED;
        } else if (!powered) {
            newStatus = STATUS_NO_POWER;
        } else if (temperature >= THROTTLE_TEMP) {
            newStatus = STATUS_THROTTLED;
        } else {
            newStatus = STATUS_ONLINE;
        }
        status = newStatus;
        BlockState state = getBlockState();
        boolean active = status == STATUS_ONLINE || status == STATUS_THROTTLED;
        if (state.getValue(HorizontalMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(HorizontalMachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        sync(); // temperature display on the rack + terminal
    }

    public void showStatus(Player player) {
        String key = switch (status) {
            case STATUS_ONLINE -> "rack.online";
            case STATUS_THROTTLED -> "rack.throttled";
            case STATUS_OVERHEATED -> "rack.overheated";
            case STATUS_NO_POWER -> "rack.no_power";
            default -> "rack.idle";
        };
        Component line = Lang.msg("rack", gpus, Lang.num(temperature), Lang.msg(key), energy.getEnergyStored(),
                energy.getMaxEnergyStored(), Lang.num(cooling));
        if (status == STATUS_ONLINE || status == STATUS_IDLE) {
            Lang.status(player, line);
        } else {
            Lang.warn(player, line);
        }
    }

    @Nullable
    @Override
    public IEnergyStorage getEnergyStorage(int right, int up, int forward, @Nullable Direction side) {
        return energy;
    }

    @Nullable
    @Override
    public IItemHandler getItemHandler(int right, int up, int forward, @Nullable Direction side) {
        return gpuSlot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Gpus", gpus);
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Overheated", overheated);
        tag.putInt("Status", status);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        gpus = tag.getInt("Gpus");
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT;
        overheated = tag.getBoolean("Overheated");
        status = tag.getInt("Status");
    }
}
