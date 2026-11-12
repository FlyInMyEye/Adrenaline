package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineCompiledInterpolatorAccess {

    double adrenaline$computeCellValue(DensityFunction.FunctionContext context);
}
