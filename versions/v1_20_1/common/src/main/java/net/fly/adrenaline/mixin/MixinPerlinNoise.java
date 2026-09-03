package net.fly.adrenaline.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.fly.adrenaline.worldgen.AdrenalinePerlinNativeAccess;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PerlinNoise.class)
public abstract class MixinPerlinNoise implements AdrenalinePerlinNativeAccess {
    @Shadow @Final private ImprovedNoise[] noiseLevels;
    @Shadow @Final private DoubleList amplitudes;
    @Shadow @Final private double lowestFreqValueFactor;
    @Shadow @Final private double lowestFreqInputFactor;

    @Unique
    private PerlinNativeSampler.Data adrenaline$nativeData;

    @Override
    public PerlinNativeSampler.Data adrenaline$getNativeData() {
        PerlinNativeSampler.Data data = this.adrenaline$nativeData;
        if (data == null) {
            data = PerlinNativeSampler.create(this.noiseLevels, this.amplitudes, this.lowestFreqValueFactor, this.lowestFreqInputFactor);
            this.adrenaline$nativeData = data;
        }
        return data;
    }
}
