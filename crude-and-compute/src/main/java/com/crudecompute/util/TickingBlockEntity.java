package com.crudecompute.util;

/** Block entities driven by {@link MachineBlock}'s ticker. */
public interface TickingBlockEntity {
    default void serverTick() {
    }

    default void clientTick() {
    }
}
