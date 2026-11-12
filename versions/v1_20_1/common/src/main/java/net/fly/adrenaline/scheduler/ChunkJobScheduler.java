package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.WorldgenStageStats.WaitingReason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.world.level.ChunkPos;

public final class ChunkJobScheduler {

    public static final int WORKER_IDLE = GenerationWorkers.IDLE;
    public static final int WORKER_ACTIVE = GenerationWorkers.ACTIVE;
    public static final int WORKER_WAITING = GenerationWorkers.WAITING;

    private static final ChunkJobScheduler INSTANCE = new ChunkJobScheduler();

    private final GenerationWorkers workers = new GenerationWorkers(AdrenalineConfig.resolvedGenerationWorkerThreads(), BuildConfig.DEBUG);
    private volatile int maxActive = this.workers.parallelism();
    private final AtomicInteger pendingDependencies = new AtomicInteger();
    private long epoch;
    private boolean cancelling;

    private int runningCount;
    private final GenerationFootprints<ChunkJob> footprints = new GenerationFootprints<>();
    private final HashSet<ChunkJob> activeJobs = new HashSet<>();

    private final GenerationTaskQueue<ChunkJob> capacityQueue = new GenerationTaskQueue<>();

    private ChunkJobScheduler() {
    }

    public static ChunkJobScheduler get() {
        return INSTANCE;
    }

    public int parallelism() {
        this.refreshPoolIfNeeded();
        return this.maxActive;
    }

    public int configuredParallelism() {
        this.refreshPoolIfNeeded();
        return this.workers.parallelism();
    }

    public synchronized boolean hasSpareWorkers() {
        return this.runningCount < this.maxActive;
    }

    public synchronized boolean isDrained() {
        return this.activeJobs.isEmpty();
    }

    public synchronized void setWorkerLimit(int workers) {
        this.refreshPoolIfNeeded();
        int previous = this.maxActive;
        this.maxActive = Math.max(1, Math.min(workers, this.workers.parallelism()));
        if (!this.cancelling && this.maxActive > previous) {
            this.drainCapacityQueue();
        }
    }

    public Executor executor() {
        this.refreshPoolIfNeeded();
        return this.workers.executor();
    }

    public void dependencyScheduled() {
        this.pendingDependencies.incrementAndGet();
    }

    public void dependencyReady() {
        this.pendingDependencies.updateAndGet(value -> Math.max(0, value - 1));
    }

    public synchronized int[] workerSnapshot() {
        if (!BuildConfig.DEBUG) {
            return new int[0];
        }
        this.refreshPoolIfNeeded();
        boolean waitingWork = this.pendingDependencies.get() > 0 || this.runningCount > 0 || this.footprints.waitingCount() > 0 || !this.capacityQueue.isEmpty();
        return this.workers.snapshot(waitingWork);
    }

    public synchronized long[] activeChunkSnapshot() {
        long[] snapshot = new long[this.activeJobs.size()];
        int count = 0;
        for (ChunkJob job : this.activeJobs) {
            if (!job.isExecuting()) {
                continue;
            }
            long position = job.trackedPosition();
            if (position != Long.MIN_VALUE) {
                snapshot[count++] = position;
            }
        }
        if (count == snapshot.length) {
            return snapshot;
        }
        long[] trimmed = new long[count];
        System.arraycopy(snapshot, 0, trimmed, 0, count);
        return trimmed;
    }

    public void markCurrentWorkerActive() {
        this.workers.markCurrentWorker(WORKER_ACTIVE);
    }

    void markCurrentWorkerIdle() {
        this.workers.markCurrentWorker(WORKER_IDLE);
    }

    public synchronized void awaitNotActive(ChunkPos pos) throws InterruptedException {
        long key = pos.toLong();
        while (this.footprints.isActive(key)) {
            wait();
        }
    }

