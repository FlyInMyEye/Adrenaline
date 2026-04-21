package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface AdrenalineMixinNoiseInterpolatorInvoker {

    @Invoker("swapSlices")
    void adrenaline$swapSlices();
}
