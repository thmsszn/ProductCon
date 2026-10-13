package de.localai.baumeister.arch;

/** Kleine Helfer, um Blockzustände als Text zu bauen, z.B. {@code minecraft:oak_stairs[facing=north,half=bottom]}. */
public final class B {
    public static final String AIR = "minecraft:air";

    private B() {}

    public static String id(String name) {
        return name.contains(":") ? name : "minecraft:" + name;
    }

    /** Reiner Blockname ohne Namespace und Zustände. */
    public static String name(String state) {
        String s = state;
        int bracket = s.indexOf('[');
        if (bracket >= 0) s = s.substring(0, bracket);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    public static String with(String block, String... keyValues) {
        StringBuilder sb = new StringBuilder(id(block)).append('[');
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(keyValues[i]).append('=').append(keyValues[i + 1]);
        }
        return sb.append(']').toString();
    }

    public static String stairs(Materials.Family family, Dir facing, boolean upsideDown) {
        return with(family.stairs(), "facing", facing.id(), "half", upsideDown ? "top" : "bottom");
    }

    public static String slab(Materials.Family family, boolean top) {
        return with(family.slab(), "type", top ? "top" : "bottom");
    }

    /** Stamm/Säule entlang einer Achse; Blöcke ohne Achse bleiben unverändert. */
    public static String axis(String block, char axis) {
        String n = name(block);
        if (n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem") || n.endsWith("_hyphae")
            || n.equals("quartz_pillar") || n.equals("purpur_pillar") || n.endsWith("basalt")
            || n.equals("bamboo_block") || n.equals("stripped_bamboo_block") || n.equals("hay_block")
            || n.equals("bone_block") || n.equals("deepslate") || n.equals("muddy_mangrove_roots")) {
            return with(block, "axis", String.valueOf(axis));
        }
        return id(block);
    }

    public static String door(String door, Dir facing, boolean hingeRight) {
        return with(door, "facing", facing.id(), "half", "lower", "hinge", hingeRight ? "right" : "left", "open", "false");
    }

    /** Fensterladen bzw. Sims: {@code facing} zeigt von der Wand weg. */
    public static String trapdoor(String trapdoor, Dir facing, boolean top, boolean open) {
        return with(trapdoor, "facing", facing.id(), "half", top ? "top" : "bottom", "open", String.valueOf(open));
    }

    /** Laterne mit {@code hanging}-Eigenschaft (Laterne, Seelen- und Kupferlaternen; nicht Seelaterne/Kürbis). */
    public static boolean isLantern(String block) {
        String n = name(block);
        return n.equals("lantern") || n.endsWith("_lantern") && !n.equals("sea_lantern") && !n.equals("jack_o_lantern");
    }

    public static String lantern(String lantern, boolean hanging) {
        return with(isLantern(lantern) ? lantern : "lantern", "hanging", String.valueOf(hanging));
    }

    public static String wallTorch(Dir facing) {
        return with("wall_torch", "facing", facing.id());
    }

    public static String ladder(Dir facing) {
        return with("ladder", "facing", facing.id());
    }

    public static String bed(String color, Dir facing) {
        return with(color + "_bed", "facing", facing.id(), "part", "foot");
    }

    public static String leaves(String leaves) {
        return with(leaves, "persistent", "true");
    }

    public static String gate(String fenceGate, Dir facing) {
        return with(fenceGate, "facing", facing.id(), "open", "false");
    }

    public static String campfire() {
        return with("campfire", "lit", "true", "signal_fire", "false", "facing", "north");
    }

    /** Für die Vorschau/Statistik: Blöcke ohne volle Würfelform. */
    public static boolean isThin(String state) {
        String n = name(state);
        return n.endsWith("_pane") || n.endsWith("_fence") || n.endsWith("_fence_gate") || n.endsWith("_wall")
            || n.endsWith("_bars") || n.endsWith("_door") || n.endsWith("_trapdoor") || n.endsWith("torch")
            || isLantern(n) || n.endsWith("_carpet") || n.endsWith("_pressure_plate") || n.equals("ladder")
            || n.endsWith("_bed") || n.startsWith("potted_") || n.equals("flower_pot") || n.endsWith("campfire")
            || n.endsWith("_sign") || n.endsWith("_banner") || n.equals("chain") || n.equals("iron_chain")
            || n.equals("lever") || n.endsWith("_button") || n.endsWith("candle") || n.equals("end_rod")
            || isPlant(n);
    }

    public static boolean isPlant(String name) {
        return switch (name) {
            case "poppy", "dandelion", "cornflower", "allium", "oxeye_daisy", "azure_bluet", "red_tulip",
                 "orange_tulip", "white_tulip", "pink_tulip", "lily_of_the_valley", "blue_orchid",
                 "short_grass", "fern", "sweet_berry_bush", "rose_bush", "peony", "lilac", "sunflower",
                 "pink_petals", "bush", "firefly_bush", "wildflowers", "leaf_litter", "tall_grass", "large_fern",
                 "lily_pad", "sugar_cane", "dead_bush", "red_mushroom", "brown_mushroom", "wheat", "carrots",
                 "potatoes", "beetroots", "bamboo", "cactus" -> true;
            default -> false;
        };
    }

    public static boolean isAir(String state) {
        return state == null || state.equals(AIR);
    }
}
