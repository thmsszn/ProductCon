package de.localai.baumeister;

/** Prompts für die KI in der Rolle des Architekten: sie wählt nur den Entwurf, die Engine baut. */
public final class PlanPrompt {
    private PlanPrompt() {}

    private static final String SYSTEM = """
        You are an experienced Minecraft architect. The player describes a building; you choose the design.
        A procedural building engine then constructs it block by block (walls, framing, windows, roof,
        stairs, furniture, garden), so you only fill in the design form as JSON.

        Fields:
        - summary: one short German sentence describing the building.
        - type: house, cottage, mansion, tavern, barn, modern_house, tower, lighthouse, castle, temple, church.
        - style: medieval, rustic, nordic, fantasy, victorian, japanese, desert, modern, classical, stone.
        - floors: number of storeys (each storey is about 4-5 blocks high; the roof needs extra height).
        - roof: gable (Satteldach), hip (Walmdach), flat, cone (towers).
        - wall, frame, roof_material: materials that fit the style and the player's wishes.
        - features: extras like chimney, porch, balcony, garden, fence, path, shutters, flower_boxes, lanterns, trees, pool, interior.
        - rooms: room types from bottom to top (2 per storey on larger buildings), e.g. living, kitchen, bedroom, library.

        Rules: follow the player's wishes (type, style, colors, materials, number of floors, details). Pick floors that fit
        the site height. Choose harmonious materials. Always include "interior". Answer with the JSON only.
        """;

    public static String system() {
        return SYSTEM;
    }

    public static String request(String prompt, BuildRegion region) {
        return "Request: " + prompt.trim() + "\n"
            + "Building site: " + region.localWidth() + " wide, " + region.localHeight() + " high, " + region.localDepth()
            + " deep (blocks). Answer with the JSON design only; the summary must be German.";
    }

    public static String refinement(String change) {
        return "Change request: " + change.trim() + "\n"
            + "Apply it to your previous design and return the COMPLETE updated design as JSON. The summary must be German.";
    }
}
