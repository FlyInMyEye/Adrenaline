package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.config.AdrenalineConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import net.minecraft.world.level.ChunkPos;

public final class ChunkJobScheduler {

    private static final ChunkJobScheduler INSTANCE = new ChunkJobScheduler();

    private final ForkJoinPool pool = new ForkJoinPool(AdrenalineConfig.resolvedWorkerThreads());
    private final int maxActive = pool.getParallelism();

    private int activeCount = 0;
    private final HashSet<Long> activeFootprint = new HashSet<>();

    private final LinkedHashSet<ChunkJob> conflictPending = new LinkedHashSet<>();
    private final HashMap<Long, Set<ChunkJob>> waitIndex = new HashMap<>();

    private final ArrayDeque<ChunkJob> capacityQueue = new ArrayDeque<>();

    private ChunkJobScheduler() {
    }

    public static ChunkJobScheduler get() {
        return INSTANCE;
    }

    public int parallelism() {
        return this.maxActive;
    }

    public Executor executor() {
        return this.pool;
    }

    public synchronized void awaitNotActive(ChunkPos pos) throws InterruptedException {
        long key = pos.toLong();
        while (activeFootprint.contains(key)) {
            wait();
        }
    }

    public synchronized void submit(ChunkJob job) {
        if (conflicts(job.footprint())) {
            conflictPending.add(job);
            indexJob(job);
        } else if (activeCount >= maxActive) {
            capacityQueue.addLast(job);
        } else {
            dispatch(job);
        }
    }

    synchronized void onComplete(Set<Long> footprint) {
        activeFootprint.removeAll(footprint);
        activeCount--;

        notifyAll();

        Set<ChunkJob> candidates = new HashSet<>();
        for (long key : footprint) {
            Set<ChunkJob> waiters = waitIndex.get(key);
            if (waiters != null) {
                candidates.addAll(waiters);
            }
        }

        for (ChunkJob candidate : candidates) {
            if (!conflictPending.contains(candidate)) {
                continue;
            }
            if (!conflicts(candidate.footprint())) {
                conflictPending.remove(candidate);
                removeFromIndex(candidate);
                if (activeCount < maxActive) {
                    dispatch(candidate);
                } else {
                    capacityQueue.addLast(candidate);
                }
            }
        }

        while (activeCount < maxActive && !capacityQueue.isEmpty()) {
            ChunkJob job = capacityQueue.pollFirst();
            if (conflicts(job.footprint())) {
                conflictPending.add(job);
                indexJob(job);
            } else {
                dispatch(job);
            }
        }
    }

    private void indexJob(ChunkJob job) {
        for (long key : job.footprint()) {
            waitIndex.computeIfAbsent(key, k -> new HashSet<>()).add(job);
        }
    }

    private void removeFromIndex(ChunkJob job) {
        for (long key : job.footprint()) {
            Set<ChunkJob> waiters = waitIndex.get(key);
            if (waiters != null) {
                waiters.remove(job);
                if (waiters.isEmpty()) {
                    waitIndex.remove(key);
                }
            }
        }
    }

    private boolean conflicts(Set<Long> footprint) {
        for (long key : footprint) {
            if (activeFootprint.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private void dispatch(ChunkJob job) {
        activeFootprint.addAll(job.footprint());
        activeCount++;
        pool.execute(job);
    }
}
