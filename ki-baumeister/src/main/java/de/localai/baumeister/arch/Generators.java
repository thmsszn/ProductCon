package de.localai.baumeister.arch;

/** Generatoren für Sonderbauten. Solange ein Typ keinen eigenen Generator hat, entsteht ein passendes Haus. */
final class Generators {
    private Generators() {}

    static Voxels tower(Site site) {
        return new TowerGenerator(site).build();
    }

    static Voxels castle(Site site) {
        return new HouseGenerator(site).build();
    }

    static Voxels temple(Site site) {
        return new HouseGenerator(site).build();
    }

    static Voxels church(Site site) {
        return new HouseGenerator(site).build();
    }
}
