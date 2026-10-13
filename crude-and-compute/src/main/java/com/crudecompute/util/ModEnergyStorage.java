package com.crudecompute.util;

import net.neoforged.neoforge.energy.EnergyStorage;

/** Forge Energy buffer with a change callback and internal (non-capability) access. */
public class ModEnergyStorage extends EnergyStorage {
    private final Runnable onChanged;

    public ModEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            onChanged.run();
        }
        return received;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int extracted = super.extractEnergy(toExtract, simulate);
        if (extracted > 0 && !simulate) {
            onChanged.run();
        }
        return extracted;
    }

    /** Adds energy ignoring the receive limit (e.g. a generator producing power). */
    public int addInternal(int amount) {
        int added = Math.min(capacity - energy, Math.max(0, amount));
        if (added > 0) {
            energy += added;
            onChanged.run();
        }
        return added;
    }

    /** Removes energy ignoring the extract limit (e.g. a machine consuming power). */
    public int removeInternal(int amount) {
        int removed = Math.min(energy, Math.max(0, amount));
        if (removed > 0) {
            energy -= removed;
            onChanged.run();
        }
        return removed;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(capacity, energy));
    }

    public int getSpace() {
        return capacity - energy;
    }
}
