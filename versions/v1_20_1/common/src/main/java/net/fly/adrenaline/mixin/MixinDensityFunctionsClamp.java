package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineClampFunctionAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Clamp")
public abstract class MixinDensityFunctionsClamp implements AdrenalineClampFunctionAccess {

    @Shadow
    public abstract DensityFunction input();

    @Shadow
    public abstract double minValue();

    @Shadow
    public abstract double maxValue();

    @Override
    public DensityFunction adrenaline$clampInput() {
        return this.input();
    }

    @Override
    public double adrenaline$clampMin() {
        return this.minValue();
    }

    @Override
    public double adrenaline$clampMax() {
        return this.maxValue();
    }
}
