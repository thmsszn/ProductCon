package de.localai.baumeister;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** System-Prompt, Nutzer-Nachrichten und JSON-Schema für die Bauplan-Erzeugung. */
public final class PlanPrompt {
    private PlanPrompt() {}

    private static final String SYSTEM = """
        You are a master Minecraft architect. You design ONE structure that fits into a building site.

        COORDINATES (integers, relative to the site):
        - x = width, y = height (y=0 is the ground layer), z = depth.
        - The front with the main entrance is at z=0; the player looks at the structure from there.

        OUTPUT: only JSON: {"summary": "<short description in German>", "elements": [ ... ]}
        Each element: {"type": T, "x1":0, "y1":0, "z1":0, "x2":0, "y2":0, "z2":0, "block": "minecraft:<id>"}
        Element types (x1..z2 are two opposite corners, inclusive):
        - "fill": solid box.
        - "hollow": box shell; everything inside becomes air (rooms).
        - "walls": only the four side walls of the box (no floor, no ceiling).
        - "roof": hip roof over the x/z area starting at y1, every layer one block smaller on each side.
          Best with stairs blocks (they are turned outwards automatically); y2 caps the height.
        - "cylinder": round hollow wall inside the box (towers); the inside becomes air.
        - "block": one block at x1,y1,z1 (repeat the same values for x2,y2,z2).
        Elements are applied in order, later ones overwrite earlier ones. After building walls, cut
        openings with "minecraft:air" and then place doors and glass into them.
        Block states are allowed, e.g. "minecraft:oak_log[axis=x]", "minecraft:oak_stairs[facing=south]".
        Doors and beds are completed automatically (place only the lower/foot block).

        GOOD BLOCKS (all vanilla blocks are allowed):
        walls: stone_bricks, bricks, oak_planks, spruce_planks, dark_oak_planks, birch_planks, cherry_planks,
          quartz_block, smooth_sandstone, white_concrete, terracotta, mud_bricks, deepslate_bricks, tuff_bricks
        frames/pillars: oak_log, spruce_log, dark_oak_log, stripped_oak_log, polished_andesite, chiseled_stone_bricks
        floor: polished_andesite, smooth_stone, spruce_planks, oak_planks, polished_deepslate
        roof: spruce_stairs, dark_oak_stairs, oak_stairs, stone_brick_stairs, brick_stairs, deepslate_tile_stairs
        glass: glass, glass_pane, white_stained_glass_pane, light_blue_stained_glass_pane
        light: lantern, torch, sea_lantern, glowstone, shroomlight
        details: oak_door, spruce_door, oak_fence, spruce_trapdoor, bookshelf, crafting_table, barrel, chest,
          red_bed, flower_pot, oak_leaves, hay_block, water, grass_block, cobblestone_wall

        DESIGN RULES:
        1. Use most of the site, never leave it. Ground floor/foundation on y=0.
        2. Prefer few large elements (15-60) over many single blocks.
        3. Always include: foundation/floor, walls, contrasting corner pillars, a door at z=0, windows on
           several sides, a roof, light inside, some interior details.
        4. Use 3-5 harmonizing materials and add depth (pillars, frames, overhangs, chimneys).
        5. If the request is not a building (statue, tree, ship, bridge, ...), build it creatively with
           the same element types.

        EXAMPLE for W=9, H=10, D=9 (a small cottage):
        {"summary":"Kleines Fachwerkhaus mit Walmdach","elements":[
        {"type":"fill","x1":0,"y1":0,"z1":0,"x2":8,"y2":0,"z2":8,"block":"minecraft:cobblestone"},
        {"type":"walls","x1":0,"y1":1,"z1":0,"x2":8,"y2":4,"z2":8,"block":"minecraft:oak_planks"},
        {"type":"fill","x1":0,"y1":1,"z1":0,"x2":0,"y2":4,"z2":0,"block":"minecraft:oak_log"},
        {"type":"fill","x1":8,"y1":1,"z1":0,"x2":8,"y2":4,"z2":0,"block":"minecraft:oak_log"},
        {"type":"fill","x1":0,"y1":1,"z1":8,"x2":0,"y2":4,"z2":8,"block":"minecraft:oak_log"},
        {"type":"fill","x1":8,"y1":1,"z1":8,"x2":8,"y2":4,"z2":8,"block":"minecraft:oak_log"},
        {"type":"fill","x1":4,"y1":1,"z1":0,"x2":4,"y2":2,"z2":0,"block":"minecraft:air"},
        {"type":"block","x1":4,"y1":1,"z1":0,"x2":4,"y2":1,"z2":0,"block":"minecraft:oak_door"},
        {"type":"fill","x1":2,"y1":2,"z1":0,"x2":2,"y2":3,"z2":0,"block":"minecraft:glass_pane"},
        {"type":"fill","x1":6,"y1":2,"z1":0,"x2":6,"y2":3,"z2":0,"block":"minecraft:glass_pane"},
        {"type":"fill","x1":0,"y1":2,"z1":3,"x2":0,"y2":3,"z2":5,"block":"minecraft:glass_pane"},
        {"type":"fill","x1":8,"y1":2,"z1":3,"x2":8,"y2":3,"z2":5,"block":"minecraft:glass_pane"},
        {"type":"roof","x1":0,"y1":5,"z1":0,"x2":8,"y2":9,"z2":8,"block":"minecraft:spruce_stairs"},
        {"type":"block","x1":2,"y1":1,"z1":6,"x2":2,"y2":1,"z2":6,"block":"minecraft:crafting_table"},
        {"type":"block","x1":6,"y1":1,"z1":6,"x2":6,"y2":1,"z2":6,"block":"minecraft:red_bed[facing=north]"},
        {"type":"block","x1":4,"y1":1,"z1":5,"x2":4,"y2":1,"z2":5,"block":"minecraft:lantern"}]}
        """;

