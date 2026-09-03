package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.synth.NormalNoise;

public interface AdrenalineNoiseFunctionAccess {

    NormalNoise adrenaline$getNoise();

    double adrenaline$getXzScale();

    double adrenaline$getYScale();
}
