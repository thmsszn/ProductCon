package de.localai.baumeister;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
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

/** Wandelt einen {@link BuildPlan} in eine sortierte Liste konkreter Blockplatzierungen um. */
public final class PlanCompiler {
    /** Blöcke, die die KI nie setzen darf. */
    private static final Set<String> FORBIDDEN = Set.of(
        "tnt", "command_block", "chain_command_block", "repeating_command_block", "structure_block",
        "structure_void", "jigsaw", "barrier", "light", "lava", "fire", "soul_fire", "nether_portal",
        "end_portal", "end_gateway", "spawner", "trial_spawner", "moving_piston", "piston_head", "bedrock",
        "test_block", "test_instance_block", "reinforced_deepslate", "end_portal_frame");

    public record Placement(BlockPos pos, BlockState state) {}

    public record Material(Block block, int count) {}

    public record Result(
        BuildPlan plan,
        BuildRegion region,
        List<Placement> placements,
        List<Material> materials,
        int solidBlocks,
        int airBlocks,
        List<String> rejected) {}

    private PlanCompiler() {}

    public static Result compile(BuildPlan plan, BuildRegion region, boolean clearArea) {
        int w = region.localWidth(), h = region.localHeight(), d = region.localDepth();
        BlockState[] grid = new BlockState[w * h * d];
        Map<String, BlockState> cache = new HashMap<>();
        Set<String> rejected = new LinkedHashSet<>();
        Grid g = new Grid(grid, w, h, d);

        for (BuildPlan.Element e : plan.elements()) {
            BlockState state = cache.computeIfAbsent(e.block(), PlanCompiler::resolve);
            if (state == null) {
                rejected.add(e.block());
                continue;
            }
            int x1 = clamp(Math.min(e.x1(), e.x2()), w), x2 = clamp(Math.max(e.x1(), e.x2()), w);
            int y1 = clamp(Math.min(e.y1(), e.y2()), h), y2 = clamp(Math.max(e.y1(), e.y2()), h);
            int z1 = clamp(Math.min(e.z1(), e.z2()), d), z2 = clamp(Math.max(e.z1(), e.z2()), d);
            switch (e.shape()) {
                case BLOCK -> {
                    if (inside(e.x1(), w) && inside(e.y1(), h) && inside(e.z1(), d)) g.set(e.x1(), e.y1(), e.z1(), state);
                }
                case FILL -> g.box(x1, y1, z1, x2, y2, z2, (x, y, z) -> state);
                case HOLLOW -> g.box(x1, y1, z1, x2, y2, z2, (x, y, z) ->
                    x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2 ? state : Blocks.AIR.defaultBlockState());
                case WALLS -> g.box(x1, y1, z1, x2, y2, z2, (x, y, z) ->
                    x == x1 || x == x2 || z == z1 || z == z2 ? state : null);
                case ROOF -> roof(g, x1, clamp(e.y1(), h), z1, x2, e.y2() > e.y1() ? clamp(e.y2(), h) : h - 1, z2, state);
                case CYLINDER -> cylinder(g, x1, y1, z1, x2, y2, z2, state);
            }
        }

        completeMultiBlocks(grid, w, h, d);

        List<Placement> air = new ArrayList<>();
        List<Placement> solid = new ArrayList<>();
        List<Placement> attached = new ArrayList<>();
        Map<Block, Integer> counts = new IdentityHashMap<>();
        BlockState airState = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    BlockState s = grid[(y * d + z) * w + x];
                    if (s == null) {
                        if (!clearArea) continue;
                        s = airState;
                    }
                    Placement p = new Placement(region.toWorld(x, y, z), s.rotate(region.rotation()));
                    if (s.isAir()) air.add(p);
                    else {
                        counts.merge(s.getBlock(), 1, Integer::sum);
                        if (s.canOcclude()) solid.add(p);
                        else attached.add(p);
                    }
                }
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

