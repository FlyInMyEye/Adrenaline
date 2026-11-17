package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkLevel.class)
public interface MixinChunkLevelAccessor {

    @Mutable
    @Accessor("FULL_CHUNK_STEP")
    static void adrenaline$setFullChunkStep(ChunkStep step) {
        throw new AssertionError();
    }

    @Mutable
    @Accessor("RADIUS_AROUND_FULL_CHUNK")
    static void adrenaline$setRadiusAroundFullChunk(int radius) {
        throw new AssertionError();
    }

    @Mutable
    @Accessor("MAX_LEVEL")
    static void adrenaline$setMaxLevel(int level) {
        throw new AssertionError();
    }
}
