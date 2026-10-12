package de.localai.baumeister;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.localai.baumeister.arch.BuildingSpec;
import de.localai.baumeister.arch.Voxels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Wandelt das Voxel-Raster der Architektur-Engine in sortierte, gedrehte Blockplatzierungen um. */
public final class PlanCompiler {
    /** Blöcke, die nie gesetzt werden. */
    private static final Set<String> FORBIDDEN = Set.of(
        "tnt", "command_block", "chain_command_block", "repeating_command_block", "structure_block",
        "structure_void", "jigsaw", "barrier", "light", "lava", "fire", "soul_fire", "nether_portal",
        "end_portal", "end_gateway", "spawner", "trial_spawner", "moving_piston", "piston_head", "bedrock",
        "test_block", "test_instance_block", "reinforced_deepslate", "end_portal_frame");

    public record Placement(BlockPos pos, BlockState state) {}

    public record Material(Block block, int count) {}

    public record Result(
        BuildingSpec spec,
        Voxels voxels,
        BuildRegion region,
        List<Placement> placements,
        List<Material> materials,
        int solidBlocks,
        int airBlocks,
        List<String> rejected) {}

    private PlanCompiler() {}

    /** Prüft, ob ein Blockzustand-Text im Spiel existiert (für die Material-Auswahl der Engine). */
    public static boolean exists(String block) {
        return resolve(block) != null;
    }

    public static Result compile(BuildingSpec spec, Voxels vox, BuildRegion region) {
        int w = vox.w, h = vox.h, d = vox.d;
        BlockState[] grid = new BlockState[w * h * d];
        Map<String, BlockState> cache = new HashMap<>();
        Set<String> rejected = new LinkedHashSet<>();
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    String s = vox.get(x, y, z);
                    if (s == null) continue;
                    BlockState state = cache.computeIfAbsent(s, PlanCompiler::resolve);
                    if (state == null) {
                        rejected.add(s);
                        continue;
                    }
                    grid[(y * d + z) * w + x] = state;
                }
        completeMultiBlocks(grid, w, h, d);

        List<Placement> air = new ArrayList<>();
        List<Placement> solid = new ArrayList<>();
        List<Placement> attached = new ArrayList<>();
        Map<Block, Integer> counts = new IdentityHashMap<>();
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    BlockState s = grid[(y * d + z) * w + x];
                    if (s == null) continue;
                    Placement p = new Placement(region.toWorld(x, y, z), s.rotate(region.rotation()));
                    if (s.isAir()) air.add(p);
                    else {
                        counts.merge(s.getBlock(), 1, Integer::sum);
                        if (s.canOcclude()) solid.add(p);
                        else attached.add(p);
                    }
                }
        // Erst Luft (von oben nach unten), dann tragende Blöcke, zuletzt Anbauteile (Türen, Fackeln, Glas …).
        Collections.reverse(air);
        List<Placement> all = new ArrayList<>(air.size() + solid.size() + attached.size());
        all.addAll(air);
        all.addAll(solid);
        all.addAll(attached);

        List<Material> materials = new ArrayList<>();
        counts.forEach((b, c) -> materials.add(new Material(b, c)));
        materials.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return new Result(spec, vox, region, List.copyOf(all), List.copyOf(materials),
            solid.size() + attached.size(), air.size(), List.copyOf(rejected));
    }

    /** Block-String (z.B. {@code oak_stairs[facing=south]}) → Zustand; {@code null} wenn unbekannt/verboten. */
    static BlockState resolve(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        int open = s.indexOf('[');
        s = open < 0 ? s.replace(' ', '_') : s.substring(0, open).trim().replace(' ', '_') + s.substring(open).replace(" ", "");
        BlockState state = parse(s);
        if (state == null && open > 0) state = parse(s.substring(0, s.indexOf('[')));
        if (state == null) return null;
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if ("minecraft".equals(id.getNamespace()) && FORBIDDEN.contains(id.getPath())) return null;
        if (state.getBlock() == Blocks.CAVE_AIR || state.getBlock() == Blocks.VOID_AIR) return Blocks.AIR.defaultBlockState();
        return state;
    }

    private static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** Ergänzt obere Türhälften und Bett-Kopfteile, entfernt verwaiste Hälften. */
    private static void completeMultiBlocks(BlockState[] grid, int w, int h, int d) {
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    int i = (y * d + z) * w + x;
                    BlockState s = grid[i];
                    if (s == null) continue;
                    if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                        if (s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                            if (y + 1 < h) grid[i + w * d] = s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                            else grid[i] = null;
                        } else {
                            BlockState below = y > 0 ? grid[i - w * d] : null;
                            if (below == null || below.getBlock() != s.getBlock()) grid[i] = null;
                        }
                    } else if (s.hasProperty(BlockStateProperties.BED_PART) && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        && s.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                        Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        int hx = x + (f == Direction.EAST ? 1 : f == Direction.WEST ? -1 : 0);
                        int hz = z + (f == Direction.SOUTH ? 1 : f == Direction.NORTH ? -1 : 0);
                        if (hx >= 0 && hx < w && hz >= 0 && hz < d) grid[(y * d + hz) * w + hx] = s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                        else grid[i] = null;
                    }
                }
    }
}
