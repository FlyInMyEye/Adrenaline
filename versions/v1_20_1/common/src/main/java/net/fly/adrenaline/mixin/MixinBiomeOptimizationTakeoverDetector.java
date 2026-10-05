package net.fly.adrenaline.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BiomeManager.class, priority = 1)
public class MixinBiomeOptimizationTakeoverDetector {
}
