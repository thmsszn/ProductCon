package com.crudecompute.hardware;

import com.crudecompute.hardware.HwError.Problem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** The hardware configuration of one server (chassis size + installed parts) with full compatibility validation. */
public final class ServerConfig {
    public static final int MODE_OFF = 0;
    public static final int MODE_IDLE = 1;
    public static final int MODE_NORMAL = 2;
    public static final int MODE_FULL = 3;

    public int sizeU;
    public String mobo = "";
    public String cooler = "";
    public final List<String> cpus = new ArrayList<>();
    public final List<String> rams = new ArrayList<>();
    public final List<String> gpus = new ArrayList<>();
    public final List<String> storage = new ArrayList<>();
    public final List<String> nics = new ArrayList<>();
    public final List<String> psus = new ArrayList<>();
    public int mode = MODE_NORMAL;

    public ServerConfig(int sizeU) {
        this.sizeU = sizeU;
    }

    public ServerConfig copy() {
        return load(save());
    }

    // ------------------------------------------------------------------ persistence
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("U", sizeU);
        t.putString("Mobo", mobo);
        t.putString("Cooler", cooler);
        t.putInt("Mode", mode);
        t.put("Cpus", strings(cpus));
        t.put("Rams", strings(rams));
        t.put("Gpus", strings(gpus));
        t.put("Storage", strings(storage));
        t.put("Nics", strings(nics));
        t.put("Psus", strings(psus));
        return t;
    }

    public static ServerConfig load(CompoundTag t) {
        ServerConfig c = new ServerConfig(Math.max(1, t.getInt("U")));
        c.mobo = t.getString("Mobo");
        c.cooler = t.getString("Cooler");
        c.mode = t.contains("Mode") ? t.getInt("Mode") : MODE_NORMAL;
        read(t.getList("Cpus", Tag.TAG_STRING), c.cpus);
        read(t.getList("Rams", Tag.TAG_STRING), c.rams);
        read(t.getList("Gpus", Tag.TAG_STRING), c.gpus);
        read(t.getList("Storage", Tag.TAG_STRING), c.storage);
        read(t.getList("Nics", Tag.TAG_STRING), c.nics);
        read(t.getList("Psus", Tag.TAG_STRING), c.psus);
        return c;
    }

    private static ListTag strings(List<String> l) {
        ListTag out = new ListTag();
        for (String s : l) {
            out.add(StringTag.valueOf(s));
        }
        return out;
    }

    private static void read(ListTag in, List<String> out) {
        for (int i = 0; i < in.size(); i++) {
            if (Catalog.get(in.getString(i)) != null) {
                out.add(in.getString(i));
            }
        }
    }

    // ------------------------------------------------------------------ access
    public List<String> list(Kind kind) {
        return switch (kind) {
            case CPU -> cpus;
            case RAM -> rams;
            case GPU -> gpus;
            case STORAGE -> storage;
            case NETWORK -> nics;
            case PSU -> psus;
            default -> new ArrayList<>();
        };
    }

    @Nullable
    public Part moboPart() {
        return Catalog.get(mobo);
    }

    @Nullable
    public Part coolerPart() {
        return Catalog.get(cooler);
    }

    public List<Part> parts(Kind kind) {
        List<Part> out = new ArrayList<>();
        if (kind == Kind.MOTHERBOARD) {
            if (moboPart() != null) {
                out.add(moboPart());
            }
        } else if (kind == Kind.COOLER) {
            if (coolerPart() != null) {
                out.add(coolerPart());
            }
        } else {
            for (String id : list(kind)) {
                out.add(Catalog.get(id));
            }
        }
        return out;
    }

    public int sataBays() {
        return sizeU == 1 ? 4 : sizeU == 2 ? 12 : 24;
    }

    public int count(Kind kind) {
        return parts(kind).size();
    }

    public double sum(Kind kind, java.util.function.ToDoubleFunction<Part> f) {
        double s = 0;
        for (Part p : parts(kind)) {
            s += f.applyAsDouble(p);
        }
        return s;
    }

    public int ramGB() {
        return (int) sum(Kind.RAM, p -> p.capacityGB);
    }

    public int storageGB() {
        return (int) sum(Kind.STORAGE, p -> p.capacityGB);
    }

    public int psuRatedW() {
        return (int) sum(Kind.PSU, p -> p.ratedW);
    }

    public double psuEfficiency() {
        int n = psus.size();
        return n == 0 ? 0.8 : sum(Kind.PSU, p -> p.eff) / n;
    }

    public int totalPrice() {
        int p = moboPart() != null ? moboPart().price : 0;
        p += coolerPart() != null ? coolerPart().price : 0;
        for (Kind k : new Kind[]{Kind.CPU, Kind.RAM, Kind.GPU, Kind.STORAGE, Kind.NETWORK, Kind.PSU}) {
            p += (int) sum(k, x -> x.price);
        }
        return p;
    }

    /** Worst-case electrical load in watts (everything at full tilt, fans at 100 %). */
    public double peakW() {
        double w = moboPart() != null ? moboPart().watts : 0;
        w += sum(Kind.CPU, p -> p.watts) + sum(Kind.GPU, p -> p.watts) + sum(Kind.RAM, p -> p.watts)
                + sum(Kind.STORAGE, p -> p.watts) + sum(Kind.NETWORK, p -> p.watts);
        w += coolerPart() != null ? coolerPart().watts : 0;
        return w;
    }

    public boolean isEmpty() {
        return mobo.isEmpty() && cooler.isEmpty() && cpus.isEmpty() && rams.isEmpty() && gpus.isEmpty() && storage.isEmpty()
                && nics.isEmpty() && psus.isEmpty();
    }

    /** Why this server cannot boot, or null if it can. */
    @Nullable
    public HwError bootProblem() {
        if (moboPart() == null) {
            return HwError.NO_MOBO;
        }
        if (cpus.isEmpty()) {
            return HwError.NO_CPU;
        }
        if (rams.isEmpty()) {
            return HwError.NO_RAM;
        }
        if (psus.isEmpty()) {
            return HwError.NO_PSU;
        }
        if (coolerPart() == null) {
            return HwError.NO_COOLER;
        }
        return null;
    }

    // ------------------------------------------------------------------ compatibility
    /** Checks the whole configuration. Returns the first hard incompatibility or null. */
    @Nullable
    public Problem validate() {
        Part mb = moboPart();
        if (mb == null) {
            if (!cpus.isEmpty() || !rams.isEmpty() || !gpus.isEmpty() || !storage.isEmpty() || !nics.isEmpty()) {
                return new Problem(HwError.NEEDS_MOBO, 0);
            }
        } else {
            if (mb.minU > sizeU) {
                return new Problem(HwError.MOBO_TOO_LARGE, mb.minU);
            }
            for (Part c : parts(Kind.CPU)) {
                if (!c.socket.equals(mb.socket)) {
                    return new Problem(HwError.CPU_SOCKET, 0);
                }
            }
            if (cpus.size() > mb.cpuSockets) {
                return new Problem(HwError.CPU_SLOTS, mb.cpuSockets);
            }
            for (Part r : parts(Kind.RAM)) {
                if (!r.memType.equals(mb.memType)) {
                    return new Problem(HwError.RAM_TYPE, 0);
                }
            }
            if (rams.size() > mb.ramSlots) {
                return new Problem(HwError.RAM_SLOTS, mb.ramSlots);
            }
            for (Part g : parts(Kind.GPU)) {
                if (g.minU > sizeU) {
                    return new Problem(HwError.GPU_TOO_BIG, g.minU);
                }
            }
            if (gpus.size() + nics.size() > mb.pcie) {
                return new Problem(HwError.PCIE_SLOTS, mb.pcie);
            }
            int nvme = 0;
            int sata = 0;
            for (Part s : parts(Kind.STORAGE)) {
                if (s.isNvme()) {
                    nvme++;
                } else {
                    sata++;
                }
            }
            if (nvme > mb.m2) {
                return new Problem(HwError.M2_SLOTS, mb.m2);
            }
            if (sata > sataBays()) {
                return new Problem(HwError.BAYS, sataBays());
            }
        }
        if (psus.size() > 2) {
            return new Problem(HwError.PSU_SLOTS, 2);
        }
        for (Part p : parts(Kind.PSU)) {
            if (p.minU > sizeU) {
                return new Problem(HwError.PSU_TOO_BIG, p.minU);
            }
        }
        if (coolerPart() != null && coolerPart().minU > sizeU) {
            return new Problem(HwError.COOLER_TOO_BIG, coolerPart().minU);
        }
        if (!psus.isEmpty() && peakW() > psuRatedW()) {
            return new Problem(HwError.PSU_WEAK, (int) Math.ceil(peakW()));
        }
        return null;
    }

    /** Installs the part if everything stays compatible. Mainboards and coolers replace the current one. */
    @Nullable
    public Problem install(Part part) {
        ServerConfig trial = copy();
        switch (part.kind) {
            case MOTHERBOARD -> trial.mobo = part.id;
            case COOLER -> trial.cooler = part.id;
            default -> trial.list(part.kind).add(part.id);
        }
        Problem problem = trial.validate();
        if (problem != null) {
            return problem;
        }
        apply(trial);
        return null;
    }

    /** Removes one part; mainboards can only be removed when nothing depends on them. */
    @Nullable
    public Problem remove(Kind kind, int index) {
        ServerConfig trial = copy();
        if (kind == Kind.MOTHERBOARD) {
            trial.mobo = "";
        } else if (kind == Kind.COOLER) {
            trial.cooler = "";
        } else {
            List<String> l = trial.list(kind);
            if (index < 0 || index >= l.size()) {
                return new Problem(HwError.NOT_FOUND, 0);
            }
            l.remove(index);
        }
        Problem problem = trial.validate();
        if (problem != null) {
            return new Problem(HwError.REMOVE_DEPENDENTS, 0);
        }
        apply(trial);
        return null;
    }

    private void apply(ServerConfig o) {
        sizeU = o.sizeU;
        mobo = o.mobo;
        cooler = o.cooler;
        mode = o.mode;
        for (Kind k : new Kind[]{Kind.CPU, Kind.RAM, Kind.GPU, Kind.STORAGE, Kind.NETWORK, Kind.PSU}) {
            list(k).clear();
            list(k).addAll(o.list(k));
        }
    }

    // ------------------------------------------------------------------ factory presets
    public static ServerConfig preset(int sizeU) {
        ServerConfig c = new ServerConfig(sizeU);
        switch (sizeU) {
            case 1 -> {
                c.mobo = "mb_1u_epyc";
                c.cpus.add("cpu_epyc32");
                for (int i = 0; i < 4; i++) {
                    c.rams.add("ram_d5_64");
                }
                c.storage.add("nvme_2");
                c.storage.add("nvme_2");
                c.nics.add("nic_25");
                c.psus.add("psu_800");
                c.cooler = "cool_air1";
            }
            case 2 -> {
                c.mobo = "mb_2u_xeon";
                c.cpus.add("cpu_xeon_g");
                c.cpus.add("cpu_xeon_g");
                for (int i = 0; i < 8; i++) {
                    c.rams.add("ram_d5_128");
                }
                c.storage.add("nvme_8");
                c.storage.add("nvme_8");
                c.gpus.add("gpu_m48");
                c.gpus.add("gpu_m48");
                c.nics.add("nic_100");
                c.psus.add("psu_1600");
                c.psus.add("psu_1600");
                c.cooler = "cool_air2";
            }
            default -> {
                c.mobo = "mb_4u_gpu";
                c.cpus.add("cpu_epyc96");
                c.cpus.add("cpu_epyc96");
                for (int i = 0; i < 12; i++) {
                    c.rams.add("ram_d5_128");
                }
                for (int i = 0; i < 4; i++) {
                    c.storage.add("nvme_8");
                }
                for (int i = 0; i < 8; i++) {
                    c.gpus.add("gpu_h80");
                }
                c.nics.add("nic_400");
                c.nics.add("nic_400");
                c.psus.add("psu_3000");
                c.psus.add("psu_3000");
                c.cooler = "cool_dlc";
            }
        }
        return c;
    }
}
