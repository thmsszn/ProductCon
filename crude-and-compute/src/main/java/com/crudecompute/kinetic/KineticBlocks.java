package com.crudecompute.kinetic;

import com.crudecompute.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Shared interaction helpers for kinetic blocks. */
public final class KineticBlocks {
    private KineticBlocks() {
    }

    /** Right-click with an empty hand: show speed and stress of the network. */
    public static InteractionResult showStatus(Level level, BlockPos pos, Player player, Component name) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) {
            Lang.status(player, kinetic.kineticStatus(name));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
