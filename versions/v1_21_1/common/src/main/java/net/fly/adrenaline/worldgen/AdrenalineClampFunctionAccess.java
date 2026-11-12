package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineClampFunctionAccess {

    DensityFunction adrenaline$clampInput();

    double adrenaline$clampMin();

    double adrenaline$clampMax();
}
