package de.localai.baumeister.arch;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Einstiegspunkt: Entwurf + Bauplatzgröße → fertiges Gebäude als Voxel-Raster (lokale Koordinaten). */
public final class Architect {
    private Architect() {}

    /**
     * @param blockExists prüft, ob ein Blockname (z.B. "minecraft:calcite") im Spiel existiert;
     *                    unbekannte KI-Materialien fallen auf die Stil-Palette zurück.
     */
    public static Voxels generate(BuildingSpec spec, int w, int h, int d, Predicate<String> blockExists) {
        Palette.Style style = spec.style();
        if (spec.type() == BuildingSpec.Type.MODERN_HOUSE) style = Palette.Style.MODERN;
        Palette palette = Palette.of(style);
        if (spec.wall() != null && Materials.WALLS.contains(spec.wall()) && blockExists.test(B.id(spec.wall()))) {
            palette = palette.withWall(spec.wall());
        }
        if (spec.frame() != null && Materials.FRAMES.contains(spec.frame()) && blockExists.test(B.id(spec.frame()))) {
            palette = palette.withFrame(spec.frame());
        }
        Materials.Family roofFamily = Materials.family(spec.roofMaterial(), null);
        if (roofFamily != null && blockExists.test(B.id(roofFamily.stairs())) && blockExists.test(B.id(roofFamily.slab()))
            && blockExists.test(B.id(roofFamily.block()))) {
            palette = palette.withRoof(roofFamily);
        }
        Site site = new Site(spec, palette, w, h, d);
        if (!spec.parts().isEmpty() && spec.type() != BuildingSpec.Type.MODERN_HOUSE) return new FreeBuilder(site).build();
        return switch (spec.type()) {
            case TOWER, LIGHTHOUSE -> Generators.tower(site);
            case CASTLE -> Generators.castle(site);
            case TEMPLE -> Generators.temple(site);
            case CHURCH -> Generators.church(site);
            case MODERN_HOUSE -> Generators.modern(site);
            default -> useFree(spec) ? new FreeBuilder(site).build() : new HouseGenerator(site).build();
        };
    }

    /** Wohnhaus-Typen: meist freier Grundriss, gelegentlich der klassische Rechteckbau. */
    private static boolean useFree(BuildingSpec spec) {
        if (spec.type() == BuildingSpec.Type.CUSTOM) return true;
        return new Rng(spec.seed() * 13 + 5).nextInt(100) < FREE_SHARE;
    }

    static int FREE_SHARE = 75;

    /** Entwurf ohne KI aus Schlüsselwörtern (Fallback, wenn Ollama nicht erreichbar ist). */
    public static BuildingSpec fromKeywords(String prompt) {
        String t = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        BuildingSpec.Type type = BuildingSpec.Type.HOUSE;
        if (has(t, "leuchtturm", "lighthouse")) type = BuildingSpec.Type.LIGHTHOUSE;
        else if (has(t, "turm", "tower")) type = BuildingSpec.Type.TOWER;
        else if (has(t, "burg", "schloss", "castle", "festung", "fort")) type = BuildingSpec.Type.CASTLE;
        else if (has(t, "tempel", "temple", "schrein", "shrine")) type = BuildingSpec.Type.TEMPLE;
        else if (has(t, "kirche", "kapelle", "church", "chapel")) type = BuildingSpec.Type.CHURCH;
        else if (has(t, "villa", "modern")) type = BuildingSpec.Type.MODERN_HOUSE;
        else if (has(t, "taverne", "gasthaus", "wirtshaus", "tavern", "inn")) type = BuildingSpec.Type.TAVERN;
        else if (has(t, "scheune", "stall", "barn")) type = BuildingSpec.Type.BARN;
        else if (has(t, "herrenhaus", "anwesen", "mansion")) type = BuildingSpec.Type.MANSION;
        else if (has(t, "hütte", "huette", "cottage", "cabin")) type = BuildingSpec.Type.COTTAGE;

        Palette.Style style = switch (type) {
            case MODERN_HOUSE -> Palette.Style.MODERN;
            case CASTLE, TOWER, LIGHTHOUSE, CHURCH -> Palette.Style.STONE;
            case TEMPLE -> Palette.Style.CLASSICAL;
            case BARN, COTTAGE -> Palette.Style.RUSTIC;
            default -> Palette.Style.MEDIEVAL;
        };
        if (has(t, "japan", "asiat", "pagode", "schrein", "shrine")) style = Palette.Style.JAPANESE;
        else if (has(t, "wüste", "wueste", "desert", "sand", "orient")) style = Palette.Style.DESERT;
        else if (has(t, "nordisch", "wikinger", "viking", "nordic", "skandinav")) style = Palette.Style.NORDIC;
        else if (has(t, "fantasy", "magisch", "elfen", "zauber")) style = Palette.Style.FANTASY;
        else if (has(t, "viktorian", "victorian", "backstein")) style = Palette.Style.VICTORIAN;
        else if (has(t, "antik", "griech", "römisch", "roemisch", "säulen")) style = Palette.Style.CLASSICAL;
        else if (has(t, "rustikal", "holzhaus", "bauernhaus", "blockhütte")) style = Palette.Style.RUSTIC;

        int floors = 2;
        if (has(t, "einstöckig", "eingeschossig", "bungalow", "klein")) floors = 1;
        if (has(t, "dreistöckig", "drei stock", "3 stock")) floors = 3;
        if (has(t, "groß", "gross", "riesig", "big", "large")) floors = Math.max(floors, 3);

        Set<BuildingSpec.Feature> features = EnumSet.of(BuildingSpec.Feature.PATH, BuildingSpec.Feature.GARDEN,
            BuildingSpec.Feature.INTERIOR, BuildingSpec.Feature.LANTERNS);
        if (has(t, "kamin", "schornstein", "chimney")) features.add(BuildingSpec.Feature.CHIMNEY);
        if (has(t, "veranda", "vordach", "porch")) features.add(BuildingSpec.Feature.PORCH);
        if (has(t, "balkon", "balcony")) features.add(BuildingSpec.Feature.BALCONY);
        if (has(t, "zaun", "fence")) features.add(BuildingSpec.Feature.FENCE);
        if (has(t, "baum", "bäume", "tree")) features.add(BuildingSpec.Feature.TREES);
        if (has(t, "pool", "schwimm")) features.add(BuildingSpec.Feature.POOL);
        if (has(t, "blumen", "flower")) features.add(BuildingSpec.Feature.FLOWER_BOXES);
        if (has(t, "fensterläden", "shutter")) features.add(BuildingSpec.Feature.SHUTTERS);

        String summary = prompt == null || prompt.isBlank() ? "Gebäude" : prompt.trim();
        return new BuildingSpec(summary, type, style, floors, null, null, null, null, features, List.of(), summary.hashCode(), List.of());
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}