    public static String system() {
        return SYSTEM;
    }

    public static String request(String prompt, BuildRegion region) {
        int w = region.localWidth(), h = region.localHeight(), d = region.localDepth();
        return "Request: " + prompt.trim() + "\n"
            + "Site: W=" + w + " (x 0.." + (w - 1) + "), H=" + h + " (y 0.." + (h - 1) + "), D=" + d
            + " (z 0.." + (d - 1) + ").\n"
            + "Answer with the JSON plan only. The summary must be German.";
    }

    public static String refinement(String change, BuildRegion region) {
        int w = region.localWidth(), h = region.localHeight(), d = region.localDepth();
        return "Change request: " + change.trim() + "\n"
            + "Apply it to your previous plan and return the COMPLETE updated plan (all elements) in the same "
            + "JSON format. Site is still W=" + w + ", H=" + h + ", D=" + d + ". The summary must be German.";
    }

    /** JSON-Schema für Ollamas {@code format}-Parameter (erzwingt gültiges JSON in genau dieser Form). */
    public static JsonObject schema() {
        JsonObject element = new JsonObject();
        element.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject type = new JsonObject();
        type.addProperty("type", "string");
        JsonArray shapes = new JsonArray();
        for (BuildPlan.Shape s : BuildPlan.Shape.values()) shapes.add(s.id());
        type.add("enum", shapes);
        props.add("type", type);
        JsonArray required = new JsonArray();
        required.add("type");
        for (String c : new String[] {"x1", "y1", "z1", "x2", "y2", "z2"}) {
            JsonObject i = new JsonObject();
            i.addProperty("type", "integer");
            props.add(c, i);
            required.add(c);
        }
        JsonObject block = new JsonObject();
        block.addProperty("type", "string");
        props.add("block", block);
        required.add("block");
        element.add("properties", props);
        element.add("required", required);

        JsonObject elements = new JsonObject();
        elements.addProperty("type", "array");
        elements.add("items", element);

        JsonObject summary = new JsonObject();
        summary.addProperty("type", "string");

        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        JsonObject rootProps = new JsonObject();
        rootProps.add("summary", summary);
        rootProps.add("elements", elements);
        root.add("properties", rootProps);
        JsonArray rootReq = new JsonArray();
        rootReq.add("summary");
        rootReq.add("elements");
        root.add("required", rootReq);
        return root;
    }
}
