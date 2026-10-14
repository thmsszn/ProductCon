package com.crudecompute.hardware;

/** Reasons why a part cannot be installed or a server cannot boot. Code 0 is reserved for "no error". */
public enum HwError {
    MOBO_TOO_LARGE, NEEDS_MOBO, CPU_SOCKET, CPU_SLOTS, RAM_TYPE, RAM_SLOTS, GPU_TOO_BIG, PCIE_SLOTS, M2_SLOTS, BAYS,
    PSU_SLOTS, PSU_TOO_BIG, PSU_WEAK, COOLER_TOO_BIG, REMOVE_DEPENDENTS, NO_SPACE, NOT_FOUND,
    NO_MOBO, NO_CPU, NO_RAM, NO_PSU, NO_COOLER;

    public int code() {
        return ordinal() + 1;
    }

    public static HwError fromCode(int code) {
        return code <= 0 || code > values().length ? null : values()[code - 1];
    }

    public String key() {
        return "hw.err." + name().toLowerCase();
    }

    public record Problem(HwError error, int arg) {
    }
}
