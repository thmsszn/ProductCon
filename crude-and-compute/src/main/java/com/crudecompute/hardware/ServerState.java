package com.crudecompute.hardware;

import net.minecraft.nbt.CompoundTag;

/** Runtime state of one installed server (changes every second, never stored on the item). */
public final class ServerState {
    public static final int OFF = 0;
    public static final int ONLINE = 1;
    public static final int THROTTLED = 2;
    public static final int OVERHEATED = 3;
    public static final int NO_POWER = 4;
    public static final int NO_BOOT = 5;

    public float temp = 22f;
    public float fanDuty = 0.2f;
    public boolean overheated;
    public int status = OFF;
    public int bootReason;
    public float cpuUtil;
    public float gpuUtil;
    public float ramUtil;
    public float storageUsedGB;
    public float loadW;
    public float inputW;
    public float compute;
    public float psuEff = 0.9f;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Temp", temp);
        t.putFloat("Fan", fanDuty);
        t.putBoolean("Hot", overheated);
        t.putInt("Status", status);
        t.putInt("Boot", bootReason);
        t.putFloat("Cpu", cpuUtil);
        t.putFloat("Gpu", gpuUtil);
        t.putFloat("Ram", ramUtil);
        t.putFloat("Used", storageUsedGB);
        t.putFloat("Load", loadW);
        t.putFloat("In", inputW);
        t.putFloat("Compute", compute);
        t.putFloat("Eff", psuEff);
        return t;
    }

    public static ServerState load(CompoundTag t) {
        ServerState s = new ServerState();
        s.temp = t.contains("Temp") ? t.getFloat("Temp") : 22f;
        s.fanDuty = t.getFloat("Fan");
        s.overheated = t.getBoolean("Hot");
        s.status = t.getInt("Status");
        s.bootReason = t.getInt("Boot");
        s.cpuUtil = t.getFloat("Cpu");
        s.gpuUtil = t.getFloat("Gpu");
        s.ramUtil = t.getFloat("Ram");
        s.storageUsedGB = t.getFloat("Used");
        s.loadW = t.getFloat("Load");
        s.inputW = t.getFloat("In");
        s.compute = t.getFloat("Compute");
        s.psuEff = t.contains("Eff") ? t.getFloat("Eff") : 0.9f;
        return s;
    }
}
