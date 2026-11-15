package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;

public final class FlatCacheArrays {

    private static final double[][] EMPTY_VALUES = new double[0][];

    private FlatCacheArrays() {
    }

    public static double[][] allocateFlatCacheValues(int xzSize, int zSize, NoiseChunk noiseChunk, boolean eagerlyPopulate) {
        if (AdrenalineConfig.flatCacheOptimizationsEnabled() && !eagerlyPopulate && noiseChunk.getBlender() == Blender.empty()) {
            return EMPTY_VALUES;
        }
        return new double[xzSize][zSize];
    }

    public static boolean isEmptyBlender(Blender blender) {
        return AdrenalineConfig.flatCacheOptimizationsEnabled() && blender == Blender.empty();
    }
}
