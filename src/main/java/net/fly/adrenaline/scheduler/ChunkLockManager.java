package net.fly.adrenaline.scheduler;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ChunkLockManager {

    private static final ConcurrentHashMap<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private ChunkLockManager() {
    }

    public static List<ReentrantLock> acquireAll(Collection<ChunkPos> positions) {
        List<Long> sorted = positions.stream()
                .map(ChunkPos::toLong)
                .sorted()
                .toList();

        List<ReentrantLock> acquired = new ArrayList<>(sorted.size());
        for (long key : sorted) {
            ReentrantLock lock = LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            acquired.add(lock);
        }
        return acquired;
    }

    public static void releaseAll(List<ReentrantLock> locks) {
        for (int i = locks.size() - 1; i >= 0; i--) {
            locks.get(i).unlock();
        }
    }

    public static void cleanup(ChunkPos pos) {
        ReentrantLock lock = LOCKS.remove(pos.toLong());
        if (lock != null && !lock.isLocked()) {
            lock.unlock();
        }
    }
}
