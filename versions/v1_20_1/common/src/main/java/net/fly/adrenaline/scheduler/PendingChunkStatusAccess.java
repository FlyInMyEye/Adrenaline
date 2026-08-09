package net.fly.adrenaline.scheduler;

import net.minecraft.world.level.chunk.ChunkStatus;

public interface PendingChunkStatusAccess {

    void adrenaline$enqueuePendingStatus(ChunkStatus status);

    ChunkStatus adrenaline$pollPendingStatus();
}
