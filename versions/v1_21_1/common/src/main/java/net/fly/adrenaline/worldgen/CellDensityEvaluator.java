package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface CellDensityEvaluator {

    void fill(double[] values, DensityFunction.ContextProvider contextProvider);
}
