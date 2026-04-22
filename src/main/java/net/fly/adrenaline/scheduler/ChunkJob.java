package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.util.DeferredNotificationBuffer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

public final class ChunkJob implements Runnable {

    private final Set<Long> footprint;
    private final Runnable work;
    private final ClassLoader contextClassLoader;

    public ChunkJob(ChunkPos center, int writeRadius, Runnable work, ClassLoader contextClassLoader) {
        this.footprint = buildFootprint(center, writeRadius);
        this.work = work;
        this.contextClassLoader = contextClassLoader;
    }

    public Set<Long> footprint() {
        return footprint;
    }

    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        try {
            if (this.contextClassLoader != null && this.contextClassLoader != previousClassLoader) {
                currentThread.setContextClassLoader(this.contextClassLoader);
            }
            work.run();
            DeferredNotificationBuffer.flush();
        } finally {
            if (currentThread.getContextClassLoader() != previousClassLoader) {
                currentThread.setContextClassLoader(previousClassLoader);
            }
            ChunkJobScheduler.get().onComplete(footprint);
        }
    }

    private static Set<Long> buildFootprint(ChunkPos center, int radius) {
        if (radius <= 0) {
            Set<Long> s = new HashSet<>(2);
            s.add(center.toLong());
            return s;
        }
        int side = radius * 2 + 1;
        Set<Long> s = new HashSet<>(side * side * 2);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                s.add(new ChunkPos(center.x + dx, center.z + dz).toLong());
            }
        }
        return s;
    }
}
