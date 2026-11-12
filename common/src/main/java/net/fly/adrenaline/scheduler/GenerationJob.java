package net.fly.adrenaline.scheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.fly.adrenaline.util.DeferredNotificationBuffer;

public abstract class GenerationJob implements Runnable, GenerationTaskQueue.Entry, GenerationFootprints.Entry {

    private static final ThreadLocal<GenerationJob> CURRENT_JOB = new ThreadLocal<>();

    private final Set<Long> footprint;
    private final Supplier<CompletableFuture<?>> work;
    private final Runnable cancelWork;
    private final ClassLoader contextClassLoader;
    private final String debugLabel;
    private final AtomicBoolean cancellationHandled = new AtomicBoolean();
    private final AtomicInteger executingTasks = new AtomicInteger();
    private long schedulerEpoch = Long.MIN_VALUE;
    private volatile boolean started;
    private boolean capacityHeld;

    protected GenerationJob(Set<Long> footprint, Supplier<CompletableFuture<?>> work, Runnable cancelWork, ClassLoader contextClassLoader, String debugLabel) {
        this.footprint = new HashSet<>(footprint);
        this.work = work;
        this.cancelWork = cancelWork;
        this.contextClassLoader = contextClassLoader;
        this.debugLabel = debugLabel;
    }

    @Override
    public final Set<Long> footprint() {
        return this.footprint;
    }

    public final String debugLabel() {
        return this.debugLabel;
    }

    public final boolean isExecuting() {
        return this.executingTasks.get() > 0;
    }

    public abstract long trackedPosition();

    protected abstract Execution execution();

    protected abstract void transitionWaiting(WaitReason reason);

    protected abstract void finishWaiting();

    public static <T> Supplier<T> trackExecution(Supplier<T> work) {
        GenerationJob job = CURRENT_JOB.get();
        return job == null ? work : () -> job.execute(work);
    }

    private <T> T execute(Supplier<T> work) {
        GenerationJob previous = CURRENT_JOB.get();
        CURRENT_JOB.set(this);
        this.executingTasks.incrementAndGet();
        try {
            return work.get();
        } finally {
            this.executingTasks.decrementAndGet();
            if (previous == null) {
                CURRENT_JOB.remove();
            } else {
                CURRENT_JOB.set(previous);
            }
        }
    }

    void setSchedulerEpoch(long schedulerEpoch) {
        this.schedulerEpoch = schedulerEpoch;
    }

    long schedulerEpoch() {
        return schedulerEpoch;
    }

    void markStarted() {
        this.started = true;
    }

    void acquireCapacity() {
        this.capacityHeld = true;
    }

    boolean releaseCapacity() {
        if (!this.capacityHeld) {
            return false;
        }
        this.capacityHeld = false;
        return true;
    }

    void cancel() {
        this.finishWaiting();
        if (!this.started && this.cancellationHandled.compareAndSet(false, true)) {
            this.cancelWork.run();
        }
    }

    @Override
    public final void run() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        boolean handedOff = false;
        try {
            if (!this.execution().begin(this)) {
                this.cancel();
                return;
            }
            this.execution().markCurrentWorkerActive();
            if (this.contextClassLoader != null && this.contextClassLoader != previousClassLoader) {
                currentThread.setContextClassLoader(this.contextClassLoader);
            }
            CompletableFuture<?> completion = this.execute(this.work);
            DeferredNotificationBuffer.flush();
            if (completion == null) {
                completion = CompletableFuture.completedFuture(null);
            }
            completion.whenComplete((value, throwable) -> this.execution().onComplete(this));
            handedOff = true;
        } finally {
            if (currentThread.getContextClassLoader() != previousClassLoader) {
                currentThread.setContextClassLoader(previousClassLoader);
            }
            this.execution().markCurrentWorkerIdle();
            if (!handedOff) {
                this.execution().onComplete(this);
            }
        }
    }

    protected static Supplier<CompletableFuture<?>> asAsync(Runnable work) {
        return () -> {
            work.run();
            return CompletableFuture.completedFuture(null);
        };
    }

    public interface Execution {
        boolean begin(GenerationJob job);

        void onComplete(GenerationJob job);

        void markCurrentWorkerActive();

        void markCurrentWorkerIdle();
    }

    public enum WaitReason {
        FOOTPRINT_CONFLICT,
        CAPACITY,
        EXECUTOR_QUEUE
    }
}
