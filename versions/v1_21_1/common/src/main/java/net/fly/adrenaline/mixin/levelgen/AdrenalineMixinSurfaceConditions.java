package net.fly.adrenaline.mixin.levelgen;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {
    "net.minecraft.world.level.levelgen.SurfaceRules$SequenceRule",
    "net.minecraft.world.level.levelgen.SurfaceRules$BiomeConditionSource$1BiomeCondition",
    "net.minecraft.world.level.levelgen.SurfaceRules$StoneDepthCheck$1StoneDepthCondition",
    "net.minecraft.world.level.levelgen.SurfaceRules$VerticalGradientConditionSource$1VerticalGradientCondition",
    "net.minecraft.world.level.levelgen.SurfaceRules$WaterConditionSource$1WaterCondition",
    "net.minecraft.world.level.levelgen.SurfaceRules$YConditionSource$1YCondition"
})
public abstract class AdrenalineMixinSurfaceConditions {
}
