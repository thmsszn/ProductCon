package de.localai.baumeister;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Liest die KI-Antwort tolerant ein und rettet bei abgeschnittener Ausgabe alle vollständigen Elemente. */
public final class PlanParser {
    public static final int MAX_ELEMENTS = 1500;

    private PlanParser() {}

    public static BuildPlan parse(String raw) {
        String text = stripFences(raw);
        JsonObject root = tryParse(text);
        if (root == null) root = tryParse(repairTruncated(text));
        if (root == null) throw new OllamaClient.OllamaException("Die KI-Antwort ist kein gültiger Bauplan (kein JSON).");

        String summary = str(root, "summary", "description", "name");
        if (summary == null || summary.isBlank()) summary = "Bauplan";
        summary = summary.replaceAll("\\s+", " ").trim();
        if (summary.length() > 200) summary = summary.substring(0, 200) + "…";

        JsonArray arr = array(root, "elements", "blocks", "parts", "structure");
        if (arr == null) throw new OllamaClient.OllamaException("Der Bauplan enthält keine Elemente.");

        List<BuildPlan.Element> elements = new ArrayList<>();
        for (JsonElement el : arr) {
            if (elements.size() >= MAX_ELEMENTS) break;
            if (!el.isJsonObject()) continue;
            BuildPlan.Element e = element(el.getAsJsonObject());
            if (e != null) elements.add(e);
        }
        if (elements.isEmpty()) throw new OllamaClient.OllamaException("Der Bauplan enthält keine gültigen Elemente.");
        return new BuildPlan(summary, List.copyOf(elements));
    }

    private static BuildPlan.Element element(JsonObject o) {
        String block = str(o, "block", "material", "id");
        if (block == null || block.isBlank()) return null;
        String type = str(o, "type", "shape", "op");
        // Altes Format {"x":..,"y":..,"z":..} ist ein Einzelblock.
        int[] from = coords(o, "from", "x1", "y1", "z1", "x", "y", "z");
        if (from == null) return null;
        int[] to = coords(o, "to", "x2", "y2", "z2", null, null, null);
        if (to == null) to = from;
        BuildPlan.Shape shape = type == null && to == from ? BuildPlan.Shape.BLOCK
            : type == null ? BuildPlan.Shape.FILL : BuildPlan.Shape.parse(type);
        return new BuildPlan.Element(shape, from[0], from[1], from[2], to[0], to[1], to[2], block.trim());
    }

    private static int[] coords(JsonObject o, String arrayKey, String kx, String ky, String kz, String fx, String fy, String fz) {
        JsonElement arr = o.get(arrayKey);
        if (arr != null && arr.isJsonArray() && arr.getAsJsonArray().size() >= 3) {
            JsonArray a = arr.getAsJsonArray();
            Integer x = num(a.get(0)), y = num(a.get(1)), z = num(a.get(2));
            if (x != null && y != null && z != null) return new int[] {x, y, z};
        }
        Integer x = num(o.get(kx)), y = num(o.get(ky)), z = num(o.get(kz));
        if (x == null && fx != null) { x = num(o.get(fx)); y = num(o.get(fy)); z = num(o.get(fz)); }
        if (x == null || y == null || z == null) return null;
        return new int[] {x, y, z};
    }

    private static Integer num(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        try {
            return (int) Math.round(e.getAsDouble());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String str(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) return e.getAsString();
        }
        return null;
    }

    private static JsonArray array(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonArray()) return e.getAsJsonArray();
        }
        return null;
    }

    private static String stripFences(String s) {
        s = s.trim();
        int start = s.indexOf('{');
        if (start > 0) s = s.substring(start);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3).trim();
        return s;
    }

    private static JsonObject tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Schneidet eine abgebrochene Antwort hinter dem letzten vollständigen Array-Element ab und schließt
     * alle offenen Klammern, z.B. {@code {"summary":"x","elements":[{..},{..},{"ty} → {..},{..}]}}.
     */
    static String repairTruncated(String s) {
        StringBuilder stack = new StringBuilder();
        boolean inString = false, escaped = false;
        int cut = -1;
        String stackAtCut = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> stack.append(c);
                case '}', ']' -> {
                    if (stack.isEmpty()) return null;
                    stack.setLength(stack.length() - 1);
                    if (c == '}' && !stack.isEmpty() && stack.charAt(stack.length() - 1) == '[') {
                        cut = i + 1;
                        stackAtCut = stack.toString();
                    }
                }
                default -> {}
            }
        }
        if (cut < 0) return null;
        StringBuilder out = new StringBuilder(s.substring(0, cut));
        for (int i = stackAtCut.length() - 1; i >= 0; i--) out.append(stackAtCut.charAt(i) == '[' ? ']' : '}');
        return out.toString();
    }
}
