package com.crudecompute.hardware;

/**
 * Thermal + electrical model of one server. Heat equals electrical load; the chassis loses heat to the rack inlet air
 * through a conductance G = passive + fans (cooler capacity / 40 K * fan duty). Above 85 °C the server throttles, at
 * 100 °C it shuts down until it has cooled below 65 °C.
 */
public final class HardwareSim {
    public static final float THROTTLE_C = 85f;
    public static final float SHUTDOWN_C = 100f;
    public static final float RESTART_C = 65f;
    /** 1 FE per tick corresponds to this many watts. */
    public static final double WATTS_PER_FE_TICK = 10.0;

    private HardwareSim() {
    }

    public static double passiveG(int sizeU) {
        return 2.5 + 2.0 * sizeU;
    }

    public static double coolerG(ServerConfig c, float fanDuty) {
        Part cool = c.coolerPart();
        return cool == null ? 0 : cool.coolW / 40.0 * fanDuty;
    }

    public static int fanRpm(ServerConfig c, ServerState s) {
        Part cool = c.coolerPart();
        return cool == null ? 0 : Math.round(cool.maxRpm * s.fanDuty);
    }

    /** Electrical input this server would request while powered, ignoring temperature effects (used to detect brown-outs). */
    public static double nominalInputW(ServerConfig c) {
        Part mb = c.moboPart();
        double load;
        if (c.mode == ServerConfig.MODE_OFF) {
            load = c.psus.isEmpty() ? 0 : 8;
        } else if (c.bootProblem() != null) {
            load = mb != null ? mb.watts * 0.5 : 10;
        } else {
            double cpuU = c.mode == ServerConfig.MODE_FULL ? 0.95 : c.mode == ServerConfig.MODE_NORMAL ? 0.45 : 0.05;
            double gpuU = c.mode == ServerConfig.MODE_FULL ? 1.0 : c.mode == ServerConfig.MODE_NORMAL ? 0.6 : 0.02;
            Part cool = c.coolerPart();
            load = c.sum(Kind.CPU, p -> p.watts) * (0.2 + 0.8 * cpuU) + c.sum(Kind.GPU, p -> p.watts) * (0.06 + 0.94 * gpuU)
                    + (mb != null ? mb.watts : 0) + c.sum(Kind.RAM, p -> p.watts) + c.sum(Kind.NETWORK, p -> p.watts)
                    + c.sum(Kind.STORAGE, p -> p.watts) * (0.4 + 0.6 * cpuU) + (cool != null ? cool.watts * 0.3 : 0);
        }
        return load / c.psuEfficiencyAt(load);
    }

    /** Advances the server by {@code dt} seconds. {@code powered} is false during a rack-wide power failure. */
    public static void step(ServerConfig c, ServerState s, double inletC, boolean powered, double dt) {
        int sizeU = c.sizeU;
        double cth = 300.0 * sizeU;
        Part mb = c.moboPart();
        HwError boot = c.bootProblem();
        boolean standbyPower = powered && !c.psus.isEmpty();

        s.cpuUtil = 0;
        s.gpuUtil = 0;
        s.ramUtil = 0;
        s.compute = 0;
        double load;
        double fanTarget = 0.1;

        if (c.mode == ServerConfig.MODE_OFF || !powered) {
            s.status = c.mode == ServerConfig.MODE_OFF ? ServerState.OFF : ServerState.NO_POWER;
            load = c.mode == ServerConfig.MODE_OFF && standbyPower ? 8 : 0;
        } else if (boot != null) {
            s.status = ServerState.NO_BOOT;
            s.bootReason = boot.code();
            load = mb != null ? mb.watts * 0.5 : 10;
        } else if (s.overheated && s.temp > RESTART_C) {
            s.status = ServerState.OVERHEATED;
            load = 15;
            fanTarget = 1.0;
        } else {
            s.overheated = false;
            float f = s.temp <= THROTTLE_C ? 1f : Math.max(0.3f, 1f - (s.temp - THROTTLE_C) / (SHUTDOWN_C - THROTTLE_C) * 0.7f);
            s.status = f < 1f ? ServerState.THROTTLED : ServerState.ONLINE;
            float cpuBase;
            float gpuBase;
            float ramBase;
            switch (c.mode) {
                case ServerConfig.MODE_IDLE -> {
                    cpuBase = 0.05f;
                    gpuBase = 0.02f;
                    ramBase = 0.08f;
                }
                case ServerConfig.MODE_FULL -> {
                    cpuBase = 0.95f;
                    gpuBase = 1.0f;
                    ramBase = 0.92f;
                }
                default -> {
                    cpuBase = 0.45f;
                    gpuBase = 0.6f;
                    ramBase = 0.55f;
                }
            }
            s.cpuUtil = cpuBase * f;
            s.gpuUtil = c.gpus.isEmpty() ? 0 : gpuBase * f;
            s.ramUtil = ramBase;
            double cpuW = c.sum(Kind.CPU, p -> p.watts) * (0.2 + 0.8 * s.cpuUtil);
            double gpuW = c.sum(Kind.GPU, p -> p.watts) * (0.06 + 0.94 * s.gpuUtil);
            double otherW = (mb != null ? mb.watts : 0) + c.sum(Kind.RAM, p -> p.watts) + c.sum(Kind.NETWORK, p -> p.watts)
                    + c.sum(Kind.STORAGE, p -> p.watts) * (0.4 + 0.6 * s.cpuUtil);
            fanTarget = Math.max(0.2, Math.min(1.0, (s.temp - 45.0) / 35.0));
            s.fanDuty += (fanTarget - s.fanDuty) * 0.3f;
            Part cool = c.coolerPart();
            double fanW = cool == null ? 0 : cool.watts * (0.05 + 0.95 * Math.pow(s.fanDuty, 3));
            load = cpuW + gpuW + otherW + fanW;
            s.compute = (float) (c.sum(Kind.GPU, p -> p.perf) * s.gpuUtil + c.sum(Kind.CPU, p -> p.cores * p.ghz / 1000.0) * s.cpuUtil);
            if (c.mode >= ServerConfig.MODE_NORMAL) {
                s.storageUsedGB = Math.min(c.storageGB(), s.storageUsedGB + c.storageGB() * 0.00004f * (c.mode == ServerConfig.MODE_FULL ? 2f : 1f));
            }
        }
        if (s.status != ServerState.ONLINE && s.status != ServerState.THROTTLED) {
            s.fanDuty += (fanTarget - s.fanDuty) * 0.3f;
        }
        s.loadW = (float) load;
        s.psuEff = (float) c.psuEfficiencyAt(load);
        s.inputW = (float) (load / s.psuEff);

        double g = passiveG(sizeU) + coolerG(c, s.fanDuty);
        s.temp += (float) ((load - g * (s.temp - inletC)) / cth * dt);
        if (s.temp < (float) inletC - 5f) {
            s.temp = (float) inletC - 5f;
        }
        if (s.temp >= SHUTDOWN_C && (s.status == ServerState.ONLINE || s.status == ServerState.THROTTLED)) {
            s.overheated = true;
            s.status = ServerState.OVERHEATED;
        }
    }
}
