package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineWeirdScaledNoiseAccess {
    DensityFunction adrenaline$getRarityInput();
    DensityFunction.NoiseHolder adrenaline$getRarityNoise();
}
