package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gemeinsame Werkzeuge aller Generatoren: Raster, Palette, Zufall und wiederverwendbare Bauteile
 * (Materialvariation, Möbel, Beleuchtung, Garten, Zaun, Weg, Bäume).
 */
public final class Site {
    public final Voxels v;
    public final Palette p;
    public final BuildingSpec spec;
    public final Rng rng;
    public final int w;
    public final int h;
    public final int d;
    private final Set<Long> reserved = new HashSet<>();

    public Site(BuildingSpec spec, Palette palette, int w, int h, int d) {
        this.spec = spec;
        this.p = palette;
        this.w = w;
        this.h = h;
        this.d = d;
        this.v = new Voxels(w, h, d);
        this.rng = new Rng(spec.seed());
    }

    // ------------------------------------------------------------------ Grundlagen

    /** Bauplatz vorbereiten: Bodenschicht (Gras/Sand), darüber alles frei. */
    public void clearSite(String ground) {
        v.fillAll(B.AIR);
        v.fill(0, 0, 0, w - 1, 0, d - 1, B.id(ground));
    }

    public void reserve(int x, int y, int z) {
        reserved.add(key(x, y, z));
    }

    public boolean isReserved(int x, int y, int z) {
        return reserved.contains(key(x, y, z));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
    }

    /** Frei für Möbel: leer und nicht reserviert. */
    public boolean free(int x, int y, int z) {
        return v.isEmpty(x, y, z) && !isReserved(x, y, z);
    }

    /** Leichte Materialvariation für einen natürlichen, gealterten Look. */
    public String vary(String block) {
        String n = B.name(block);
        double r = rng.nextDouble();
        return switch (n) {
            case "stone_bricks" -> r < 0.08 ? B.id("mossy_stone_bricks") : r < 0.15 ? B.id("cracked_stone_bricks") : B.id(block);
            case "cobblestone" -> r < 0.15 ? B.id("mossy_cobblestone") : B.id(block);
            case "deepslate_bricks" -> r < 0.12 ? B.id("cracked_deepslate_bricks") : B.id(block);
            case "bricks" -> r < 0.05 ? B.id("mud_bricks") : B.id(block);
            case "sandstone" -> r < 0.10 ? B.id("smooth_sandstone") : B.id(block);
            case "polished_blackstone_bricks" -> r < 0.12 ? B.id("cracked_polished_blackstone_bricks") : B.id(block);
            default -> B.id(block);
        };
    }

    // ------------------------------------------------------------------ Innenausstattung

    /** Rechteckiger Raum (Innenzellen) auf einer Ebene; {@code y} ist die Zellenhöhe über dem Fußboden. */
    public record Room(int x0, int z0, int x1, int z1, int y, int ceiling, String type) {
        int cx() { return (x0 + x1) / 2; }
        int cz() { return (z0 + z1) / 2; }
        int width() { return x1 - x0 + 1; }
        int depth() { return z1 - z0 + 1; }
    }

    /** Wandnahe Zellen eines Raums mit der Richtung zur Wand. */
    record WallSpot(int x, int z, Dir toWall) {}

    List<WallSpot> wallSpots(Room r) {
        List<WallSpot> spots = new ArrayList<>();
        for (int x = r.x0; x <= r.x1; x++) spots.add(new WallSpot(x, r.z0, Dir.NORTH));
        for (int z = r.z0 + 1; z <= r.z1; z++) spots.add(new WallSpot(r.x1, z, Dir.EAST));
        for (int x = r.x1 - 1; x >= r.x0; x--) spots.add(new WallSpot(x, r.z1, Dir.SOUTH));
        for (int z = r.z1 - 1; z > r.z0; z--) spots.add(new WallSpot(r.x0, z, Dir.WEST));
        // Nur Zellen, hinter denen wirklich eine Wand steht
        spots.removeIf(s -> !v.isSolid(s.x + s.toWall.dx, r.y, s.z + s.toWall.dz));
        return spots;
    }

