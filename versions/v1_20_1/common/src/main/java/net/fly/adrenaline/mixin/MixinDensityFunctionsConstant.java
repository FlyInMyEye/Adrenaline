package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineConstantFunctionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Constant")
public abstract class MixinDensityFunctionsConstant implements AdrenalineConstantFunctionAccess {

    @Shadow
    public abstract double value();

    @Override
    public double adrenaline$getConstantValue() {
        return this.value();
    }
}
