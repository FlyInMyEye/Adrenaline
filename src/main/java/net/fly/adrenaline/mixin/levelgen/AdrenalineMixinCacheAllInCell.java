package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public class AdrenalineMixinCacheAllInCell {

    @Shadow @Final private DensityFunction noiseFiller;
    @Shadow @Final private double[] values;
}