package de.localai.baumeister;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.localai.baumeister.arch.Architect;
import de.localai.baumeister.arch.BuildingSpec;
import de.localai.baumeister.arch.Materials;
import de.localai.baumeister.arch.Voxels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Prüft jeden Blockzustand, den die Architektur-Engine erzeugt, gegen die echte Block-Registry von Minecraft:
 * Ein unbekannter Block oder eine falsche Eigenschaft würde im Spiel als Loch oder falsch gedrehter Block enden.
 */
class EngineBlocksTest {
    private static final String[] TYPES = {"house", "cottage", "mansion", "tavern", "barn", "modern_house", "tower",
        "lighthouse", "castle", "temple", "church", "custom"};
    private static final String[] STYLES = {"medieval", "rustic", "nordic", "fantasy", "victorian", "japanese", "desert",
        "modern", "classical", "stone", "viking", "gothic", "steampunk", "oriental"};
    private static final String[] ROOFS = {"gable", "hip", "flat", "cone"};
    private static final String ALL_FEATURES = "[\"chimney\",\"porch\",\"balcony\",\"garden\",\"fence\",\"path\","
        + "\"shutters\",\"flower_boxes\",\"lanterns\",\"trees\",\"pool\",\"interior\"]";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Exakt parsen, ohne den Rückfall auf den Standardzustand, den PlanCompiler.resolve im Spiel nutzt. */
    private static String problem(String block) {
        try {
            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, block, false);
        } catch (CommandSyntaxException e) {
            return e.getMessage();
        }
        return PlanCompiler.resolve(block) == null ? "gesperrter Block" : null;
    }

    @Test
    void materialListsExist() {
        List<String> bad = new ArrayList<>();
        for (String key : Materials.familyKeys()) {
            Materials.Family f = Materials.family(key);
            for (String b : List.of(f.stairs(), f.slab(), f.block())) if (problem(b) != null) bad.add(key + ": " + b);
        }
        for (String b : Materials.WALLS) if (problem(b) != null) bad.add("WALLS: " + b);
        for (String b : Materials.FRAMES) if (problem(b) != null) bad.add("FRAMES: " + b);
        assertTrue(bad.isEmpty(), "Unbekannte Materialien: " + bad);
    }

    @Test
    void everyGeneratedBlockStateIsValid() {
        Map<String, String> bad = new TreeMap<>();
        Map<String, String> firstCase = new TreeMap<>();
        Random r = new Random(7);
        int cases = 0;
        // Jede Kombination aus Typ und Stil in einer typischen Größe, dazu zufällige Größen und Materialien
        for (String type : TYPES)
            for (String style : STYLES) {
                cases += check(type, style, ROOFS[r.nextInt(ROOFS.length)], 1 + r.nextInt(4),
                    21 + r.nextInt(12), 24 + r.nextInt(16), 21 + r.nextInt(12), null, null, r.nextLong(), bad, firstCase);
            }
        for (int i = 0; i < 400; i++) {
            String wall = r.nextInt(3) == 0 ? Materials.WALLS.get(r.nextInt(Materials.WALLS.size())) : null;
            String frame = r.nextInt(3) == 0 ? Materials.FRAMES.get(r.nextInt(Materials.FRAMES.size())) : null;
            cases += check(TYPES[r.nextInt(TYPES.length)], STYLES[r.nextInt(STYLES.length)], ROOFS[r.nextInt(ROOFS.length)],
                1 + r.nextInt(6), 5 + r.nextInt(56), 5 + r.nextInt(56), 5 + r.nextInt(56), wall, frame, r.nextLong(), bad, firstCase);
        }
        StringBuilder msg = new StringBuilder();
        bad.forEach((block, why) -> msg.append("\n  ").append(block).append("  -> ").append(why)
            .append("  (z.B. ").append(firstCase.get(block)).append(')'));
        assertTrue(bad.isEmpty(), bad.size() + " ungültige Blockzustände in " + cases + " Gebäuden:" + msg);
    }

    private static int check(String type, String style, String roof, int floors, int w, int h, int d, String wall,
                             String frame, long seed, Map<String, String> bad, Map<String, String> firstCase) {
        JsonObject o = new JsonObject();
        o.addProperty("summary", type + " " + style + " " + seed);
        o.addProperty("type", type);
        o.addProperty("style", style);
        o.addProperty("floors", floors);
        o.addProperty("roof", roof);
        if (wall != null) o.addProperty("wall", wall);
        if (frame != null) o.addProperty("frame", frame);
        o.add("features", JsonParser.parseString(ALL_FEATURES));
        BuildingSpec spec = BuildingSpec.fromJson(o).withSeed(seed);
        Voxels v = Architect.generate(spec, w, h, d, PlanCompiler::exists);
        assertEquals(w, v.w);
        assertEquals(h, v.h);
        assertEquals(d, v.d);
        String where = type + "/" + style + "/" + roof + " " + w + "x" + h + "x" + d;
        for (int y = 0; y < v.h; y++)
            for (int z = 0; z < v.d; z++)
                for (int x = 0; x < v.w; x++) {
                    String s = v.get(x, y, z);
                    if (s == null || bad.containsKey(s) || firstCase.containsKey("ok:" + s)) continue;
                    String why = problem(s);
                    if (why == null) firstCase.put("ok:" + s, "");
                    else {
                        bad.put(s, why);
                        firstCase.put(s, where + " @" + x + "," + y + "," + z);
                    }
                }
        // Im Spiel kompilieren (Drehung, Türen, Betten); darf nie werfen
        BuildRegion region = new BuildRegion(BlockPos.ZERO, new BlockPos(w - 1, h - 1, d - 1), Rotation.NONE);
        PlanCompiler.Result result = PlanCompiler.compile(spec, v, region);
        assertTrue(result.solidBlocks() > 0, "leeres Gebäude: " + where);
        return 1;
    }
}
