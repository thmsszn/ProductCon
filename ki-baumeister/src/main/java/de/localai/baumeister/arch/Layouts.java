package de.localai.baumeister.arch;

import java.util.ArrayList;
import java.util.List;

/**
 * Grundriss-Vorlagen für den freien Bauplan. Jede Vorlage liefert Baukörper in Prozent der nutzbaren Fläche; der
 * Seed (und damit „Variante“) wählt Vorlage und Spiegelung, sodass derselbe Entwurf unterschiedliche Grundstrukturen
 * bekommt.
 */
final class Layouts {
    enum Kind {
        BLOCK, L_SHAPE, T_SHAPE, U_SHAPE, ANNEX_TOWER, CROSS, LONGHOUSE, STEPPED,
        /** Steinsockel + Holzobergeschoss mit Überhang, Pultdach-Anbau und aufgesetztem Quergiebel. */
        SPLIT_HOUSE,
        /** Winkelhaus auf Steinsockel: vorderer Flügel mit Überhang, Haupthaus quer, Pultdach-Anbau. */
        SPLIT_L;

        /** Gestaffelter Fachwerkbau (Steinsockel, Überhang, Außentreppe, Garten) in {@link FreeBuilder}. */
        boolean split() {
            return this == SPLIT_HOUSE || this == SPLIT_L;
        }
    }

    private Layouts() {}

    static Kind pick(BuildingSpec spec, Rng rng) {
        String force = System.getProperty("baumeister.layout"); // nur für Vorschau/Tests
        if (force != null) {
            try {
                return Kind.valueOf(force);
            } catch (IllegalArgumentException ignored) {
                // unbekannter Name: normale Wahl
            }
        }
        List<Kind> pool = new ArrayList<>();
        switch (spec.type()) {
            case COTTAGE -> pool.addAll(List.of(Kind.BLOCK, Kind.L_SHAPE, Kind.ANNEX_TOWER, Kind.LONGHOUSE));
            case BARN -> pool.addAll(List.of(Kind.LONGHOUSE, Kind.BLOCK, Kind.L_SHAPE));
            case TAVERN -> pool.addAll(List.of(Kind.L_SHAPE, Kind.T_SHAPE, Kind.U_SHAPE, Kind.STEPPED, Kind.ANNEX_TOWER));
            case MANSION -> pool.addAll(List.of(Kind.U_SHAPE, Kind.T_SHAPE, Kind.CROSS, Kind.L_SHAPE, Kind.STEPPED,
                Kind.ANNEX_TOWER));
            default -> pool.addAll(List.of(Kind.BLOCK, Kind.L_SHAPE, Kind.T_SHAPE, Kind.ANNEX_TOWER, Kind.CROSS,
                Kind.STEPPED, Kind.LONGHOUSE, Kind.U_SHAPE));
        }
        boolean timberStyle = spec.style() == Palette.Style.MEDIEVAL || spec.style() == Palette.Style.RUSTIC
            || spec.style() == Palette.Style.NORDIC || spec.style() == Palette.Style.VIKING;
        boolean big = spec.type() == BuildingSpec.Type.HOUSE || spec.type() == BuildingSpec.Type.TAVERN
            || spec.type() == BuildingSpec.Type.MANSION;
        if (timberStyle && big) pool.addAll(List.of(Kind.SPLIT_HOUSE, Kind.SPLIT_HOUSE, Kind.SPLIT_L, Kind.SPLIT_L));
        if (spec.style() == Palette.Style.NORDIC) pool.addAll(List.of(Kind.LONGHOUSE, Kind.LONGHOUSE, Kind.LONGHOUSE));
        if (spec.style() == Palette.Style.JAPANESE) pool.addAll(List.of(Kind.L_SHAPE, Kind.U_SHAPE, Kind.LONGHOUSE));
        if (spec.style() == Palette.Style.VIKING) pool.addAll(List.of(Kind.LONGHOUSE, Kind.LONGHOUSE, Kind.LONGHOUSE, Kind.T_SHAPE));
        if (spec.style() == Palette.Style.GOTHIC) pool.addAll(List.of(Kind.CROSS, Kind.CROSS, Kind.ANNEX_TOWER, Kind.ANNEX_TOWER));
        if (spec.style() == Palette.Style.ORIENTAL) pool.addAll(List.of(Kind.U_SHAPE, Kind.ANNEX_TOWER, Kind.STEPPED, Kind.CROSS));
        if (spec.style() == Palette.Style.STEAMPUNK) pool.addAll(List.of(Kind.ANNEX_TOWER, Kind.ANNEX_TOWER, Kind.STEPPED));
        return rng.pick(pool);
    }

