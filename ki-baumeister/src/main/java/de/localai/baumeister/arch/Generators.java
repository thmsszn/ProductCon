package de.localai.baumeister.arch;

/** Generatoren für Sonderbauten (Turm/Leuchtturm, Burg, Tempel/Schrein, Kirche, moderne Villa). */
final class Generators {
    private Generators() {}

    static Voxels tower(Site site) {
        return new TowerGenerator(site).build();
    }

    static Voxels castle(Site site) {
        return new CastleGenerator(site).build();
    }

    static Voxels temple(Site site) {
        return new TempleGenerator(site).build();
    }

    static Voxels modern(Site site) {
        return new ModernGenerator(site).build();
    }

    static Voxels church(Site site) {
        return new ChurchGenerator(site).build();
    }
}
