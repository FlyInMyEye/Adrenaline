package net.fly.adrenaline.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface MixinNoiseChunkWrapNewInvoker {

    @Invoker("wrapNew")
    DensityFunction adrenaline$invokeWrapNew(DensityFunction densityFunction);
}
