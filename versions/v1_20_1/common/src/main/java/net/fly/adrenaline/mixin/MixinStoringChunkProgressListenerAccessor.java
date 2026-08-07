package net.fly.adrenaline.mixin;

import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StoringChunkProgressListener.class)
public interface MixinStoringChunkProgressListenerAccessor {

    @Accessor("spawnPos")
    ChunkPos adrenaline$getSpawnPos();

    @Accessor("radius")
    int adrenaline$getRadius();
}
