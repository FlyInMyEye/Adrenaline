package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineShiftNoiseAccess {
    DensityFunction.NoiseHolder adrenaline$getOffsetNoise();
    int adrenaline$getShiftKind();
}
