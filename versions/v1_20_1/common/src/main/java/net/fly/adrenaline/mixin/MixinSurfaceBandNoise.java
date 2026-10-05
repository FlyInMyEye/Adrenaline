package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.worldgen.SurfaceNoiseBatch;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SurfaceSystem.class, priority = 499)
public class MixinSurfaceBandNoise {
    @Redirect(method = "getBand", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"), require = 0)
    @ControlsOptimization(Optimization.SURFACE_NOISE)
    private double adrenaline$reuseBandNoise(NormalNoise noise, double x, double y, double z) {
        return SurfaceNoiseBatch.sampleExact(noise, x, y, z);
    }
}
