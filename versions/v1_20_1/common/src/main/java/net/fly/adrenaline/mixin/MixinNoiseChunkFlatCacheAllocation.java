package net.fly.adrenaline.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public abstract class MixinNoiseChunkFlatCacheAllocation {
}
