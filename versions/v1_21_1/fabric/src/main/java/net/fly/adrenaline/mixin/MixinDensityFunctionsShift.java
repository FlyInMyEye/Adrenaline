package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineShiftNoiseAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Shift")
public abstract class MixinDensityFunctionsShift implements AdrenalineShiftNoiseAccess {
    @Accessor("offsetNoise") public abstract DensityFunction.NoiseHolder adrenaline$getOffsetNoise();
    @Override public int adrenaline$getShiftKind() { return 0; }
}
