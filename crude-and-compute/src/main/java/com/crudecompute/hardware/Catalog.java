package com.crudecompute.hardware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All parts that can be installed. Indices into {@link #ALL} are stable and used by the GUI. */
public final class Catalog {
    public static final List<Part> ALL = new ArrayList<>();
    public static final Map<String, Part> BY_ID = new HashMap<>();
    public static final Map<Kind, List<Part>> BY_KIND = new LinkedHashMap<>();

    static {
        for (Kind k : Kind.values()) {
            BY_KIND.put(k, new ArrayList<>());
        }
        // ---- mainboards: id, name, socket, memory, cpu sockets, dimm slots, pcie, m2, min U, max MT/s, watts, price
        mobo("mb_1u_entry", "Rack Board E3 (1U, entry)", "LGA1700", "DDR4", 1, 4, 1, 2, 1, 3200, 30, 320);
        mobo("mb_2u_ws", "Rack Board W680 (2U)", "LGA1700", "DDR5", 1, 4, 3, 3, 2, 5600, 38, 520);
        mobo("mb_1u_epyc", "Rack Board SP5-1P (1U)", "SP5", "DDR5", 1, 12, 3, 2, 1, 4800, 45, 900);
        mobo("mb_2u_epyc", "Rack Board SP5-2P (2U)", "SP5", "DDR5", 2, 24, 5, 2, 2, 4800, 55, 2100);
        mobo("mb_2u_xeon", "Rack Board 4677-2P (2U)", "LGA4677", "DDR5", 2, 16, 6, 4, 2, 4800, 50, 1600);
        mobo("mb_1u_xeon", "Rack Board 4677-2P (1U)", "LGA4677", "DDR5", 2, 16, 2, 2, 1, 4800, 48, 1100);
        mobo("mb_4u_gpu", "GPU Board SP5-2P (4U)", "SP5", "DDR5", 2, 24, 10, 4, 4, 5200, 70, 3600);
        // ---- CPUs: id, name, socket, cores, GHz, TDP, price
        cpu("cpu_e2388", "Entry E-2388 8C", "LGA1700", 8, 3.2, 95, 450);
        cpu("cpu_i9", "Workstation i9 24C", "LGA1700", 24, 5.2, 125, 640);
        cpu("cpu_xeon_g", "Xeon-class Gold 32C", "LGA4677", 32, 2.1, 270, 2100);
        cpu("cpu_xeon_p", "Xeon-class Platinum 56C", "LGA4677", 56, 2.0, 350, 10700);
        cpu("cpu_epyc32", "EPYC-class 32C", "SP5", 32, 3.25, 280, 3400);
        cpu("cpu_epyc96", "EPYC-class 96C", "SP5", 96, 2.4, 360, 11800);
        // ---- RAM: id, name, type, GB, MT/s, watts, price
        ram("ram_d4_16", "16 GB DDR4-3200", "DDR4", 16, 3200, 3, 60);
        ram("ram_d4_32", "32 GB DDR4-3200", "DDR4", 32, 3200, 5, 110);
        ram("ram_d5_32", "32 GB DDR5-4800", "DDR5", 32, 4800, 5, 140);
        ram("ram_d5_64", "64 GB DDR5-5600", "DDR5", 64, 5600, 6, 280);
        ram("ram_d5_128", "128 GB DDR5-4800", "DDR5", 128, 4800, 9, 780);
        // ---- GPUs / accelerators: id, name, VRAM, PFLOPS, TDP, min U, price
        gpu("gpu_lp24", "Compute LP-24 (low profile)", 24, 0.5, 72, 1, 2200);
        gpu("gpu_g16", "Graphics G-16 (consumer)", 16, 0.4, 220, 2, 900);
        gpu("gpu_m48", "Compute M-48", 48, 1.2, 300, 2, 6800);
        gpu("gpu_h80", "Compute H-80", 80, 2.5, 400, 2, 22000);
        gpu("gpu_x141", "Compute X-141 SXM", 141, 4.5, 700, 4, 36000);
        // ---- storage: id, name, type, GB, MB/s, watts, price
        storage("hdd_8", "8 TB HDD 7200 rpm", "HDD", 8000, 250, 8, 190);
        storage("hdd_20", "20 TB HDD 7200 rpm", "HDD", 20000, 270, 9, 420);
        storage("ssd_1", "1 TB SATA SSD", "SSD", 1000, 550, 3, 90);
        storage("ssd_4", "4 TB SATA SSD", "SSD", 4000, 540, 5, 380);
        storage("nvme_2", "2 TB NVMe Gen4", "NVME", 2000, 7000, 7, 180);
        storage("nvme_8", "7.68 TB NVMe Gen4 U.2", "NVME", 7680, 6800, 12, 900);
        // ---- network: id, name, Gbit/s, watts, price
        nic("nic_1", "1 GbE dual port", 1, 2, 20);
        nic("nic_10", "10 GbE dual port", 10, 6, 120);
        nic("nic_25", "25 GbE dual port", 25, 9, 260);
        nic("nic_100", "100 GbE QSFP28", 100, 17, 900);
        nic("nic_400", "400 Gbit/s NDR adapter", 400, 25, 2200);
        // ---- PSUs: id, name, rated W, efficiency, min U, price
        psu("psu_450", "450 W 80+ Bronze", 450, 0.85, 1, 80);
        psu("psu_800", "800 W 80+ Gold", 800, 0.90, 1, 190);
        psu("psu_1600", "1600 W 80+ Platinum", 1600, 0.92, 1, 420);
        psu("psu_2400", "2400 W 80+ Titanium", 2400, 0.96, 2, 780);
        psu("psu_3000", "3000 W 80+ Titanium", 3000, 0.96, 4, 1100);
        // ---- coolers: id, name, type, removable W @ 40 K, own watts, max rpm, min U, price
        cooler("cool_air1", "1U Heatsink + 8 fans (40 mm)", "AIR", 400, 25, 12000, 1, 120);
        cooler("cool_air2", "2U Heatsink + 6 fans (80 mm)", "AIR", 900, 60, 9000, 2, 320);
        cooler("cool_air4", "4U Tower + 8 fans (120 mm)", "AIR", 1600, 110, 7000, 4, 640);
        cooler("cool_aio", "Liquid loop 240 mm radiator", "LIQUID", 2200, 45, 2800, 2, 650);
        cooler("cool_dlc", "Direct-to-chip liquid + CDU", "LIQUID", 4500, 120, 4200, 4, 2400);
    }

    private Catalog() {
    }

    private static Part add(Part p) {
        ALL.add(p);
        BY_ID.put(p.id, p);
        BY_KIND.get(p.kind).add(p);
        return p;
    }

    private static void mobo(String id, String name, String socket, String mem, int cpus, int dimms, int pcie, int m2, int minU, int mhz, int w, int price) {
        Part p = add(new Part(id, Kind.MOTHERBOARD, name, price));
        p.socket = socket;
        p.memType = mem;
        p.cpuSockets = cpus;
        p.ramSlots = dimms;
        p.pcie = pcie;
        p.m2 = m2;
        p.minU = minU;
        p.mhz = mhz;
        p.watts = w;
    }

    private static void cpu(String id, String name, String socket, int cores, double ghz, int tdp, int price) {
        Part p = add(new Part(id, Kind.CPU, name, price));
        p.socket = socket;
        p.cores = cores;
        p.ghz = ghz;
        p.watts = tdp;
    }

    private static void ram(String id, String name, String type, int gb, int mhz, int w, int price) {
        Part p = add(new Part(id, Kind.RAM, name, price));
        p.memType = type;
        p.capacityGB = gb;
        p.mhz = mhz;
        p.watts = w;
    }

    private static void gpu(String id, String name, int vram, double perf, int tdp, int minU, int price) {
        Part p = add(new Part(id, Kind.GPU, name, price));
        p.vramGB = vram;
        p.perf = perf;
        p.watts = tdp;
        p.minU = minU;
    }

    private static void storage(String id, String name, String type, int gb, int mbs, int w, int price) {
        Part p = add(new Part(id, Kind.STORAGE, name, price));
        p.type = type;
        p.capacityGB = gb;
        p.speedMBs = mbs;
        p.watts = w;
    }

    private static void nic(String id, String name, double gbps, int w, int price) {
        Part p = add(new Part(id, Kind.NETWORK, name, price));
        p.gbps = gbps;
        p.watts = w;
    }

    private static void psu(String id, String name, int watts, double eff, int minU, int price) {
        Part p = add(new Part(id, Kind.PSU, name, price));
        p.ratedW = watts;
        p.eff = eff;
        p.minU = minU;
    }

    private static void cooler(String id, String name, String type, int coolW, int w, int rpm, int minU, int price) {
        Part p = add(new Part(id, Kind.COOLER, name, price));
        p.type = type;
        p.coolW = coolW;
        p.watts = w;
        p.maxRpm = rpm;
        p.minU = minU;
    }

    public static Part get(String id) {
        return BY_ID.get(id);
    }

    public static int indexOf(Part p) {
        return ALL.indexOf(p);
    }
}
