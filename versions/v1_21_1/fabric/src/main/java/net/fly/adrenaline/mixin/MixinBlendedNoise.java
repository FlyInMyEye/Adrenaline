package net.fly.adrenaline.mixin;

import net.fly.adrenaline.natives.BlendedNativeSampler;
import net.fly.adrenaline.worldgen.AdrenalineBlendedNoiseAccess;
import net.fly.adrenaline.worldgen.AdrenalinePerlinNativeAccess;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlendedNoise.class)
public abstract class MixinBlendedNoise implements AdrenalineBlendedNoiseAccess {
    @Shadow @Final private PerlinNoise minLimitNoise;
    @Shadow @Final private PerlinNoise maxLimitNoise;
    @Shadow @Final private PerlinNoise mainNoise;
    @Shadow @Final private double xzMultiplier;
    @Shadow @Final private double yMultiplier;
    @Shadow @Final private double xzFactor;
    @Shadow @Final private double yFactor;
    @Shadow @Final private double smearScaleMultiplier;

    @Unique private volatile BlendedNativeSampler.Data adrenaline$nativeData;

    @Override
    public BlendedNativeSampler.Data adrenaline$getNativeData() {
        BlendedNativeSampler.Data data = this.adrenaline$nativeData;
        return data == null ? this.adrenaline$initializeNativeData() : data;
    }

    @Unique
    private synchronized BlendedNativeSampler.Data adrenaline$initializeNativeData() {
        if (this.adrenaline$nativeData == null) {
            this.adrenaline$nativeData = new BlendedNativeSampler.Data(
                ((AdrenalinePerlinNativeAccess) this.minLimitNoise).adrenaline$getNativeData(),
                ((AdrenalinePerlinNativeAccess) this.maxLimitNoise).adrenaline$getNativeData(),
                ((AdrenalinePerlinNativeAccess) this.mainNoise).adrenaline$getNativeData(),
                this.xzMultiplier, this.yMultiplier, this.xzFactor, this.yFactor, this.smearScaleMultiplier);
        }
        return this.adrenaline$nativeData;
    }
}
