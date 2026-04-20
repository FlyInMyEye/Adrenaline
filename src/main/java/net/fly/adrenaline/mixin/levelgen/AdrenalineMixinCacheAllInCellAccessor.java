package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public interface AdrenalineMixinCacheAllInCellAccessor {

    @Accessor("noiseFiller")
    DensityFunction getNoiseFiller();

    @Accessor("values")
    double[] getValues();
}