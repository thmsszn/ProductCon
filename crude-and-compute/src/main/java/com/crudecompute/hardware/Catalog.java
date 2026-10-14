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
        // rack boards
        mobo("mb_1u_entry", "ASRock Rack W680D4U (1U)", "LGA1700", "DDR4", 1, 4, 1, 2, 1, 3200, 30, 320);
        mobo("mb_2u_ws", "ASUS Pro WS W680-ACE (2U)", "LGA1700", "DDR5", 1, 4, 3, 3, 2, 5600, 38, 520);
        mobo("mb_1u_epyc", "Supermicro H13SSL-N (1U)", "SP5", "DDR5", 1, 12, 3, 2, 1, 4800, 45, 900);
        mobo("mb_2u_epyc", "Gigabyte MZ73-LM0 (2U, 2P)", "SP5", "DDR5", 2, 24, 5, 2, 2, 4800, 55, 2100);
        mobo("mb_2u_xeon", "Supermicro X13DEM (2U, 2P)", "LGA4677", "DDR5", 2, 16, 6, 4, 2, 4800, 50, 1600);
        mobo("mb_1u_xeon", "Supermicro X13DDW-A (1U, 2P)", "LGA4677", "DDR5", 2, 16, 2, 2, 1, 4800, 48, 1100);
        mobo("mb_4u_gpu", "ASUS ESC8000A-E12 (4U GPU)", "SP5", "DDR5", 2, 24, 10, 4, 4, 5200, 70, 3600);
        // enthusiast boards (ATX needs a 4U chassis, mATX fits 2U)
        mobo("mb_asus_x670e", "ASUS ROG Crosshair X670E Hero", "AM5", "DDR5", 1, 4, 3, 4, 4, 6400, 55, 700);
        mobo("mb_asus_z790", "ASUS ROG Strix Z790-E Gaming", "LGA1700", "DDR5", 1, 4, 3, 5, 4, 7200, 50, 500);
        mobo("mb_asus_b650", "ASUS ROG Strix B650E-F Gaming", "AM5", "DDR5", 1, 4, 2, 3, 4, 6400, 45, 330);
        mobo("mb_msi_x670e", "MSI MEG X670E ACE", "AM5", "DDR5", 1, 4, 3, 4, 4, 6600, 55, 700);
        mobo("mb_msi_z790", "MSI MPG Z790 Carbon WiFi", "LGA1700", "DDR5", 1, 4, 3, 4, 4, 7200, 50, 400);
        mobo("mb_msi_b650", "MSI MAG B650 Tomahawk WiFi", "AM5", "DDR5", 1, 4, 2, 3, 4, 6400, 42, 220);
        mobo("mb_msi_b760m", "MSI MAG B760M Mortar (mATX)", "LGA1700", "DDR4", 1, 4, 2, 2, 2, 3600, 32, 160);
        mobo("mb_asus_trx", "ASUS Pro WS WRX90E-SAGE SE", "sTR5", "DDR5", 1, 8, 7, 4, 4, 5600, 65, 1300);
        // ---- CPUs: id, name, socket, cores, GHz, TDP, price
        cpu("cpu_e2388", "Intel Xeon E-2388G", "LGA1700", 8, 3.2, 95, 450);
        cpu("cpu_i9", "Intel Core i9-14900K", "LGA1700", 24, 5.8, 125, 590);
        cpu("cpu_i7_14700k", "Intel Core i7-14700K", "LGA1700", 20, 5.6, 125, 410);
        cpu("cpu_i5_14600k", "Intel Core i5-14600K", "LGA1700", 14, 5.3, 125, 320);
        cpu("cpu_xeon_g", "Intel Xeon Gold 6430", "LGA4677", 32, 2.1, 270, 2100);
        cpu("cpu_xeon_p", "Intel Xeon Platinum 8480+", "LGA4677", 56, 2.0, 350, 10700);
        cpu("cpu_epyc32", "AMD EPYC 9354", "SP5", 32, 3.25, 280, 3400);
        cpu("cpu_epyc96", "AMD EPYC 9654", "SP5", 96, 2.4, 360, 11800);
        cpu("cpu_r5_7600x", "AMD Ryzen 5 7600X", "AM5", 6, 4.7, 105, 230);
        cpu("cpu_r7_7800x3d", "AMD Ryzen 7 7800X3D", "AM5", 8, 4.2, 120, 370);
        cpu("cpu_r9_7950x", "AMD Ryzen 9 7950X", "AM5", 16, 4.5, 170, 550);
        cpu("cpu_tr_7970x", "AMD Ryzen Threadripper 7970X", "sTR5", 32, 4.0, 350, 2500);
        cpu("cpu_tr_7995wx", "AMD Ryzen Threadripper PRO 7995WX", "sTR5", 96, 2.5, 350, 9900);
        // ---- RAM: id, name, type, GB, MT/s, watts, price
        ram("ram_d4_16", "Corsair Vengeance LPX 16GB DDR4-3200", "DDR4", 16, 3200, 3, 60);
        ram("ram_d4_32", "Corsair Vengeance LPX 32GB DDR4-3200", "DDR4", 32, 3200, 5, 110);
        ram("ram_c_d4_rgb", "Corsair Vengeance RGB Pro 16GB DDR4-3600", "DDR4", 16, 3600, 3, 75);
        ram("ram_d5_32", "Corsair Vengeance 32GB DDR5-4800", "DDR5", 32, 4800, 5, 140);
        ram("ram_c_d5_16", "Corsair Vengeance 16GB DDR5-6000", "DDR5", 16, 6000, 4, 90);
        ram("ram_c_d5_rgb", "Corsair Vengeance RGB 32GB DDR5-6000", "DDR5", 32, 6000, 5, 150);
        ram("ram_c_dom", "Corsair Dominator Platinum 32GB DDR5-6400", "DDR5", 32, 6400, 6, 190);
        ram("ram_c_titan", "Corsair Dominator Titanium 48GB DDR5-7200", "DDR5", 48, 7200, 8, 290);
        ram("ram_d5_64", "Samsung 64GB DDR5-5600 RDIMM", "DDR5", 64, 5600, 6, 280);
        ram("ram_d5_128", "Micron 128GB DDR5-4800 RDIMM", "DDR5", 128, 4800, 9, 780);
        // ---- GPUs / accelerators: id, name, VRAM, PFLOPS, TDP, min U, price
        gpu("gpu_lp24", "NVIDIA L4 24GB", 24, 0.5, 72, 1, 2200);
        gpu("gpu_4060", "NVIDIA GeForce RTX 4060 8GB", 8, 0.15, 115, 2, 300);
        gpu("gpu_4070s", "NVIDIA GeForce RTX 4070 Super 12GB", 12, 0.25, 220, 2, 600);
        gpu("gpu_g16", "NVIDIA GeForce RTX 4080 Super 16GB", 16, 0.4, 320, 2, 1000);
        gpu("gpu_4090", "NVIDIA GeForce RTX 4090 24GB", 24, 0.65, 450, 4, 1800);
        gpu("gpu_rx7900", "AMD Radeon RX 7900 XTX 24GB", 24, 0.55, 355, 2, 950);
        gpu("gpu_m48", "NVIDIA RTX 6000 Ada 48GB", 48, 1.2, 300, 2, 6800);
        gpu("gpu_l40s", "NVIDIA L40S 48GB", 48, 1.0, 350, 2, 8000);
        gpu("gpu_h80", "NVIDIA H100 NVL 80GB", 80, 2.5, 400, 2, 22000);
        gpu("gpu_mi300", "AMD Instinct MI300X 192GB", 192, 5.2, 750, 4, 15000);
        gpu("gpu_x141", "NVIDIA H200 SXM 141GB", 141, 4.5, 700, 4, 36000);
        gpu("gpu_b200", "NVIDIA B200 192GB", 192, 9.0, 1000, 4, 40000);
        // ---- storage: id, name, type, GB, MB/s, watts, price
        storage("hdd_8", "WD Red Pro 8TB", "HDD", 8000, 250, 8, 190);
        storage("hdd_20", "Seagate Exos X20 20TB", "HDD", 20000, 270, 9, 420);
        storage("ssd_1", "Samsung 870 EVO 1TB", "SSD", 1000, 550, 3, 90);
        storage("ssd_4", "Samsung 870 EVO 4TB", "SSD", 4000, 540, 5, 380);
        storage("nvme_2", "Samsung 990 Pro 2TB", "NVME", 2000, 7000, 7, 180);
        storage("nvme_c_mp700", "Corsair MP700 PRO 2TB (Gen5)", "NVME", 2000, 12000, 10, 250);
        storage("nvme_c_mp600", "Corsair MP600 PRO XT 4TB", "NVME", 4000, 7100, 9, 350);
        storage("nvme_8", "Kioxia CD8P 7.68TB U.2", "NVME", 7680, 6800, 12, 900);
        // ---- network: id, name, Gbit/s, watts, price
        nic("nic_1", "Intel I350-T2 1GbE", 1, 2, 20);
        nic("nic_10", "Intel X710-DA2 10GbE", 10, 6, 120);
        nic("nic_25", "NVIDIA ConnectX-6 Lx 25GbE", 25, 9, 260);
        nic("nic_100", "NVIDIA ConnectX-6 Dx 100GbE", 100, 17, 900);
        nic("nic_400", "NVIDIA ConnectX-7 NDR 400G", 400, 25, 2200);
        // ---- PSUs: id, name, rated W, efficiency, min U, price
        psu("psu_450", "Corsair CX450 80+ Bronze", 450, 0.85, 1, 80);
        psu("psu_sf750", "Corsair SF750 80+ Platinum (SFX)", 750, 0.92, 1, 170);
        psu("psu_800", "Corsair RM850e 80+ Gold", 850, 0.90, 1, 190);
        psu("psu_rm1000", "Corsair RM1000x 80+ Gold", 1000, 0.90, 2, 210);
        psu("psu_hx1500", "Corsair HX1500i 80+ Platinum", 1500, 0.92, 2, 380);
        psu("psu_1600", "Corsair AX1600i 80+ Titanium", 1600, 0.94, 1, 520);
        psu("psu_2400", "Supermicro PWS-2K40 80+ Titanium", 2400, 0.96, 2, 780);
        psu("psu_3000", "Delta DPS-3000 80+ Titanium", 3000, 0.96, 4, 1100);
        // ---- coolers: id, name, type, removable W @ 40 K, own watts, max rpm, min U, price
        cooler("cool_air1", "Dynatron A26 1U Active Heatsink", "AIR", 400, 25, 12000, 1, 120);
        cooler("cool_air2", "Noctua NH-U9 TR5 2U Cooler", "AIR", 900, 60, 9000, 2, 320);
        cooler("cool_air4", "Noctua NH-D15 4U Tower", "AIR", 1600, 110, 7000, 4, 640);
        cooler("cool_aio", "Corsair iCUE H100i Elite 240mm", "LIQUID", 2200, 45, 2800, 2, 170);
        cooler("cool_aio360", "Corsair iCUE H150i Elite LCD XT 360mm", "LIQUID", 3000, 60, 2800, 4, 290);
        cooler("cool_dlc", "CoolIT Direct Liquid Cooling + CDU", "LIQUID", 4500, 120, 4200, 4, 2400);
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