    /** Richtet einen Raum passend zu seinem Typ ein. */
    public void furnish(Room r) {
        if (r.width() < 2 || r.depth() < 2) return;
        List<String> items = switch (r.type) {
            case "kitchen" -> List.of("smoker", "crafting_table", "barrel", "water_cauldron[level=3]", "furnace", "chest", "barrel");
            case "bedroom" -> List.of("#bed", "chest", "#plant", "bookshelf", "barrel");
            case "library", "office" -> List.of("bookshelf", "bookshelf", "#lectern", "bookshelf", "bookshelf", "cartography_table", "bookshelf", "bookshelf");
            case "storage" -> List.of("barrel", "chest", "barrel", "barrel", "chest", "barrel", "barrel");
            case "workshop" -> List.of("crafting_table", "smithing_table", "#anvil", "grindstone[face=floor,facing=north]", "stonecutter[facing=north]", "barrel", "loom[facing=north]");
            case "bar" -> List.of("barrel", "barrel", "brewing_stand", "barrel", "barrel", "#plant", "barrel");
            case "shop" -> List.of("barrel", "chest", "#lectern", "barrel", "chest", "#plant");
            case "chapel" -> List.of("#plant", "bookshelf", "#plant");
            case "dining" -> List.of("barrel", "#plant", "chest", "#plant");
            default -> List.of("bookshelf", "#plant", "chest", "bookshelf", "barrel", "#plant"); // living
        };
        boolean centerTable = !r.type.equals("storage") && !r.type.equals("workshop") && !r.type.equals("bedroom");
        // Zentrum (Tisch + Stühle) zuerst reservieren
        if (centerTable && r.width() >= 5 && r.depth() >= 5) {
            placeTable(r, r.type.equals("dining") || r.type.equals("bar") ? Math.min(3, r.width() - 4) : 1);
        } else if (r.width() >= 4 && r.depth() >= 4 && !r.type.equals("storage")) {
            rug(r);
        }
        List<WallSpot> spots = wallSpots(r);
        int i = 0;
        int step = r.type.equals("library") || r.type.equals("storage") ? 1 : 2;
        for (String item : items) {
            while (i < spots.size()) {
                WallSpot s = spots.get(i);
                i += step;
                if (!free(s.x, r.y, s.z) || blocksPassage(s.x, r.y, s.z)) continue;
                if (placeItem(item, s, r)) break;
            }
        }
        // Bibliothek/Lager: zweite Regal-/Fassreihe obendrauf
        if (r.type.equals("library") || r.type.equals("storage")) {
            for (WallSpot s : spots) {
                String below = v.get(s.x, r.y, s.z);
                if (below != null && (below.endsWith("bookshelf") || below.endsWith("barrel")) && free(s.x, r.y + 1, s.z)
                    && r.y + 1 < r.ceiling) {
                    v.set(s.x, r.y + 1, s.z, below);
                }
            }
        }
        hangingLight(r.cx(), r.ceiling - 1, r.cz());
    }

    /** Zellen direkt vor Türen/Durchgängen frei lassen. */
    private boolean blocksPassage(int x, int y, int z) {
        for (Dir dir : Dir.values()) {
            int nx = x + dir.dx, nz = z + dir.dz;
            String s = v.get(nx, y, nz);
            if (s != null && B.name(s).endsWith("_door")) return true;
            if (isReserved(nx, y, nz) && v.isEmpty(nx, y + 1, nz) && v.isSolid(nx + dir.dx, y + 1, nz + dir.dz)) return true;
        }
        return false;
    }

    private boolean placeItem(String item, WallSpot s, Room r) {
        Dir away = s.toWall.opposite();
        switch (item) {
            case "#bed" -> {
                // Kopfende an der Wand: Fuß eine Zelle davor
                int fx = s.x + away.dx, fz = s.z + away.dz;
                if (fx < r.x0 || fx > r.x1 || fz < r.z0 || fz > r.z1 || !free(fx, r.y, fz)) return false;
                v.set(fx, r.y, fz, B.bed(p.bed(), s.toWall));
                reserve(s.x, r.y, s.z);
                return true;
            }
            case "#plant" -> {
                v.set(s.x, r.y, s.z, B.id(rng.pick(List.of("potted_fern", "potted_azalea_bush", "potted_red_tulip", "potted_oak_sapling", "potted_cornflower"))));
                return true;
            }
            case "#lectern" -> {
                v.set(s.x, r.y, s.z, B.with("lectern", "facing", away.id()));
                return true;
            }
            case "#anvil" -> {
                v.set(s.x, r.y, s.z, B.with("anvil", "facing", s.toWall.clockwise().id()));
                return true;
            }
            default -> {
                String state = item.contains("[") ? B.id(item.substring(0, item.indexOf('['))) + item.substring(item.indexOf('['))
                    : B.id(item);
                if (item.startsWith("furnace") || item.startsWith("smoker") || item.equals("chest") || item.equals("barrel")) {
                    state = item.equals("barrel") ? B.with("barrel", "facing", away.id())
                        : B.with(item, "facing", away.id());
                }
                v.set(s.x, r.y, s.z, state);
                return true;
            }
        }
    }

