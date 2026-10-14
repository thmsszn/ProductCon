package com.crudecompute.menu;

import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.hardware.Catalog;
import com.crudecompute.hardware.HwError;
import com.crudecompute.hardware.HwError.Problem;
import com.crudecompute.hardware.Kind;
import com.crudecompute.hardware.Part;
import com.crudecompute.item.ServerItem;
import com.crudecompute.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server rack management. The menu has no item slots; the GUI sends action ids through {@link #clickMenuButton} and the
 * server validates and applies them. Feedback (error code + argument) is synchronised through three data slots.
 */
public class RackMenu extends AbstractContainerMenu {
    public static final int A_SELECT = 1000;
    public static final int A_INSTALL = 2000;
    public static final int A_REMOVE_PART = 3000;
    public static final int A_MODE = 4000;
    public static final int A_EJECT = 5000;
    public static final int A_INSERT_HELD = 6000;
    public static final int A_MOVE = 7000;

    public final BlockPos pos;
    private final ContainerData data = new SimpleContainerData(3);
    private int selectedU = -1;
    private int seq;

    public RackMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public RackMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.RACK.get(), id);
        this.pos = pos;
        addDataSlots(data);
    }

    /** Last feedback: error (or null) and its argument. Sequence number changes on every action. */
    @Nullable
    public HwError lastError() {
        return HwError.fromCode(data.get(0));
    }

    public int lastArg() {
        return data.get(1);
    }

    public int lastSeq() {
        return data.get(2);
    }

    @Nullable
    public ServerRackBlockEntity rack(Player player) {
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof ServerRackBlockEntity r ? r : null;
    }

    private void feedback(@Nullable Problem problem) {
        data.set(0, problem == null ? 0 : problem.error().code());
        data.set(1, problem == null ? 0 : Math.min(30000, problem.arg()));
        seq = (seq + 1) % 30000;
        data.set(2, seq);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ServerRackBlockEntity rack = rack(player);
        if (rack == null || player.level().isClientSide) {
            return false;
        }
        if (id >= A_SELECT && id < A_SELECT + 100) {
            selectedU = id - A_SELECT;
            feedback(null);
        } else if (id >= A_INSTALL && id < A_INSTALL + 500) {
            Part part = ServerRackBlockEntity.catalogPart(id - A_INSTALL);
            feedback(part == null ? new Problem(HwError.NOT_FOUND, 0) : rack.installPart(selectedU, part));
        } else if (id >= A_REMOVE_PART && id < A_REMOVE_PART + 1000) {
            int kind = (id - A_REMOVE_PART) / 100;
            int index = (id - A_REMOVE_PART) % 100;
            feedback(kind < Kind.values().length ? rack.removePart(selectedU, Kind.values()[kind], index) : new Problem(HwError.NOT_FOUND, 0));
        } else if (id >= A_MODE && id < A_MODE + 4) {
            rack.setMode(selectedU, id - A_MODE);
            feedback(null);
        } else if (id >= A_EJECT && id < A_EJECT + 100) {
            ServerRackBlockEntity.Slot slot = rack.remove(id - A_EJECT);
            if (slot != null) {
                ItemStack stack = ServerItem.make(com.crudecompute.registry.ModItems.serverItem(slot.cfg.sizeU), slot.cfg);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                if (selectedU == slot.u) {
                    selectedU = -1;
                }
            }
            feedback(null);
        } else if (id == A_INSERT_HELD) {
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof ServerItem item) {
                int u = rack.insert(item.configOf(held), -1);
                if (u >= 0) {
                    held.shrink(1);
                    feedback(null);
                } else {
                    feedback(new Problem(HwError.NO_SPACE, item.getSizeU()));
                }
            } else {
                feedback(new Problem(HwError.NOT_FOUND, 0));
            }
        } else if (id >= A_MOVE && id < A_MOVE + 1000) {
            int u = (id - A_MOVE) / 10;
            int dir = (id - A_MOVE) % 10;
            if (rack.move(u, dir == 1 ? 1 : -1) && selectedU == u) {
                selectedU += dir == 1 ? 1 : -1;
            }
            feedback(null);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) <= 64.0 && rack(player) != null;
    }

    /** Ensures the catalog class is loaded on both sides before the screen indexes into it. */
    public static int catalogSize() {
        return Catalog.ALL.size();
    }
}
