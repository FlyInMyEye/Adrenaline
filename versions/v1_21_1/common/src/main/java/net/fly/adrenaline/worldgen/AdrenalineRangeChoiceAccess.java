package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineRangeChoiceAccess {

    DensityFunction adrenaline$rangeInput();

    double adrenaline$minInclusive();

    double adrenaline$maxExclusive();

    DensityFunction adrenaline$whenInRange();

    DensityFunction adrenaline$whenOutOfRange();
}
