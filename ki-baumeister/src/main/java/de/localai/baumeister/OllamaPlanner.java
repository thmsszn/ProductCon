package de.localai.baumeister;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class OllamaPlanner {
    private static final Set<String> ALLOWED = Set.of(
        "minecraft:stone_bricks","minecraft:stone","minecraft:oak_planks","minecraft:spruce_planks",
        "minecraft:dark_oak_planks","minecraft:birch_planks","minecraft:quartz_block","minecraft:glass",
        "minecraft:glass_pane","minecraft:bricks","minecraft:cobblestone","minecraft:oak_log",
        "minecraft:spruce_log","minecraft:dark_oak_log","minecraft:white_concrete","minecraft:gray_concrete",
        "minecraft:black_concrete","minecraft:blue_concrete","minecraft:lantern","minecraft:glowstone",
        "minecraft:sea_lantern","minecraft:oak_stairs","minecraft:stone_brick_stairs","minecraft:oak_door",
        "minecraft:iron_block","minecraft:copper_block","minecraft:grass_block","minecraft:polished_andesite",
        "minecraft:andesite","minecraft:deepslate_bricks","minecraft:oak_fence","minecraft:oak_trapdoor"
    );
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    public static CompletableFuture<BlockPlan> create(String prompt, int width, int height, int depth, String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String system = "Du bist Minecraft-Bauplaner. Antworte ausschließlich als JSON der Form "
                    + "{\"summary\":\"deutsch\",\"blocks\":[{\"x\":0,\"y\":0,\"z\":0,\"block\":\"minecraft:stone_bricks\"}]}. "
                    + "Koordinaten relativ zum kleinsten Eckpunkt, x=0.." + (width-1) + ", y=0.." + (height-1)
                    + ", z=0.." + (depth-1) + ". Nur diese Block-IDs: " + String.join(", ", ALLOWED)
                    + ". Maximal 3000 Blöcke. Erzeuge ein zusammenhängendes Bauwerk und halte die Maße exakt ein.";
                JsonObject req = new JsonObject();
                req.addProperty("model", model);
                req.addProperty("system", system);
                req.addProperty("prompt", "Bauauftrag: " + prompt + ". Maße W=" + width + ", H=" + height + ", D=" + depth + ".");
                req.addProperty("stream", false);
                req.addProperty("format", "json");
                req.add("options", JsonParser.parseString("{\"temperature\":0.2,\"num_predict\":10000}"));
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/generate"))
                    .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(req.toString())).build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("Ollama HTTP " + response.statusCode());
                JsonObject outer = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject result = JsonParser.parseString(outer.get("response").getAsString()).getAsJsonObject();
                String summary = result.has("summary") ? result.get("summary").getAsString() : "Bauplan";
                JsonArray arr = result.getAsJsonArray("blocks");
                if (arr == null || arr.isEmpty()) throw new IllegalStateException("KI lieferte keine Blöcke.");
                if (arr.size() > 3000) throw new IllegalStateException("Bauplan überschreitet 3000 Blöcke.");
                List<PlannedBlock> blocks = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject b = el.getAsJsonObject();
                    int x=b.get("x").getAsInt(), y=b.get("y").getAsInt(), z=b.get("z").getAsInt();
                    String id=b.get("block").getAsString();
                    if (x>=0 && x<width && y>=0 && y<height && z>=0 && z<depth && ALLOWED.contains(id))
                        blocks.add(new PlannedBlock(x,y,z,id));
                }
                if (blocks.isEmpty()) throw new IllegalStateException("Keine gültigen Blöcke im Bauplan.");
                return new BlockPlan(summary, List.copyOf(blocks));
            } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        });
    }
}
