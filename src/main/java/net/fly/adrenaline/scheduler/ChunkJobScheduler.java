package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.WorldgenStageStats.WaitingReason;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import net.minecraft.world.level.ChunkPos;

public final class ChunkJobScheduler {

    public static final int WORKER_IDLE = 0;
    public static final int WORKER_ACTIVE = 1;
    public static final int WORKER_WAITING = 2;

    private static final ChunkJobScheduler INSTANCE = new ChunkJobScheduler();

    private volatile AtomicIntegerArray workerStates;
    private volatile ForkJoinPool pool = this.createPool(AdrenalineConfig.resolvedGenerationWorkerThreads());
    private volatile int maxActive = pool.getParallelism();
    private final AtomicInteger pendingDependencies = new AtomicInteger();
    private long epoch;
    private boolean cancelling;

    private int runningCount;
    private int yieldedCount;
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

    public void dependencyScheduled() {
        this.pendingDependencies.incrementAndGet();
    }

    public void dependencyReady() {
        this.pendingDependencies.updateAndGet(value -> Math.max(0, value - 1));
    }

    public synchronized int[] workerSnapshot() {
        this.refreshPoolIfNeeded();
        int processors = Runtime.getRuntime().availableProcessors();
        int[] snapshot = new int[processors];
        AtomicIntegerArray states = this.workerStates;
        boolean waitingWork = this.pendingDependencies.get() > 0 || this.yieldedCount > 0 || !this.conflictPending.isEmpty() || !this.capacityQueue.isEmpty();
        int configuredWorkers = Math.min(this.maxActive, processors);
        for (int i = 0; i < configuredWorkers; i++) {
            int state = i < states.length() ? states.get(i) : WORKER_IDLE;
            snapshot[i] = state == WORKER_IDLE && waitingWork ? WORKER_WAITING : state;
        }
        for (int i = configuredWorkers; i < processors; i++) {
            snapshot[i] = -1;
        }
        return snapshot;
    }

    public void markCurrentWorkerActive() {
        this.markCurrentWorker(WORKER_ACTIVE);
    }

    void markCurrentWorkerIdle() {
        this.markCurrentWorker(WORKER_IDLE);
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
        job.setSchedulerEpoch(this.epoch);
        if (conflicts(job.footprint())) {
            job.transitionWaiting(WaitingReason.FOOTPRINT_CONFLICT);
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                Adrenaline.LOGGER.info("Queued conflicting job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
            }
            conflictPending.add(job);
            indexJob(job);
        } else if (runningCount >= maxActive) {
            job.transitionWaiting(WaitingReason.CAPACITY);
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
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
        job.finishWaiting();
        job.markStarted();
        return true;
    }

    synchronized void releaseWorker(ChunkJob job) {
        if (job.schedulerEpoch() != this.epoch || !job.releaseCapacity()) {
            return;
        }
        this.runningCount--;
        if (job.markYielded()) {
            this.yieldedCount++;
        }
        this.drainCapacityQueue();
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
        if (job.clearYielded()) {
            this.yieldedCount--;
        }
        Set<Long> footprint = job.footprint();
        this.activeFootprint.removeAll(footprint);

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
                if (AdrenalineConfig.prioritizeHigherStagesEnabled()) {
                    candidate.transitionWaiting(WaitingReason.CAPACITY);
                    capacityQueue.addLast(candidate);
                } else if (runningCount < maxActive) {
                    dispatch(candidate);
                } else {
                    candidate.transitionWaiting(WaitingReason.CAPACITY);
                    capacityQueue.addLast(candidate);
                }
            }
        }

        this.drainCapacityQueue();
    }

    private void drainCapacityQueue() {
        while (runningCount < maxActive && !capacityQueue.isEmpty()) {
            ChunkJob queuedJob = this.pollCapacityJob();
            if (conflicts(queuedJob.footprint())) {
                queuedJob.transitionWaiting(WaitingReason.FOOTPRINT_CONFLICT);
                conflictPending.add(queuedJob);
                indexJob(queuedJob);
            } else {
                dispatch(queuedJob);
            }
        }
    }

    private ChunkJob pollCapacityJob() {
        if (!AdrenalineConfig.prioritizeHigherStagesEnabled()) {
            return this.capacityQueue.pollFirst();
        }
        Iterator<ChunkJob> iterator = this.capacityQueue.iterator();
        ChunkJob selected = iterator.next();
        int highestStage = selected.stageIndex();
        int closestLevel = selected.queueLevel();
        while (iterator.hasNext()) {
            ChunkJob candidate = iterator.next();
            int stage = candidate.stageIndex();
            int queueLevel = candidate.queueLevel();
            if (stage > highestStage || stage == highestStage && queueLevel < closestLevel) {
                selected = candidate;
                highestStage = stage;
                closestLevel = queueLevel;
            }
        }
        this.capacityQueue.remove(selected);
        return selected;
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
            this.runningCount = 0;
            this.yieldedCount = 0;
            this.activeFootprint.clear();
            this.conflictPending.clear();
            this.waitIndex.clear();
            this.capacityQueue.clear();
            this.pendingDependencies.set(0);
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
        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("Dispatching job {} with footprint {}", job.debugLabel(), summarizeFootprint(job.footprint()));
        }
        activeFootprint.addAll(job.footprint());
        activeJobs.add(job);
        runningCount++;
        job.acquireCapacity();
        job.transitionWaiting(WaitingReason.EXECUTOR_QUEUE);
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

            ForkJoinPool replacement = this.createPool(threads);
            this.pool = replacement;
            this.maxActive = replacement.getParallelism();
            current.shutdown();
        }
    }

    private ForkJoinPool createPool(int threads) {
        AtomicIntegerArray states = new AtomicIntegerArray(threads);
        AtomicInteger nextWorker = new AtomicInteger();
        this.workerStates = states;
        return new ForkJoinPool(threads, pool -> {
            int index = nextWorker.getAndIncrement();
            return new GenerationWorkerThread(pool, states, index);
        }, null, false);
    }

    private void markCurrentWorker(int state) {
        Thread thread = Thread.currentThread();
        if (thread instanceof GenerationWorkerThread worker) {
            worker.setState(state);
        }
    }

    private static final class GenerationWorkerThread extends ForkJoinWorkerThread {

        private final AtomicIntegerArray states;
        private final int index;

        private GenerationWorkerThread(ForkJoinPool pool, AtomicIntegerArray states, int index) {
            super(pool);
            this.states = states;
            this.index = index;
        }

        private void setState(int state) {
            if (this.index < this.states.length()) {
                this.states.set(this.index, state);
            }
        }
    }

}
