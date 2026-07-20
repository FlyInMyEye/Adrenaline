package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import net.minecraft.world.level.ChunkPos;

public final class ChunkJobScheduler {

    private static final ChunkJobScheduler INSTANCE = new ChunkJobScheduler();

    private volatile ForkJoinPool pool = new ForkJoinPool(AdrenalineConfig.resolvedGenerationWorkerThreads());
    private volatile int maxActive = pool.getParallelism();
    private long epoch;
    private boolean cancelling;

    private int activeCount = 0;
    private final HashSet<Long> activeFootprint = new HashSet<>();
    private final HashSet<ChunkJob> activeJobs = new HashSet<>();

    private final LinkedHashSet<ChunkJob> conflictPending = new LinkedHashSet<>();
    private final HashMap<Long, Set<ChunkJob>> waitIndex = new HashMap<>();

    private final ArrayDeque<ChunkJob> capacityQueue = new ArrayDeque<>();

    private ChunkJobScheduler() {
    }

    public static ChunkJobScheduler get() {
        return INSTANCE;
    }

    public int parallelism() {
        this.refreshPoolIfNeeded();
        return this.maxActive;
    }

    public Executor executor() {
        this.refreshPoolIfNeeded();
        return this.pool;
    }

    public synchronized void awaitNotActive(ChunkPos pos) throws InterruptedException {
        long key = pos.toLong();
        while (activeFootprint.contains(key)) {
            wait();
        }
    }

    public synchronized void submit(ChunkJob job) {
        if (this.cancelling) {
            job.cancel();
            return;
        }
        this.refreshPoolIfNeeded();
        job.markSubmitted();
        job.setSchedulerEpoch(this.epoch);
        if (conflicts(job.footprint())) {
            if (AdrenalineConfig.debugLoggingEnabled()) {
                Adrenaline.LOGGER.info("Queued conflicting job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
            }
            conflictPending.add(job);
            indexJob(job);
        } else if (activeCount >= maxActive) {
            if (AdrenalineConfig.debugLoggingEnabled()) {
                Adrenaline.LOGGER.info("Queued capacity job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
            }
            capacityQueue.addLast(job);
        } else {
            dispatch(job);
        }
    }

    synchronized boolean begin(ChunkJob job) {
        if (this.cancelling || job.schedulerEpoch() != this.epoch) {
            return false;
        }
        job.markStarted();
        job.finishScheduling();
        return true;
    }

    synchronized void onComplete(ChunkJob job) {
        this.activeJobs.remove(job);
        notifyAll();
        if (job.schedulerEpoch() != this.epoch) {
            return;
        }
        this.refreshPoolIfNeeded();
        Set<Long> footprint = job.footprint();
        this.activeFootprint.removeAll(footprint);
        this.activeCount--;

        notifyAll();

        Set<ChunkJob> candidates = new HashSet<>();
        for (long key : footprint) {
            Set<ChunkJob> waiters = waitIndex.get(key);
            if (waiters != null) {
                candidates.addAll(waiters);
            }
        }

        for (ChunkJob candidate : candidates) {
            if (conflictPending.contains(candidate) && !conflicts(candidate.footprint())) {
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
            ChunkJob queuedJob = capacityQueue.pollFirst();
            if (conflicts(queuedJob.footprint())) {
                conflictPending.add(queuedJob);
                indexJob(queuedJob);
            } else {
                dispatch(queuedJob);
            }
        }
    }

    public void cancel(Executor cancellationExecutor) {
        List<ChunkJob> jobsToCancel;
        int pendingCount;
        int activeJobCount;
        synchronized (this) {
            pendingCount = this.conflictPending.size() + this.capacityQueue.size();
            jobsToCancel = new ArrayList<>(this.conflictPending.size() + this.capacityQueue.size() + this.activeJobs.size());
            jobsToCancel.addAll(this.conflictPending);
            jobsToCancel.addAll(this.capacityQueue);
            jobsToCancel.addAll(this.activeJobs);
            activeJobCount = this.activeJobs.size();
            this.epoch++;
            this.cancelling = true;
            this.activeCount = 0;
            this.activeFootprint.clear();
            this.conflictPending.clear();
            this.waitIndex.clear();
            this.capacityQueue.clear();
            notifyAll();
        }

        for (ChunkJob job : jobsToCancel) {
            cancellationExecutor.execute(job::cancel);
        }
        Adrenaline.LOGGER.info("Cancelled {} pending worldgen jobs; {} active jobs will drain", pendingCount, activeJobCount);
    }

    public synchronized void resume() {
        this.cancelling = false;
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
        if (AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("Dispatching job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
        }
        activeFootprint.addAll(job.footprint());
        activeJobs.add(job);
        activeCount++;
        pool.execute(job);
    }

    private static String summarizeFootprint(Set<Long> footprint) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : footprint) {
            ChunkPos pos = new ChunkPos(key);
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return "size=" + footprint.size() + " bounds=[" + minX + "," + minZ + " -> " + maxX + "," + maxZ + "]";
    }

    private void refreshPoolIfNeeded() {
        int threads = AdrenalineConfig.resolvedGenerationWorkerThreads();
        ForkJoinPool current = this.pool;
        if (current.getParallelism() == threads) {
            return;
        }

        synchronized (this) {
            current = this.pool;
            if (current.getParallelism() == threads) {
                return;
            }

            ForkJoinPool replacement = new ForkJoinPool(threads);
            this.pool = replacement;
            this.maxActive = replacement.getParallelism();
            current.shutdown();
        }
    }
}
