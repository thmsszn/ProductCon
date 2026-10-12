package de.localai.baumeister;

import java.util.List;

/** Bauplan in lokalen Koordinaten (x = Breite, y = Höhe, z = Tiefe, Vorderseite bei z = 0). */
public record BuildPlan(String summary, List<Element> elements) {

    /** Ein Bauelement: Form zwischen zwei Ecken, gefüllt mit einem Block(-zustand). */
    public record Element(Shape shape, int x1, int y1, int z1, int x2, int y2, int z2, String block) {}

    public enum Shape {
        FILL, HOLLOW, WALLS, ROOF, CYLINDER, BLOCK;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static Shape parse(String s) {
            if (s == null) return BLOCK;
            return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "fill", "box", "solid", "cuboid", "floor", "platform" -> FILL;
                case "hollow", "room", "shell", "hollow_box" -> HOLLOW;
                case "walls", "wall", "ring", "outline" -> WALLS;
                case "roof", "hip_roof", "pyramid" -> ROOF;
                case "cylinder", "tower", "circle", "round" -> CYLINDER;
                default -> BLOCK;
            };
        }
    }
}