    /** Tisch aus Zaun + Druckplatte mit Stühlen (Treppen) rundherum. */
    private void placeTable(Room r, int length) {
        int z = r.cz();
        int x0 = r.cx() - length / 2;
        for (int x = x0; x < x0 + length; x++) {
            if (!free(x, r.y, z)) return;
        }
        String fence = p.fence();
        String plate = B.name(fence).replace("_fence", "_pressure_plate");
        for (int x = x0; x < x0 + length; x++) {
            v.set(x, r.y, z, B.id(fence));
            v.set(x, r.y + 1, z, B.id(plate));
            chair(x, r.y, z - 1, Dir.NORTH);
            chair(x, r.y, z + 1, Dir.SOUTH);
        }
        if (length == 1) {
            chair(x0 - 1, r.y, z, Dir.WEST);
            chair(x0 + 1, r.y, z, Dir.EAST);
        }
    }

    /** Stuhl mit Lehne in Richtung {@code back} (Sitzfläche zeigt zum Tisch). */
    private void chair(int x, int y, int z, Dir back) {
        if (!free(x, y, z)) return;
        v.set(x, y, z, B.stairs(p.floor(), back, false));
    }

    private void rug(Room r) {
        int x0 = r.cx() - 1, x1 = r.cx() + 1, z0 = r.cz() - 1, z1 = r.cz() + 1;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                if (free(x, r.y, z) && v.isSolid(x, r.y - 1, z)) v.set(x, r.y, z, B.id(p.carpet()));
    }

    /** Hängende Lampe, wenn darüber ein Block ist. */
    public void hangingLight(int x, int y, int z) {
        if (free(x, y, z) && v.isSolid(x, y + 1, z)) {
            String light = p.light();
            if (B.name(light).endsWith("lantern")) v.set(x, y, z, B.lantern(light, true));
            else v.set(x, y + 1, z, B.id(light)); // Leuchtblock in der Decke (modern)
        }
    }

    // ------------------------------------------------------------------ Außenanlagen

    public void flowersAndBushes(int x0, int z0, int x1, int z1, double flowerChance, double bushChance) {
        List<String> flowers = List.of("poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet",
            "red_tulip", "white_tulip", "pink_tulip", "lily_of_the_valley");
        for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++)
            for (int z = Math.max(0, z0); z <= Math.min(d - 1, z1); z++) {
                if (!free(x, 1, z) || !isGround(v.get(x, 0, z))) continue;
                double r = rng.nextDouble();
                if (r < bushChance) v.set(x, 1, z, B.leaves(p.leaves()));
                else if (r < bushChance + flowerChance) v.set(x, 1, z, B.id(rng.pick(flowers)));
                else if (r < bushChance + flowerChance + 0.10) v.set(x, 1, z, B.id("short_grass"));
            }
    }

    private static boolean isGround(String s) {
        if (s == null) return false;
        String n = B.name(s);
        return n.equals("grass_block") || n.equals("dirt") || n.equals("moss_block") || n.equals("podzol");
    }

    /** Kleiner Laubbaum (Stamm + Krone), nur wenn genug Platz ist. */
    public void tree(int x, int z, int trunk) {
        String log = B.name(p.leaves()).replace("_leaves", "_log");
        if (log.startsWith("flowering_azalea") || log.startsWith("azalea")) log = "oak_log";
        int top = 1 + trunk;
        if (top + 2 >= h) return;
        for (int y = 1; y <= trunk; y++) {
            if (!free(x, y, z)) return;
        }
        for (int y = 1; y <= trunk; y++) v.set(x, y, z, B.axis(log, 'y'));
        for (int y = top - 1; y <= top + 1; y++) {
            int rad = y == top + 1 ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) == rad && Math.abs(dz) == rad && rng.chance(0.6)) continue;
                    if (free(x + dx, y, z + dz)) v.set(x + dx, y, z + dz, B.leaves(p.leaves()));
                }
        }
    }

    /** Zaun am Grundstücksrand mit Tor an {@code gateX} (Vorderseite). */
    public void fence(int gateX, int gateWidth) {
        for (int x = 0; x < w; x++) {
            for (int z : new int[] {0, d - 1}) {
                if (z == 0 && x >= gateX && x < gateX + gateWidth) {
                    v.set(x, 1, z, B.gate(p.gate(), Dir.NORTH));
                } else if (free(x, 1, z)) {
                    v.set(x, 1, z, B.id(p.fence()));
                }
            }
        }
        for (int z = 1; z < d - 1; z++) {
            for (int x : new int[] {0, w - 1}) if (free(x, 1, z)) v.set(x, 1, z, B.id(p.fence()));
        }
    }

    /** Laternen auf Zaunpfosten beidseits eines Wegs. */
    public void lanternPosts(int x, int z, int width) {
        for (int px : new int[] {x - 1, x + width}) {
            if (!v.in(px, 2, z)) continue;
            String at = v.get(px, 1, z);
            if (at != null && !B.isAir(at) && !B.name(at).endsWith("_fence")) continue;
            v.set(px, 1, z, B.id(p.fence()));
            v.set(px, 2, z, B.lantern(p.light().contains("lantern") ? p.light() : "lantern", false));
        }
    }
}
