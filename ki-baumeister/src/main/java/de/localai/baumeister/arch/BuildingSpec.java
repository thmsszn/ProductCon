package de.localai.baumeister.arch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Architektur-Entwurf, den die KI liefert: Gebäudetyp, Stil, Geschosse, Dach, Materialien und Ausstattung.
 * Die eigentliche Geometrie erzeugen die Generatoren – so entstehen immer saubere, vollständige Gebäude.
 */
public record BuildingSpec(
    String summary,
    Type type,
    Palette.Style style,
    int floors,
    Palette.Roof roof,
    String wall,
    String roofMaterial,
    String frame,
    Set<Feature> features,
    List<String> rooms,
    long seed) {

    public enum Type {
        HOUSE, COTTAGE, MANSION, TAVERN, BARN, MODERN_HOUSE, TOWER, LIGHTHOUSE, CASTLE, TEMPLE, CHURCH;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Type parse(String s) {
            if (s == null) return HOUSE;
            String k = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            for (Type t : values()) if (t.id().equals(k)) return t;
            if (k.contains("modern") || k.contains("villa")) return MODERN_HOUSE;
            if (k.contains("light")) return LIGHTHOUSE;
            if (k.contains("tower") || k.contains("turm")) return TOWER;
            if (k.contains("castle") || k.contains("burg") || k.contains("fort")) return CASTLE;
            if (k.contains("temple") || k.contains("tempel")) return TEMPLE;
            if (k.contains("church") || k.contains("kirche") || k.contains("chapel")) return CHURCH;
            if (k.contains("mansion") || k.contains("manor")) return MANSION;
            if (k.contains("tavern") || k.contains("inn") || k.contains("shop")) return TAVERN;
            if (k.contains("barn") || k.contains("stable") || k.contains("scheune")) return BARN;
            if (k.contains("cottage") || k.contains("hut") || k.contains("cabin")) return COTTAGE;
            return HOUSE;
        }
    }

    public enum Feature {
        CHIMNEY, PORCH, BALCONY, GARDEN, FENCE, PATH, SHUTTERS, FLOWER_BOXES, LANTERNS, TREES, POOL, INTERIOR;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Feature parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (Feature f : values()) if (f.id().equals(k)) return f;
            return null;
        }
    }

    public static final List<String> ROOMS = List.of(
        "living", "kitchen", "bedroom", "library", "dining", "storage", "workshop", "bar", "shop", "chapel", "office");

    public boolean has(Feature f) {
        return features.contains(f);
    }

    public BuildingSpec withSeed(long newSeed) {
        return new BuildingSpec(summary, type, style, floors, roof, wall, roofMaterial, frame, features, rooms, newSeed);
    }

    /** Liest die KI-Antwort tolerant ein (fehlende/unbekannte Werte bekommen sinnvolle Standards). */
    public static BuildingSpec parse(String json) {
        String text = json.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("Die KI-Antwort enthält keinen Entwurf (kein JSON).");
        JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
        return fromJson(o);
    }

    public static BuildingSpec fromJson(JsonObject o) {
        String summary = str(o, "summary");
        Type type = Type.parse(str(o, "type"));
        Palette.Style style = Palette.Style.parse(str(o, "style"));
        if (type == Type.MODERN_HOUSE && !o.has("style")) style = Palette.Style.MODERN;
        int floors = 2;
        if (o.has("floors") && o.get("floors").isJsonPrimitive()) {
            try { floors = (int) Math.round(o.get("floors").getAsDouble()); } catch (NumberFormatException ignored) {}
        }
        floors = Math.max(1, Math.min(6, floors));
        Palette.Roof roof = null;
        String r = str(o, "roof");
        if (r != null) {
            switch (r.trim().toLowerCase(Locale.ROOT)) {
                case "gable", "satteldach", "pitched" -> roof = Palette.Roof.GABLE;
                case "hip", "walmdach", "pyramid" -> roof = Palette.Roof.HIP;
                case "flat", "flachdach" -> roof = Palette.Roof.FLAT;
                case "cone", "spire", "kegel", "dome" -> roof = Palette.Roof.CONE;
                default -> roof = null;
            }
        }
        Set<Feature> features = EnumSet.noneOf(Feature.class);
        JsonArray fa = o.has("features") && o.get("features").isJsonArray() ? o.getAsJsonArray("features") : null;
        if (fa != null) for (JsonElement e : fa) {
            Feature f = e.isJsonPrimitive() ? Feature.parse(e.getAsString()) : null;
            if (f != null) features.add(f);
        }
        if (fa == null) features.addAll(EnumSet.of(Feature.GARDEN, Feature.PATH, Feature.INTERIOR, Feature.LANTERNS));
        features.add(Feature.INTERIOR);
        List<String> rooms = new ArrayList<>();
        if (o.has("rooms") && o.get("rooms").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("rooms")) {
                if (!e.isJsonPrimitive()) continue;
                String room = e.getAsString().trim().toLowerCase(Locale.ROOT);
                if (ROOMS.contains(room)) rooms.add(room);
            }
        }
        if (summary == null || summary.isBlank()) summary = "Gebäude";
        summary = summary.replaceAll("\\s+", " ").trim();
        if (summary.length() > 200) summary = summary.substring(0, 200) + "…";
        return new BuildingSpec(summary, type, style, floors, roof, str(o, "wall"), str(o, "roof_material"),
            str(o, "frame"), features, List.copyOf(rooms), summary.hashCode());
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("summary", summary);
        o.addProperty("type", type.id());
        o.addProperty("style", style.name().toLowerCase(Locale.ROOT));
        o.addProperty("floors", floors);
        if (roof != null) o.addProperty("roof", roof.name().toLowerCase(Locale.ROOT));
        if (wall != null) o.addProperty("wall", wall);
        if (roofMaterial != null) o.addProperty("roof_material", roofMaterial);
        if (frame != null) o.addProperty("frame", frame);
        JsonArray f = new JsonArray();
        for (Feature feature : features) f.add(feature.id());
        o.add("features", f);
        JsonArray r = new JsonArray();
        for (String room : rooms) r.add(room);
        o.add("rooms", r);
        return o;
    }

    /** JSON-Schema für Ollamas {@code format}: nur gültige Typen, Stile, Materialien und Features. */
    public static JsonObject schema() {
        JsonObject props = new JsonObject();
        props.add("summary", prop("string", null));
        props.add("type", prop("string", enumValues(Type.values())));
        props.add("style", prop("string", List.of("medieval", "rustic", "nordic", "fantasy", "victorian", "japanese",
            "desert", "modern", "classical", "stone")));
        props.add("floors", prop("integer", null));
        props.add("roof", prop("string", List.of("gable", "hip", "flat", "cone")));
        props.add("wall", prop("string", Materials.WALLS));
        props.add("roof_material", prop("string", Materials.familyKeys()));
        props.add("frame", prop("string", Materials.FRAMES));
        JsonObject features = new JsonObject();
        features.addProperty("type", "array");
        features.add("items", prop("string", enumValues(Feature.values())));
        props.add("features", features);
        JsonObject rooms = new JsonObject();
        rooms.addProperty("type", "array");
        rooms.add("items", prop("string", ROOMS));
        props.add("rooms", rooms);
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.add("properties", props);
        JsonArray required = new JsonArray();
        for (String k : List.of("summary", "type", "style", "floors", "roof", "wall", "roof_material", "frame", "features", "rooms")) {
            required.add(k);
        }
        root.add("required", required);
        return root;
    }

    private static List<String> enumValues(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> v : values) out.add(v.name().toLowerCase(Locale.ROOT));
        return out;
    }

    private static JsonObject prop(String type, List<String> values) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        if (values != null) {
            JsonArray a = new JsonArray();
            values.forEach(a::add);
            p.add("enum", a);
        }
        return p;
    }
}
