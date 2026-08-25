package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ImprovedNoise.class)
public abstract class MixinImprovedNoise {

    @Unique
    private static final byte[] adrenaline$gradients = {
        1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
        1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
        0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1,
        1, 1, 0, 0, -1, 1, -1, 1, 0, 0, -1, -1
    };

    @Shadow @Final private byte[] p;

    @Unique
    private boolean adrenaline$fastSampling;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$configureFastSampling(RandomSource random, CallbackInfo ci) {
        this.adrenaline$fastSampling = AdrenalineConfig.noiseChunkOptimizationsEnabled();
    }

    @WrapOperation(
        method = "noise(DDDDD)D",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/synth/ImprovedNoise;sampleAndLerp(IIIDDDD)D"
        )
    )
    @ControlsOptimization(Optimization.NOISE_CHUNK)
    private double adrenaline$sampleDirect(
        ImprovedNoise noise,
        int sectionX,
        int sectionY,
        int sectionZ,
        double localX,
        double localY,
        double localZ,
        double fadeY,
        Operation<Double> original
    ) {
        if (!this.adrenaline$fastSampling) {
            return original.call(noise, sectionX, sectionY, sectionZ, localX, localY, localZ, fadeY);
        }

        byte[] permutation = this.p;
        int x0 = permutation[sectionX & 255] & 255;
        int x1 = permutation[sectionX + 1 & 255] & 255;
        int y00 = permutation[x0 + sectionY & 255] & 255;
        int y01 = permutation[x0 + sectionY + 1 & 255] & 255;
        int y10 = permutation[x1 + sectionY & 255] & 255;
        int y11 = permutation[x1 + sectionY + 1 & 255] & 255;
        double x1Offset = localX - 1.0D;
        double y1Offset = localY - 1.0D;
        double z1Offset = localZ - 1.0D;
        double n000 = adrenaline$gradient(permutation[y00 + sectionZ & 255], localX, localY, localZ);
        double n100 = adrenaline$gradient(permutation[y10 + sectionZ & 255], x1Offset, localY, localZ);
        double n010 = adrenaline$gradient(permutation[y01 + sectionZ & 255], localX, y1Offset, localZ);
        double n110 = adrenaline$gradient(permutation[y11 + sectionZ & 255], x1Offset, y1Offset, localZ);
        double n001 = adrenaline$gradient(permutation[y00 + sectionZ + 1 & 255], localX, localY, z1Offset);
        double n101 = adrenaline$gradient(permutation[y10 + sectionZ + 1 & 255], x1Offset, localY, z1Offset);
        double n011 = adrenaline$gradient(permutation[y01 + sectionZ + 1 & 255], localX, y1Offset, z1Offset);
        double n111 = adrenaline$gradient(permutation[y11 + sectionZ + 1 & 255], x1Offset, y1Offset, z1Offset);
        return Mth.lerp3(
            Mth.smoothstep(localX),
            Mth.smoothstep(fadeY),
            Mth.smoothstep(localZ),
            n000,
            n100,
            n010,
            n110,
            n001,
            n101,
            n011,
            n111
        );
    }

    @Unique
    private static double adrenaline$gradient(int hash, double x, double y, double z) {
        int index = (hash & 15) * 3;
        return adrenaline$gradients[index] * x
            + adrenaline$gradients[index + 1] * y
            + adrenaline$gradients[index + 2] * z;
    }

}
