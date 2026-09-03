package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineNormalNoiseNativeAccess;
import net.fly.adrenaline.worldgen.AdrenalinePerlinNativeAccess;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.fly.adrenaline.worldgen.PerlinBatching;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NormalNoise.class)
public abstract class MixinNormalNoise implements AdrenalineNormalNoiseNativeAccess {
    @Shadow @Final private PerlinNoise first;
    @Shadow @Final private PerlinNoise second;
    @Shadow @Final private double valueFactor;

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void adrenaline$useNativeBatch(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        if (PerlinBatching.isVanillaSampleSuppressed()) {
            return;
        }
        double value = PerlinBatching.tryGet((NormalNoise) (Object) this, x, y, z);
        if (!Double.isNaN(value)) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "getValue", at = @At("RETURN"))
    private void adrenaline$recordNativeBatch(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        if (PerlinBatching.isVanillaSampleSuppressed()) {
            return;
        }
        PerlinBatching.record((NormalNoise) (Object) this, x, y, z, cir.getReturnValue());
    }

    @Override
    public PerlinNativeSampler.Data adrenaline$getFirstNativeData() {
        return ((AdrenalinePerlinNativeAccess) this.first).adrenaline$getNativeData();
    }

    @Override
    public PerlinNativeSampler.Data adrenaline$getSecondNativeData() {
        return ((AdrenalinePerlinNativeAccess) this.second).adrenaline$getNativeData();
    }

    @Override
    public double adrenaline$getNativeValueFactor() {
        return this.valueFactor;
    }
}
