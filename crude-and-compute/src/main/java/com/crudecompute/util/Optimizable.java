package com.crudecompute.util;

/** Machines that can be upgraded with an AI optimization chip (double output). */
public interface Optimizable {
    boolean isOptimized();

    void setOptimized();

    default float boost() {
        return isOptimized() ? 2f : 1f;
    }
}
