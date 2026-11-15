package net.fly.adrenaline.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunkBlenderAllocation {
}
