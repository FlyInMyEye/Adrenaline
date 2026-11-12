package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public interface AdrenalineMixinCacheAllInCellAccessor {

    @Accessor("noiseFiller")
    DensityFunction adrenaline$getNoiseFiller();

    @Accessor("values")
    double[] adrenaline$getValues();
}
