package com.crudecompute.hardware;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One purchasable hardware component. Only the fields relevant for its {@link Kind} are used. */
public final class Part {
    public final String id;
    public final Kind kind;
    public final String name;
    public final int price;
    public String socket = "";
    public String memType = "";
    public String type = "";
    public int cores;
    public int cpuSockets;
    public int ramSlots;
    public int pcie;
    public int m2;
    public int minU = 1;
    public int capacityGB;
    public int mhz;
    public int vramGB;
    public int ratedW;
    public int maxRpm;
    public int speedMBs;
    public double ghz;
    public double watts;
    public double perf;
    public double eff = 0.9;
    public double gbps;
    public double coolW;

    Part(String id, Kind kind, String name, int price) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.price = price;
    }

    public boolean isNvme() {
        return kind == Kind.STORAGE && type.equals("NVME");
    }

    /** Short technical data sheet, language neutral. */
    public List<String> specLines() {
        List<String> l = new ArrayList<>();
        switch (kind) {
            case CPU -> {
                l.add(cores + " cores @ " + fmt(ghz) + " GHz");
                l.add("Socket " + socket + " | TDP " + (int) watts + " W");
            }
            case MOTHERBOARD -> {
                l.add("Socket " + socket + " x" + cpuSockets + " | " + memType + " x" + ramSlots + " (max " + mhz + " MT/s)");
                l.add("PCIe x" + pcie + " | M.2/U.2 x" + m2 + " | min. " + minU + "U");
            }
            case RAM -> {
                l.add(capacityGB + " GB " + memType + "-" + mhz);
                l.add((int) watts + " W");
            }
            case GPU -> {
                l.add(vramGB + " GB VRAM | " + fmt(perf) + " PFLOPS");
                l.add("TDP " + (int) watts + " W | min. " + minU + "U chassis");
            }
            case STORAGE -> {
                l.add(type + " " + (capacityGB >= 1000 ? fmt(capacityGB / 1000.0) + " TB" : capacityGB + " GB"));
                l.add(speedMBs + " MB/s | " + (int) watts + " W");
            }
            case NETWORK -> l.add(fmt(gbps) + " Gbit/s | " + (int) watts + " W");
            case PSU -> {
                l.add(ratedW + " W | efficiency " + Math.round(eff * 100) + " %");
                l.add("min. " + minU + "U chassis");
            }
            case COOLER -> {
                l.add(type + " | removes " + (int) coolW + " W @ 40 K");
                l.add("up to " + maxRpm + " rpm | " + (int) watts + " W | min. " + minU + "U");
            }
        }
        l.add("$" + String.format(Locale.ROOT, "%,d", price));
        return l;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.005 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.2f", v);
    }
}
