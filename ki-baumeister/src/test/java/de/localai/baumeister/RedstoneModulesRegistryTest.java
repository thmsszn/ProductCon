package de.localai.baumeister;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.localai.baumeister.arch.Palette;
import de.localai.baumeister.arch.Rng;
import de.localai.baumeister.arch.Voxels;
import de.localai.baumeister.arch.redstone.Module;
import de.localai.baumeister.arch.redstone.Modules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Jedes Redstone-Modul muss in allen Stilen nur gültige Blockzustände erzeugen und in seinen Platz passen. */
class RedstoneModulesRegistryTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean valid(String block) {
        try {
            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, block, false);
            return PlanCompiler.resolve(block) != null;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    @Test
    void idsAreUniqueAndDescribed() {
        Set<String> ids = new HashSet<>();
        for (Module m : Modules.all()) {
            assertTrue(ids.add(m.id()), "doppelte Kennung " + m.id());
            assertTrue(!m.title().isBlank() && !m.description().isBlank(), "Beschreibung fehlt: " + m.id());
            int[] s = m.size();
            assertEquals(3, s.length);
            assertTrue(s[0] > 0 && s[1] > 0 && s[2] > 0, "Größe: " + m.id());
        }
    }

    @Test
    void everyModuleBuildsOnlyValidBlockStates() {
        List<String> bad = new ArrayList<>();
        for (Module m : Modules.all()) {
            for (Palette.Style style : Palette.Style.values()) {
                int[] s = m.size();
                Voxels v = new Voxels(s[0] + 4, s[1] + 4, s[2] + 4);
                v.fillAll("minecraft:air");
                m.build(v, 2, 1, 2, Palette.of(style), new Rng(7));
                Set<String> seen = new HashSet<>();
                for (int y = 0; y < v.h; y++)
                    for (int z = 0; z < v.d; z++)
                        for (int x = 0; x < v.w; x++) {
                            String b = v.get(x, y, z);
                            if (b == null || !seen.add(b)) continue;
                            if (!valid(b)) bad.add(m.id() + "/" + style + ": " + b);
                        }
            }
        }
        assertTrue(bad.isEmpty(), "Ungültige Blockzustände: " + bad);
    }
}
