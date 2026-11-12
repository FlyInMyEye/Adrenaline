package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineRangeChoiceAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$RangeChoice")
public abstract class MixinDensityFunctionsRangeChoice implements AdrenalineRangeChoiceAccess {

    @Shadow
    public abstract DensityFunction input();

    @Shadow
    public abstract double minInclusive();

    @Shadow
    public abstract double maxExclusive();

    @Shadow
    public abstract DensityFunction whenInRange();

    @Shadow
    public abstract DensityFunction whenOutOfRange();

    @Override
    public DensityFunction adrenaline$rangeInput() {
        return this.input();
    }

    @Override
    public double adrenaline$minInclusive() {
        return this.minInclusive();
    }

    @Override
    public double adrenaline$maxExclusive() {
        return this.maxExclusive();
    }

    @Override
    public DensityFunction adrenaline$whenInRange() {
        return this.whenInRange();
    }

    @Override
    public DensityFunction adrenaline$whenOutOfRange() {
        return this.whenOutOfRange();
    }
}
