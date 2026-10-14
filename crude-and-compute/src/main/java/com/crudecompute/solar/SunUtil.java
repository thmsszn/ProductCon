package com.crudecompute.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Solar irradiance model: sun elevation over the day, reduced by rain and thunderstorms; zero without sky light. */
public final class SunUtil {
    private SunUtil() {
    }

    /** 0..1 fraction of the maximum irradiance at the given position (ignoring sky obstruction). */
    public static float irradiance(Level level) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0f;
        }
        long t = level.getDayTime() % 24000L;
        if (t >= 12000L) {
            return 0f;
        }
        float elevation = (float) Math.sin(Math.PI * t / 12000.0);
        float weather = 1f - 0.65f * level.getRainLevel(1f) - 0.2f * level.getThunderLevel(1f);
        return Math.max(0f, (float) Math.pow(elevation, 0.85) * Math.max(0.1f, weather));
    }

    public static boolean exposed(Level level, BlockPos pos) {
        return level.canSeeSky(pos.above());
    }
}
