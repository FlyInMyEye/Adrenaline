package net.fly.adrenaline.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {
    "net.minecraft.util.CubicSpline$Multipoint",
    "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator",
    "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache",
    "net.minecraft.world.level.levelgen.DensityFunctions$Spline",
    "net.minecraft.world.level.levelgen.DensityFunctions$Spline$Coordinate",
    "net.minecraft.world.level.levelgen.DensityFunctions$Noise",
    "net.minecraft.world.level.levelgen.SurfaceRules$Context",
    "net.minecraft.world.level.levelgen.RandomState$1"
}, priority = 1)
public class MixinAdditionalOptimizationTakeoverDetector {
}
