package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineNoiseFunctionAccess;
import net.fly.adrenaline.worldgen.PerlinBatching;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public abstract class MixinDensityFunctionsNoise implements AdrenalineNoiseFunctionAccess {

    @Shadow @Final private DensityFunction.NoiseHolder noise;
    @Shadow @Final private double xzScale;
    @Shadow @Final private double yScale;

    @Override
    public NormalNoise adrenaline$getNoise() {
        return this.noise.noise();
    }

    @Override
    public double adrenaline$getXzScale() {
        return this.xzScale;
    }

    @Override
    public double adrenaline$getYScale() {
        return this.yScale;
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void adrenaline$computeNativePerlinSection(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        if (!AdrenalineConfig.nativePerlinBatchingEnabled()) {
            return;
        }
        int blockX = context.blockX();
        int blockY = context.blockY();
        int blockZ = context.blockZ();
        NormalNoise normalNoise = this.noise.noise();
        double value = PerlinBatching.tryGetSection(this, normalNoise, this.xzScale, this.yScale, blockX, blockY, blockZ);
        if (!Double.isNaN(value)) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)
    private void adrenaline$fillNativePerlinSection(double[] values, DensityFunction.ContextProvider contextProvider, CallbackInfo ci) {
        if (AdrenalineConfig.nativePerlinBatchingEnabled()
            && PerlinBatching.tryFillSection(this, this.noise.noise(), this.xzScale, this.yScale, values, contextProvider)) {
            ci.cancel();
        }
    }
}
