package net.fly.adrenaline.worldgen;

import java.util.Arrays;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.fly.adrenaline.natives.AdrenalineNatives.Feature;
import net.fly.adrenaline.natives.NativeRuntimeStats;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;

public final class OreCandidateBatch {

    private static final int MAX_WIDTH = 40;
    private static final boolean STANDARD_HEIGHT_CHECK = standardHeightCheck();
    private static final ThreadLocal<int[]> SCRATCH = ThreadLocal.withInitial(() -> new int[MAX_WIDTH * MAX_WIDTH * MAX_WIDTH]);

    private OreCandidateBatch() {
    }

    public static int[] prepare(WorldGenLevel level, double[] spheres, int x, int y, int z, int width, int height) {
        if (!STANDARD_HEIGHT_CHECK || level.getClass() != WorldGenRegion.class || width <= 0 || width > MAX_WIDTH || height <= 0 || height > MAX_WIDTH
            || spheres.length < 4 || spheres.length > 64 * 4 || !AdrenalineConfig.get().oreCandidateBatching
            || !AdrenalineNatives.isAvailable(Feature.ORE_CANDIDATES)) {
            return null;
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        if (level.isOutsideBuildHeight(minY) || level.isOutsideBuildHeight(maxY - 1)) {
            return null;
        }

        int[] scratch = SCRATCH.get();
        long started = NativeRuntimeStats.begin(NativeRuntimeStats.Stage.ORE_CANDIDATES);
        try {
            int count = collect0(spheres, spheres.length >>> 2, scratch, x, y, z, width, height, minY, maxY);
            if (count < 0) {
                return null;
            }
            NativeRuntimeStats.end(NativeRuntimeStats.Stage.ORE_CANDIDATES, started);
            return Arrays.copyOf(scratch, count);
        } catch (LinkageError | RuntimeException exception) {
            AdrenalineNatives.disable(Feature.ORE_CANDIDATES, exception);
            return null;
        }
    }

    private static boolean standardHeightCheck() {
        for (var method : LevelHeightAccessor.class.getDeclaredMethods()) {
            if (method.isDefault() && method.getReturnType() == boolean.class && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == int.class) {
                String key = method.getName() + "(I)Z";
                try {
                    return WorldGenRegion.class.getMethod(method.getName(), int.class).getDeclaringClass() == LevelHeightAccessor.class
                        && !SurfaceRuleHooks.hooked(LevelHeightAccessor.class, key);
                } catch (NoSuchMethodException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static native int collect0(double[] spheres, int count, int[] output, int x, int y, int z,
        int width, int height, int minY, int maxY);
}
