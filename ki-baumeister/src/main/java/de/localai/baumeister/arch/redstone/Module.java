package de.localai.baumeister.arch.redstone;

import de.localai.baumeister.arch.Palette;
import de.localai.baumeister.arch.Rng;
import de.localai.baumeister.arch.Voxels;

/**
 * Ein geprüftes Redstone-Bauteil (Tür, Farm, Schaltung …). Module werden fest als Bauplan beschrieben und im
 * Redstone-Simulator ({@link Sim}) auf ihre Logik getestet; die KI wählt nur Modul und Position.
 * Alle Module sind nach Norden ausgerichtet (Front = -z), die Mod dreht das fertige Gebäude später zum Spieler.
 */
public interface Module {
    /** Stabile Kennung, z.B. "piston_door_2x2". */
    String id();

    /** Deutscher Name für Menü und Beschreibung. */
    String title();

    /** Ein Satz auf Deutsch: was es tut und wie man es bedient (z.B. "Hebel rechts neben dem Eingang"). */
    String description();

    /** Benötigter Platz {Breite x, Höhe y, Tiefe z} inklusive Bedienelementen und Zuleitungen. */
    int[] size();

    /**
     * Schreibt das Modul mit der linken, vorderen, unteren Ecke bei (ox, oy, oz) in {@code v}. Zellen außerhalb des
     * Rasters werden ignoriert. Das Modul darf Umgebung (Boden, Wände) selbst mitbauen, soweit es zur Funktion gehört.
     */
    void build(Voxels v, int ox, int oy, int oz, Palette p, Rng rng);
}
