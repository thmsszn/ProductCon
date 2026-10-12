package de.localai.baumeister.arch;

import java.util.Locale;

/** Abgestimmte Materialien eines Baustils. Die KI kann einzelne Einträge (Wand, Dach, Rahmen) überschreiben. */
public record Palette(
    String foundation,
    String groundWall,
    String wall,
    String frame,
    Materials.Family floor,
    Materials.Family roof,
    Materials.Family trim,
    String window,
    String door,
    String trapdoor,
    String fence,
    String light,
    String chimney,
    String path,
    String accent,
    String carpet,
    String bed,
    String leaves,
    boolean timber,
    boolean shutters,
    boolean bigWindows,
    int floorHeight,
    Roof defaultRoof) {

    public enum Roof { GABLE, HIP, FLAT, CONE }

    public enum Style {
        MEDIEVAL, RUSTIC, NORDIC, FANTASY, VICTORIAN, JAPANESE, DESERT, MODERN, CLASSICAL, STONE;

        public static Style parse(String s) {
            if (s == null) return MEDIEVAL;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "rustic", "rustikal", "farm", "country" -> RUSTIC;
                case "nordic", "viking", "scandinavian", "nordisch" -> NORDIC;
                case "fantasy", "magic", "elven", "fairy" -> FANTASY;
                case "victorian", "viktorianisch", "colonial" -> VICTORIAN;
                case "japanese", "asian", "japanisch", "chinese", "oriental" -> JAPANESE;
                case "desert", "wüste", "middle_eastern", "arabic", "mediterranean" -> DESERT;
                case "modern", "contemporary", "minimal", "futuristic" -> MODERN;
                case "classical", "greek", "roman", "antik", "ancient" -> CLASSICAL;
                case "stone", "castle", "gothic", "fortress" -> STONE;
                default -> MEDIEVAL;
            };
        }
    }

    public static Palette of(Style style) {
        Materials.Family spruce = Materials.family("spruce");
        Materials.Family oak = Materials.family("oak");
        Materials.Family darkOak = Materials.family("dark_oak");
        return switch (style) {
            case MEDIEVAL -> new Palette("cobblestone", "stone_bricks", "white_terracotta", "dark_oak_log",
                spruce, Materials.family("deepslate_tile"), darkOak, "glass_pane", "dark_oak_door", "spruce_trapdoor",
                "dark_oak_fence", "lantern", "bricks", "dirt_path", "mossy_cobblestone", "red_carpet", "red",
                "oak_leaves", true, true, false, 4, Roof.GABLE);
            case RUSTIC -> new Palette("cobblestone", "cobblestone", "oak_planks", "spruce_log",
                oak, spruce, spruce, "glass_pane", "spruce_door", "spruce_trapdoor",
                "spruce_fence", "lantern", "cobblestone", "dirt_path", "mossy_cobblestone", "brown_carpet", "brown",
                "oak_leaves", true, true, false, 4, Roof.GABLE);
            case NORDIC -> new Palette("cobbled_deepslate", "spruce_planks", "spruce_planks", "dark_oak_log",
                spruce, darkOak, darkOak, "glass_pane", "dark_oak_door", "dark_oak_trapdoor",
                "dark_oak_fence", "lantern", "cobbled_deepslate", "gravel", "mossy_cobblestone", "gray_carpet", "gray",
                "spruce_leaves", true, false, false, 4, Roof.GABLE);
            case FANTASY -> new Palette("polished_andesite", "calcite", "calcite", "stripped_dark_oak_log",
                Materials.family("dark_oak"), Materials.family("dark_prismarine"), Materials.family("cherry"),
                "light_blue_stained_glass_pane", "cherry_door", "cherry_trapdoor", "cherry_fence", "soul_lantern",
                "stone_bricks", "dirt_path", "amethyst_block", "purple_carpet", "purple", "flowering_azalea_leaves",
                true, true, false, 4, Roof.HIP);
            case VICTORIAN -> new Palette("stone_bricks", "bricks", "bricks", "smooth_quartz",
                darkOak, Materials.family("deepslate_tile"), Materials.family("quartz"), "glass_pane", "dark_oak_door",
                "dark_oak_trapdoor", "dark_oak_fence", "lantern", "bricks", "gravel", "smooth_quartz", "red_carpet",
                "red", "oak_leaves", false, true, false, 5, Roof.HIP);
            case JAPANESE -> new Palette("stone_bricks", "white_terracotta", "white_terracotta", "stripped_dark_oak_log",
                spruce, Materials.family("deepslate_tile"), darkOak, "white_stained_glass_pane", "spruce_door",
                "spruce_trapdoor", "dark_oak_fence", "lantern", "stone_bricks", "gravel", "moss_block", "green_carpet",
                "white", "cherry_leaves", true, false, false, 4, Roof.HIP);
            case DESERT -> new Palette("sandstone", "smooth_sandstone", "smooth_sandstone", "cut_sandstone",
                Materials.family("acacia"), Materials.family("smooth_sandstone"), Materials.family("sandstone"),
                "glass_pane", "acacia_door", "acacia_trapdoor", "acacia_fence", "lantern", "sandstone", "sand",
                "orange_terracotta", "orange_carpet", "orange", "acacia_leaves", false, false, false, 4, Roof.FLAT);
            case MODERN -> new Palette("smooth_stone", "white_concrete", "white_concrete", "light_gray_concrete",
                Materials.family("birch"), Materials.family("smooth_stone"), Materials.family("polished_deepslate"),
                "glass_pane", "birch_door", "iron_trapdoor", "birch_fence", "sea_lantern", "gray_concrete",
                "smooth_stone", "gray_concrete", "light_gray_carpet", "white", "azalea_leaves", false, false, true, 4,
                Roof.FLAT);
            case CLASSICAL -> new Palette("quartz_block", "smooth_quartz", "smooth_quartz", "quartz_pillar",
                Materials.family("quartz"), Materials.family("quartz"), Materials.family("smooth_quartz"), "glass_pane",
                "birch_door", "birch_trapdoor", "birch_fence", "lantern", "quartz_block", "smooth_stone",
                "chiseled_quartz_block", "white_carpet", "white", "oak_leaves", false, false, false, 5, Roof.GABLE);
            case STONE -> new Palette("cobblestone", "stone_bricks", "stone_bricks", "chiseled_stone_bricks",
                spruce, Materials.family("deepslate_tile"), Materials.family("stone_brick"), "glass_pane",
                "dark_oak_door", "dark_oak_trapdoor", "dark_oak_fence", "lantern", "cobblestone", "gravel",
                "mossy_stone_bricks", "red_carpet", "red", "oak_leaves", false, false, false, 5, Roof.GABLE);
        };
    }

    public Palette withWall(String wall) {
        return new Palette(foundation, groundWall.equals(this.wall) ? wall : groundWall, wall, frame, floor, roof, trim,
            window, door, trapdoor, fence, light, chimney, path, accent, carpet, bed, leaves, timber, shutters,
            bigWindows, floorHeight, defaultRoof);
    }

    public Palette withFrame(String frame) {
        return new Palette(foundation, groundWall, wall, frame, floor, roof, trim, window, door, trapdoor, fence, light,
            chimney, path, accent, carpet, bed, leaves, timber, shutters, bigWindows, floorHeight, defaultRoof);
    }

    public Palette withRoof(Materials.Family roof) {
        return new Palette(foundation, groundWall, wall, frame, floor, roof, trim, window, door, trapdoor, fence, light,
            chimney, path, accent, carpet, bed, leaves, timber, shutters, bigWindows, floorHeight, defaultRoof);
    }

    /** Zaun-Tor passend zum Zaun. */
    public String gate() {
        return fence.replace("_fence", "_fence_gate");
    }
}