        return new Result(plan, region, List.copyOf(all), List.copyOf(materials),
            solid.size() + attached.size(), air.size(), List.copyOf(rejected));
    }

    /** Block-String (z.B. {@code oak_stairs[facing=south]}) → Zustand; {@code null} wenn unbekannt/verboten. */
    static BlockState resolve(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.isEmpty()) return null;
        BlockState state = parse(s);
        if (state == null) {
            int bracket = s.indexOf('[');
            if (bracket > 0) state = parse(s.substring(0, bracket));
        }
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

    /** Walmdach: Ringe, die pro Ebene auf jeder Seite um 1 kleiner werden; Treppen zeigen nach außen. */
    private static void roof(Grid g, int x1, int y1, int z1, int x2, int yMax, int z2, BlockState state) {
        boolean stairs = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            && state.hasProperty(BlockStateProperties.STAIRS_SHAPE);
        for (int i = 0; ; i++) {
            int y = y1 + i, ax = x1 + i, bx = x2 - i, az = z1 + i, bz = z2 - i;
            if (y > yMax || y >= g.h || ax > bx || az > bz) break;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    if (x != ax && x != bx && z != az && z != bz) continue;
                    BlockState s = state;
                    if (stairs) {
                        // Treppen steigen zur Mitte hin an: "facing" zeigt zur Dachmitte.
                        Direction dir = z == az ? Direction.SOUTH : z == bz ? Direction.NORTH : x == ax ? Direction.EAST : Direction.WEST;
                        s = state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                    }
                    g.set(x, y, z, s);
                }
            }
        }
    }

    /** Runde, hohle Wand (Turm); innen wird Luft gesetzt. */
    private static void cylinder(Grid g, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        double cx = (x1 + x2 + 1) / 2.0, cz = (z1 + z2 + 1) / 2.0;
        double rx = (x2 - x1 + 1) / 2.0, rz = (z2 - z1 + 1) / 2.0;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (!inEllipse(x, z, cx, cz, rx, rz)) continue;
                boolean edge = !inEllipse(x + 1, z, cx, cz, rx, rz) || !inEllipse(x - 1, z, cx, cz, rx, rz)
                    || !inEllipse(x, z + 1, cx, cz, rx, rz) || !inEllipse(x, z - 1, cx, cz, rx, rz);
                for (int y = y1; y <= y2; y++) g.set(x, y, z, edge ? state : air);
            }
        }
    }

    private static boolean inEllipse(int x, int z, double cx, double cz, double rx, double rz) {
        double dx = (x + 0.5 - cx) / rx, dz = (z + 0.5 - cz) / rz;
        return dx * dx + dz * dz <= 1.0;
    }

    /** Ergänzt obere Türhälften und Bett-Kopfteile, entfernt verwaiste Hälften. */
    private static void completeMultiBlocks(BlockState[] grid, int w, int h, int d) {
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
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
                        int hx = x + stepX(f), hz = z + stepZ(f);
                        if (inside(hx, w) && inside(hz, d)) grid[(y * d + hz) * w + hx] = s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                        else grid[i] = null;
                    }
                }
            }
        }
    }

    private static int stepX(Direction d) {
        return d == Direction.EAST ? 1 : d == Direction.WEST ? -1 : 0;
    }

    private static int stepZ(Direction d) {
        return d == Direction.SOUTH ? 1 : d == Direction.NORTH ? -1 : 0;
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    private static boolean inside(int v, int size) {
        return v >= 0 && v < size;
    }

    @FunctionalInterface
    private interface CellFn {
        BlockState at(int x, int y, int z);
    }

    private record Grid(BlockState[] cells, int w, int h, int d) {
        void set(int x, int y, int z, BlockState s) {
            if (s != null) cells[(y * d + z) * w + x] = s;
        }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, CellFn fn) {
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    for (int x = x1; x <= x2; x++) set(x, y, z, fn.at(x, y, z));
        }
    }
}
