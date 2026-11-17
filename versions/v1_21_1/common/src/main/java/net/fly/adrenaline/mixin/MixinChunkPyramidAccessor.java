package net.fly.adrenaline.mixin;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkPyramid.class)
public interface MixinChunkPyramidAccessor {

    @Mutable
    @Accessor("GENERATION_PYRAMID")
    static void adrenaline$setGenerationPyramid(ChunkPyramid pyramid) {
        throw new AssertionError();
    }
}
