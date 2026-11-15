package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PerlinSimplexNoise.class)
public interface AdrenalineMixinPerlinSimplexNoiseAccess {
    @Accessor("noiseLevels")
    SimplexNoise[] adrenaline$getNoiseLevels();

    @Accessor("highestFreqInputFactor")
    double adrenaline$getHighestFreqInputFactor();

    @Accessor("highestFreqValueFactor")
    double adrenaline$getHighestFreqValueFactor();
}
