package net.fly.adrenaline.mixin;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {NormalNoise.class, SurfaceSystem.class}, priority = 1)
public class MixinSurfaceNoiseOptimizationTakeoverDetector {
}
