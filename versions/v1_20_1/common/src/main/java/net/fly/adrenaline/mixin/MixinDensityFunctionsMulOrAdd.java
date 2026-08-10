package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineMulOrAddAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$MulOrAdd")
public abstract class MixinDensityFunctionsMulOrAdd implements AdrenalineMulOrAddAccess {

    @Shadow
    public abstract DensityFunction input();

    @Shadow
    public abstract double argument();

    @Override
    public DensityFunction adrenaline$transformInput() {
        return this.input();
    }

    @Override
    public double adrenaline$transformArgument() {
        return this.argument();
    }
}
