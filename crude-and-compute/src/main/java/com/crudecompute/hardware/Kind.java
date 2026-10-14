package com.crudecompute.hardware;

public enum Kind {
    MOTHERBOARD, CPU, RAM, GPU, STORAGE, NETWORK, PSU, COOLER;

    public String key() {
        return "hw.kind." + name().toLowerCase();
    }
}
