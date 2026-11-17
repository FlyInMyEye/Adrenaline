package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkTaskPriorityQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkTaskPriorityQueue.class)
public interface MixinChunkTaskPriorityQueueAccessor {

    @Mutable
    @Accessor("PRIORITY_LEVEL_COUNT")
    static void adrenaline$setPriorityLevelCount(int count) {
        throw new AssertionError();
    }
}
