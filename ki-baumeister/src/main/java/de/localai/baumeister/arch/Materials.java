package de.localai.baumeister.arch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Material-Familien (Treppe, Stufe, Vollblock) und erlaubte Baustoffe für die KI-Auswahl. */
public final class Materials {
    /** Zusammengehörige Blöcke einer Familie, z.B. spruce_stairs / spruce_slab / spruce_planks. */
    public record Family(String key, String stairs, String slab, String block) {}

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();

    static {
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "pale_oak", "crimson", "warped")) {
            add(wood, wood + "_stairs", wood + "_slab", wood + "_planks");
        }
        add("bamboo", "bamboo_mosaic_stairs", "bamboo_mosaic_slab", "bamboo_mosaic");
        for (String plural : List.of("stone_brick", "mossy_stone_brick", "brick", "mud_brick", "deepslate_brick",
            "deepslate_tile", "tuff_brick", "nether_brick", "red_nether_brick", "end_stone_brick", "prismarine_brick",
            "polished_blackstone_brick", "resin_brick")) {
            add(plural, plural + "_stairs", plural + "_slab", plural + "s");
        }
        for (String same : List.of("cobblestone", "mossy_cobblestone", "sandstone", "smooth_sandstone", "red_sandstone",
            "smooth_red_sandstone", "smooth_quartz", "prismarine", "dark_prismarine", "blackstone", "polished_blackstone",
            "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
            "cobbled_deepslate", "polished_deepslate", "tuff", "polished_tuff", "stone", "cut_copper")) {
            add(same, same + "_stairs", same + "_slab", same);
        }
        add("quartz", "quartz_stairs", "quartz_slab", "quartz_block");
        add("purpur", "purpur_stairs", "purpur_slab", "purpur_block");
        add("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab", "waxed_cut_copper");
        add("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_cut_copper");
        add("smooth_stone", "stone_stairs", "smooth_stone_slab", "smooth_stone");
    }

    private static void add(String key, String stairs, String slab, String block) {
        FAMILIES.put(key, new Family(key, stairs, slab, block));
    }

    private Materials() {}

    public static Family family(String key, Family fallback) {
        if (key == null) return fallback;
        String k = B.name(key.trim().toLowerCase(java.util.Locale.ROOT));
        Family f = FAMILIES.get(k);
        if (f != null) return f;
        // "spruce_stairs", "spruce_planks", "stone_bricks" usw. auf die Familie zurückführen
        for (Family fam : FAMILIES.values()) {
            if (k.equals(fam.stairs()) || k.equals(fam.slab()) || k.equals(fam.block())) return fam;
        }
        return fallback;
    }

    public static Family family(String key) {
        return FAMILIES.get(key);
    }

    public static List<String> familyKeys() {
        return List.copyOf(FAMILIES.keySet());
    }

    /** Wandbaustoffe, aus denen die KI wählen darf. */
    public static final List<String> WALLS = List.of(
        "white_terracotta", "calcite", "smooth_quartz", "quartz_block", "white_concrete", "light_gray_concrete",
        "gray_concrete", "black_concrete", "smooth_sandstone", "sandstone", "cut_sandstone", "red_sandstone",
        "terracotta", "orange_terracotta", "brown_terracotta", "light_gray_terracotta", "bricks", "stone_bricks",
        "mossy_stone_bricks", "cobblestone", "mud_bricks", "packed_mud", "deepslate_bricks", "tuff_bricks",
        "polished_andesite", "polished_diorite", "polished_deepslate", "oak_planks", "spruce_planks", "birch_planks",
        "dark_oak_planks", "cherry_planks", "pale_oak_planks", "jungle_planks", "stripped_birch_log",
        "stripped_oak_log", "stripped_spruce_log", "prismarine_bricks", "blackstone", "polished_blackstone_bricks",
        "resin_bricks", "nether_bricks", "end_stone_bricks", "purpur_block", "waxed_cut_copper");

    /** Holz/Säulen für Rahmen, Pfosten und Balken. */
    public static final List<String> FRAMES = List.of(
        "oak_log", "spruce_log", "dark_oak_log", "birch_log", "cherry_log", "jungle_log", "acacia_log", "mangrove_log",
        "pale_oak_log", "stripped_oak_log", "stripped_spruce_log", "stripped_dark_oak_log", "stripped_birch_log",
        "stripped_cherry_log", "quartz_pillar", "polished_basalt", "chiseled_stone_bricks", "polished_andesite",
        "light_gray_concrete", "gray_concrete", "black_concrete", "smooth_stone", "cut_sandstone");
}
