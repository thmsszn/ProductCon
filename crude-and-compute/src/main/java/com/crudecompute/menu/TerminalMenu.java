package com.crudecompute.menu;

import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Datacenter dashboard of the AI terminal. The only action is ejecting the model drive. */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int A_EJECT = 1;

    public final BlockPos pos;

    public TerminalMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public TerminalMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.TERMINAL.get(), id);
        this.pos = pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == A_EJECT && !player.level().isClientSide && player.level().getBlockEntity(pos) instanceof AITerminalBlockEntity terminal) {
            ItemStack drive = terminal.takeDrive();
            if (!drive.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, drive);
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof AITerminalBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
