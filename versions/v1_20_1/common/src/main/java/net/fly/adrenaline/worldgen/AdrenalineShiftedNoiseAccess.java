package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineShiftedNoiseAccess {
    boolean adrenaline$isHeightIndependent();
    DensityFunction adrenaline$getShiftX();
    DensityFunction adrenaline$getShiftY();
    DensityFunction adrenaline$getShiftZ();
    double adrenaline$getXzScale();
    double adrenaline$getYScale();
    DensityFunction.NoiseHolder adrenaline$getShiftedNoise();
}
