package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineBinaryFunctionAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Ap2")
public abstract class MixinDensityFunctionsAp2 implements AdrenalineBinaryFunctionAccess {

    @Shadow
    public abstract DensityFunction argument1();

    @Shadow
    public abstract DensityFunction argument2();

    @Override
    public DensityFunction adrenaline$firstArgument() {
        return this.argument1();
    }

    @Override
    public DensityFunction adrenaline$secondArgument() {
        return this.argument2();
    }
}
