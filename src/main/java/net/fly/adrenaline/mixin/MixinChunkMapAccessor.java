package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface MixinChunkMapAccessor {

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder adrenaline$getVisibleChunkIfPresent(long pos);
}
