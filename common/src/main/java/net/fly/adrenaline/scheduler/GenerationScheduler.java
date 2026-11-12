package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.scheduler.GenerationJob.WaitReason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class GenerationScheduler implements GenerationJob.Execution {

    public static final int WORKER_IDLE = GenerationWorkers.IDLE;
    public static final int WORKER_ACTIVE = GenerationWorkers.ACTIVE;
    public static final int WORKER_WAITING = GenerationWorkers.WAITING;

    private final boolean debug;
    private final GenerationWorkers workers;
    private volatile int maxActive;
    private final AtomicInteger pendingDependencies = new AtomicInteger();
    private long epoch;
    private boolean cancelling;

    private int runningCount;
    private final GenerationFootprints<GenerationJob> footprints = new GenerationFootprints<>();
    private final HashSet<GenerationJob> activeJobs = new HashSet<>();

    private final GenerationTaskQueue<GenerationJob> capacityQueue = new GenerationTaskQueue<>();

    protected GenerationScheduler(int threads, boolean debug) {
        this.debug = debug;
        this.workers = new GenerationWorkers(threads, debug);
        this.maxActive = this.workers.parallelism();
    }

    protected abstract int configuredWorkerThreads();

    protected abstract GenerationTaskQueue.Priority stagePriority();

    protected abstract boolean debugLoggingEnabled();

    protected abstract void onQueued(GenerationJob job, WaitReason reason);

    protected abstract void onDispatched(GenerationJob job);

    protected abstract void onCancelled(int pendingCount, int activeJobCount);

    public int parallelism() {
        this.refreshPoolIfNeeded();
        return this.maxActive;
    }

    public int configuredParallelism() {
        this.refreshPoolIfNeeded();
        return this.workers.parallelism();
    }

    public synchronized boolean isDrained() {
        return this.activeJobs.isEmpty();
    }

    public synchronized boolean hasSpareWorkers() {
        return this.runningCount < this.maxActive;
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
        if (!this.debug) {
            return new int[0];
        }
        this.refreshPoolIfNeeded();
        boolean waitingWork = this.pendingDependencies.get() > 0 || this.runningCount > 0 || this.footprints.waitingCount() > 0 || !this.capacityQueue.isEmpty();
        return this.workers.snapshot(waitingWork);
    }

    public synchronized long[] activeChunkSnapshot() {
        long[] snapshot = new long[this.activeJobs.size()];
        int count = 0;
        for (GenerationJob job : this.activeJobs) {
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

    @Override
    public void markCurrentWorkerActive() {
        this.workers.markCurrentWorker(WORKER_ACTIVE);
    }

    @Override
    public void markCurrentWorkerIdle() {
        this.workers.markCurrentWorker(WORKER_IDLE);
    }

    public synchronized void awaitNotActive(long key) throws InterruptedException {
        while (this.footprints.isActive(key)) {
            wait();
        }
    }

    public synchronized void submit(GenerationJob job) {
        if (this.cancelling) {
            job.cancel();
            return;
        }
        this.refreshPoolIfNeeded();
        job.setSchedulerEpoch(this.epoch);
        if (this.footprints.conflicts(job.footprint())) {
            job.transitionWaiting(WaitReason.FOOTPRINT_CONFLICT);
            if (this.debug && this.debugLoggingEnabled()) {
                this.onQueued(job, WaitReason.FOOTPRINT_CONFLICT);
            }
            this.footprints.addWaiting(job);
        } else if (runningCount >= maxActive) {
            job.transitionWaiting(WaitReason.CAPACITY);
            if (this.debug && this.debugLoggingEnabled()) {
                this.onQueued(job, WaitReason.CAPACITY);
            }
            capacityQueue.addLast(job);
        } else {
            dispatch(job);
        }
    }

    @Override
    public synchronized boolean begin(GenerationJob job) {
        if (this.cancelling || job.schedulerEpoch() != this.epoch) {
            if (this.activeJobs.remove(job)) {
                notifyAll();
            }
            return false;
        }
        job.finishWaiting();
        job.markStarted();
        return true;
    }

    @Override
    public synchronized void onComplete(GenerationJob job) {
        this.activeJobs.remove(job);
        notifyAll();
        if (job.schedulerEpoch() != this.epoch) {
            return;
        }
        this.refreshPoolIfNeeded();
        if (job.releaseCapacity()) {
            this.runningCount--;
        }
        List<GenerationJob> ready = this.footprints.release(job.footprint());
        notifyAll();
        for (GenerationJob candidate : ready) {
            candidate.transitionWaiting(WaitReason.CAPACITY);
            capacityQueue.addLast(candidate);
        }

        this.drainCapacityQueue();
    }

    private void drainCapacityQueue() {
        while (runningCount < maxActive && !capacityQueue.isEmpty()) {
            GenerationJob queuedJob = this.pollCapacityJob();
            if (this.footprints.conflicts(queuedJob.footprint())) {
                queuedJob.transitionWaiting(WaitReason.FOOTPRINT_CONFLICT);
                this.footprints.addWaiting(queuedJob);
            } else {
                dispatch(queuedJob);
            }
        }
    }

    private GenerationJob pollCapacityJob() {
        return this.capacityQueue.poll(this.stagePriority());
    }

    public void cancel(Executor cancellationExecutor) {
        List<GenerationJob> jobsToCancel;
        int pendingCount;
        int activeJobCount;
        synchronized (this) {
            pendingCount = this.footprints.waitingCount() + this.capacityQueue.size();
            jobsToCancel = new ArrayList<>(this.footprints.waitingCount() + this.capacityQueue.size() + this.activeJobs.size());
            this.footprints.copyWaitingTo(jobsToCancel);
            for (GenerationJob job : this.capacityQueue) {
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

        for (GenerationJob job : jobsToCancel) {
            cancellationExecutor.execute(job::cancel);
        }
        this.onCancelled(pendingCount, activeJobCount);
    }

    public synchronized void resume() {
        this.cancelling = false;
    }

    private void dispatch(GenerationJob job) {
        if (this.debug && this.debugLoggingEnabled()) {
            this.onDispatched(job);
        }
        this.footprints.acquire(job.footprint());
        activeJobs.add(job);
        runningCount++;
        job.acquireCapacity();
        job.transitionWaiting(WaitReason.EXECUTOR_QUEUE);
        this.workers.executor().execute(job);
    }

    private void refreshPoolIfNeeded() {
        int threads = this.configuredWorkerThreads();
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