    /** Baukörper der gewählten Vorlage, ggf. an der Mittelachse gespiegelt. */
    static List<BuildingSpec.Part> parts(BuildingSpec spec, Kind kind, Rng rng) {
        int f = Math.max(1, Math.min(6, spec.type() == BuildingSpec.Type.COTTAGE ? 1 : spec.floors()));
        int lo = Math.max(1, f - 1);
        String roof = null;
        List<BuildingSpec.Part> p = new ArrayList<>();
        switch (kind) {
            case BLOCK -> p.add(rect(5, 0, 90, 100, f, 0, roof));
            case L_SHAPE -> {
                p.add(rect(0, 0, 55, 100, f, 0, roof));
                p.add(rect(0, 45, 100, 55, lo, 0, roof));
            }
            case T_SHAPE -> {
                p.add(rect(0, 0, 100, 42, f, 0, roof));
                p.add(rect(28, 30, 44, 70, f, 0, roof));
            }
            case U_SHAPE -> {
                p.add(rect(0, 0, 34, 100, f, 0, roof));
                p.add(rect(66, 0, 34, 100, f, 0, roof));
                p.add(rect(0, 66, 100, 34, lo, 0, roof));
            }
            case ANNEX_TOWER -> {
                p.add(rect(0, 25, 78, 75, f, 0, roof));
                p.add(rect(60, 0, 40, 55, lo, 0, roof));
                p.add(new BuildingSpec.Part("round", 0, 0, 30, 30, Math.min(6, f + 1), 0, "cone"));
            }
            case CROSS -> {
                p.add(rect(0, 30, 100, 40, f, 0, roof));
                p.add(rect(30, 0, 40, 100, f, 0, roof));
            }
            case LONGHOUSE -> {
                p.add(rect(0, 18, 100, 64, f, 0, roof));
                p.add(rect(38, 0, 24, 30, 1, 0, "gable"));
            }
            case STEPPED -> {
                p.add(rect(0, 15, 100, 85, f, 0, roof));
                p.add(rect(0, 0, 55, 45, lo, 0, roof));
                p.add(rect(25, 30, 50, 60, 1, f, roof));
            }
            case SPLIT_HOUSE -> {
                int fs = Math.max(2, Math.min(3, f));
                p.add(rect(0, 18, 54, 72, fs, 0, roof));
                p.add(rect(54, 36, 30, 52, 1, 0, "shed"));
                if (fs == 2) p.add(rect(6, 18, 42, 30, 1, fs, roof));
            }
            case SPLIT_L -> {
                int fs = Math.max(2, Math.min(3, f));
                p.add(rect(0, 0, 42, 64, fs, 0, roof));
                p.add(rect(0, 40, 100, 52, fs, 0, roof));
                p.add(rect(42, 22, 34, 18, 1, 0, "shed"));
            }
        }
        if (rng.chance(0.5)) {
            List<BuildingSpec.Part> mirrored = new ArrayList<>();
            for (BuildingSpec.Part q : p) {
                mirrored.add(new BuildingSpec.Part(q.shape(), Math.max(0, 100 - q.x() - q.w()), q.z(), q.w(), q.d(),
                    q.floors(), q.base(), q.roof()));
            }
            return mirrored;
        }
        return p;
    }

    private static BuildingSpec.Part rect(int x, int z, int w, int d, int floors, int base, String roof) {
        return new BuildingSpec.Part("rect", x, z, w, d, floors, base, roof);
    }
}
