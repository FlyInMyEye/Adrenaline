package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface AdrenalineMixinNoiseInterpolatorAccessor {

    @Accessor("slice0")
    double[][] adrenaline$getSlice0();

    @Accessor("slice1")
    double[][] adrenaline$getSlice1();
}
