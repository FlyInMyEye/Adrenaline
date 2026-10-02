package net.fly.adrenaline.mixin;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkStorage.class)
public interface MixinChunkStorageAccessor {
    @Accessor("worker")
    IOWorker adrenaline$getWorker();
}
