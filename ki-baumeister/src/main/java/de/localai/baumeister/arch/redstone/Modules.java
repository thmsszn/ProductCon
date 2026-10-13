package de.localai.baumeister.arch.redstone;

import java.util.ArrayList;
import java.util.List;

/** Verzeichnis aller Redstone-Module; jede Gruppe liegt in einer eigenen Klasse, damit parallele Arbeit nicht kollidiert. */
public final class Modules {
    private Modules() {}

    public static List<Module> all() {
        List<Module> out = new ArrayList<>();
        out.addAll(DoorModules.list());
        out.addAll(FarmModules.list());
        out.addAll(LogicModules.list());
        return List.copyOf(out);
    }

    public static Module get(String id) {
        if (id == null) return null;
        for (Module m : all()) if (m.id().equals(id)) return m;
        return null;
    }
}
