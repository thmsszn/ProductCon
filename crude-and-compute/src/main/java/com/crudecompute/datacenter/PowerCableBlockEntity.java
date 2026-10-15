package com.crudecompute.datacenter;

import com.crudecompute.power.PowerGrid;
import com.crudecompute.registry.ModBlockEntities;
import com.crudecompute.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A piece of cable. It stores nothing; the {@link PowerGrid} treats all touching cables as one network. */
public class PowerCableBlockEntity extends BlockEntity {
    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CABLE.get(), pos, state);
    }

    protected PowerCableBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            PowerGrid.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            PowerGrid.remove(level, worldPosition);
        }
    }

    public PowerGrid.Network network() {
        return level == null ? null : PowerGrid.networkAt(level, worldPosition);
    }

    public void showStatus(Player player) {
        PowerGrid.Network n = network();
        if (n == null) {
            Lang.status(player, Lang.msg("cable.idle"));
            return;
        }
        double w = 10.0 / 1000.0;
        Component line = Lang.msg("cable", n.cableCount(), Math.round(n.lossFraction() * 100),
                Lang.num(n.produced() * w), Lang.num(n.consumed() * w), n.producerCount(), n.consumerCount(), n.bufferCount(),
                Lang.num(n.buffered() * w));
        if (n.overloaded()) {
            Lang.warn(player, line);
        } else {
            Lang.status(player, line);
        }
    }
}
