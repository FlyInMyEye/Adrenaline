package net.fly.adrenaline.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class DensityEvaluationBridge {

    private DensityEvaluationBridge() {
    }

    public static DensityFunction.FunctionContext forIndex(DensityFunction.ContextProvider contextProvider, int index) {
        return contextProvider.forIndex(index);
    }

    public static double compute(DensityFunction function, DensityFunction.FunctionContext context) {
        return function.compute(context);
    }

    public static double clamp(double value, double min, double max) {
        return Mth.clamp(value, min, max);
    }
}
