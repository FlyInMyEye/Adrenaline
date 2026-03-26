package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.util.DeferredNotificationBuffer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class GenerationTask implements Runnable {

    private final ChunkPos center;
    private final int writeRadius;
    private final Runnable work;

    public GenerationTask(ChunkPos center, int writeRadius, Runnable work) {
        this.center = center;
        this.writeRadius = writeRadius;
        this.work = work;
    }

    @Override
    public void run() {
        Collection<ChunkPos> footprint = collectFootprint();
        List<ReentrantLock> locks = ChunkLockManager.acquireAll(footprint);
        try {
            work.run();
            DeferredNotificationBuffer.flush();
        } finally {
            ChunkLockManager.releaseAll(locks);
            ChunkLockManager.cleanup(center);
        }
    }

    private Collection<ChunkPos> collectFootprint() {
        if (writeRadius <= 0) {
            return List.of(center);
        }
        List<ChunkPos> result = new ArrayList<>((writeRadius * 2 + 1) * (writeRadius * 2 + 1));
        for (int dx = -writeRadius; dx <= writeRadius; dx++) {
            for (int dz = -writeRadius; dz <= writeRadius; dz++) {
                result.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        return result;
    }
}
