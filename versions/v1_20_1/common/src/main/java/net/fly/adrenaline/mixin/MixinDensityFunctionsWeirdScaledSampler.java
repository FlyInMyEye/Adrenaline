package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineWeirdScaledNoiseAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler")
public abstract class MixinDensityFunctionsWeirdScaledSampler implements AdrenalineWeirdScaledNoiseAccess {
    @Shadow @Final private DensityFunction input;
    @Shadow @Final private DensityFunction.NoiseHolder noise;

    @Override public DensityFunction adrenaline$getRarityInput() { return this.input; }
    @Override public DensityFunction.NoiseHolder adrenaline$getRarityNoise() { return this.noise; }
}