    public synchronized void submit(ChunkJob job) {
        if (this.cancelling) {
            job.cancel();
            return;
        }
        this.refreshPoolIfNeeded();
        job.setSchedulerEpoch(this.epoch);
        if (this.footprints.conflicts(job.footprint())) {
            job.transitionWaiting(WaitingReason.FOOTPRINT_CONFLICT);
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                GlobalCommon.LOGGER.info("Queued conflicting job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
            }
            this.footprints.addWaiting(job);
        } else if (runningCount >= maxActive) {
            job.transitionWaiting(WaitingReason.CAPACITY);
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                GlobalCommon.LOGGER.info("Queued capacity job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
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
        job.finishWaiting();
        job.markStarted();
        return true;
    }

    synchronized void onComplete(ChunkJob job) {
        this.activeJobs.remove(job);
        notifyAll();
        if (job.schedulerEpoch() != this.epoch) {
            return;
        }
        this.refreshPoolIfNeeded();
        if (job.releaseCapacity()) {
            this.runningCount--;
        }
        List<ChunkJob> ready = this.footprints.release(job.footprint());
        notifyAll();
        for (ChunkJob candidate : ready) {
            candidate.transitionWaiting(WaitingReason.CAPACITY);
            capacityQueue.addLast(candidate);
        }

        this.drainCapacityQueue();
    }

    private void drainCapacityQueue() {
        while (runningCount < maxActive && !capacityQueue.isEmpty()) {
            ChunkJob queuedJob = this.pollCapacityJob();
            if (this.footprints.conflicts(queuedJob.footprint())) {
                queuedJob.transitionWaiting(WaitingReason.FOOTPRINT_CONFLICT);
                this.footprints.addWaiting(queuedJob);
            } else {
                dispatch(queuedJob);
            }
        }
    }

    private ChunkJob pollCapacityJob() {
        GenerationTaskQueue.Priority priority = switch (AdrenalineConfig.stagePriority()) {
            case FIFO -> GenerationTaskQueue.Priority.FIFO;
            case HIGHEST -> GenerationTaskQueue.Priority.HIGHEST;
            case NEAREST -> GenerationTaskQueue.Priority.NEAREST;
        };
        return this.capacityQueue.poll(priority);
    }

    public void cancel(Executor cancellationExecutor) {
        List<ChunkJob> jobsToCancel;
        int pendingCount;
        int activeJobCount;
        synchronized (this) {
            pendingCount = this.footprints.waitingCount() + this.capacityQueue.size();
            jobsToCancel = new ArrayList<>(this.footprints.waitingCount() + this.capacityQueue.size() + this.activeJobs.size());
            this.footprints.copyWaitingTo(jobsToCancel);
            for (ChunkJob job : this.capacityQueue) {
                jobsToCancel.add(job);
            }
            jobsToCancel.addAll(this.activeJobs);
            activeJobCount = this.activeJobs.size();
            this.epoch++;
            this.cancelling = true;
            this.runningCount = 0;
            this.footprints.clear();
            this.capacityQueue.clear();
            this.pendingDependencies.set(0);
            notifyAll();
        }

        for (ChunkJob job : jobsToCancel) {
            cancellationExecutor.execute(job::cancel);
        }
        GlobalCommon.LOGGER.info("Cancelled {} pending worldgen jobs; {} active jobs will drain", pendingCount, activeJobCount);
    }

    public synchronized void resume() {
        this.cancelling = false;
    }

    private void dispatch(ChunkJob job) {
        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            GlobalCommon.LOGGER.info("Dispatching job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
        }
        this.footprints.acquire(job.footprint());
        activeJobs.add(job);
        runningCount++;
        job.acquireCapacity();
        job.transitionWaiting(WaitingReason.EXECUTOR_QUEUE);
        this.workers.executor().execute(job);
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
        if (this.workers.parallelism() == threads) {
            return;
        }
        synchronized (this) {
            if (this.workers.parallelism() == threads) {
                return;
            }
            this.workers.resize(threads);
            this.maxActive = this.workers.parallelism();
        }
    }
}
