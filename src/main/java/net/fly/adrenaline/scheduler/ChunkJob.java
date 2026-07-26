package net.fly.adrenaline.scheduler;

import net.fly.adrenaline.util.DeferredNotificationBuffer;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.WaitingReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class ChunkJob implements Runnable {

    private final Set<Long> footprint;
    private final Supplier<CompletableFuture<?>> work;
    private final Runnable cancelWork;
    private final ClassLoader contextClassLoader;
    private final String debugLabel;
    private final AtomicBoolean cancellationHandled = new AtomicBoolean();
    private long schedulerEpoch = Long.MIN_VALUE;
    private volatile boolean started;
    private ChunkPos waitingPos;
    private ChunkStatus waitingStatus;
    private WaitingReason waitingReason;
    private long waitingStartedNanos;
    private boolean capacityHeld;
    private boolean yielded;

    public ChunkJob(ChunkPos center, int writeRadius, Runnable work, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), asAsync(work), () -> {
        }, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(ChunkPos center, int writeRadius, Runnable work, Runnable cancelWork, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), asAsync(work), cancelWork, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(ChunkPos center, int writeRadius, Supplier<CompletableFuture<?>> work, Runnable cancelWork, ClassLoader contextClassLoader) {
        this(buildFootprint(center, writeRadius), work, cancelWork, contextClassLoader, center.x + "," + center.z);
    }

    public ChunkJob(Set<Long> footprint, Runnable work, ClassLoader contextClassLoader) {
        this(footprint, asAsync(work), () -> {
        }, contextClassLoader, "custom");
    }

    public ChunkJob(Set<Long> footprint, Runnable work, ClassLoader contextClassLoader, String debugLabel) {
        this(footprint, asAsync(work), () -> {
        }, contextClassLoader, debugLabel);
    }

    public ChunkJob(Set<Long> footprint, Runnable work, Runnable cancelWork, ClassLoader contextClassLoader, String debugLabel) {
        this(footprint, asAsync(work), cancelWork, contextClassLoader, debugLabel);
    }

    public ChunkJob(Set<Long> footprint, Supplier<CompletableFuture<?>> work, Runnable cancelWork, ClassLoader contextClassLoader, String debugLabel) {
        this.footprint = new HashSet<>(footprint);
        this.work = work;
        this.cancelWork = cancelWork;
        this.contextClassLoader = contextClassLoader;
        this.debugLabel = debugLabel;
    }

    public Set<Long> footprint() {
        return footprint;
    }

    public String debugLabel() {
        return debugLabel;
    }

    public ChunkJob trackWaiting(ChunkPos pos, ChunkStatus status) {
        this.waitingPos = pos;
        this.waitingStatus = status;
        return this;
    }

    int stageIndex() {
        return this.waitingStatus == null ? Integer.MIN_VALUE : this.waitingStatus.getIndex();
    }

    synchronized void transitionWaiting(WaitingReason reason) {
        if (this.waitingPos == null || this.waitingStatus == null || !WorldgenStageStats.isEnabled()) {
            return;
        }
        long now = System.nanoTime();
        if (this.waitingReason != null) {
            WorldgenStageStats.addWaitingInterval(this.waitingPos, this.waitingStatus, this.waitingReason, this.waitingStartedNanos, now);
        }
        this.waitingReason = reason;
        this.waitingStartedNanos = now;
    }

    synchronized void finishWaiting() {
        if (this.waitingReason == null) {
            return;
        }
        long now = System.nanoTime();
        WorldgenStageStats.addWaitingInterval(this.waitingPos, this.waitingStatus, this.waitingReason, this.waitingStartedNanos, now);
        this.waitingReason = null;
        this.waitingStartedNanos = 0L;
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

    boolean markYielded() {
        if (this.yielded) {
            return false;
        }
        this.yielded = true;
        return true;
    }

    boolean clearYielded() {
        if (!this.yielded) {
            return false;
        }
        this.yielded = false;
        return true;
    }

    void cancel() {
        this.finishWaiting();
        if (!this.started && this.cancellationHandled.compareAndSet(false, true)) {
            this.cancelWork.run();
        }
    }

    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        boolean began = false;
        boolean handedOff = false;
        try {
            if (!ChunkJobScheduler.get().begin(this)) {
                this.cancel();
                return;
            }
            began = true;
            ChunkJobScheduler.get().markCurrentWorkerActive();
            if (this.contextClassLoader != null && this.contextClassLoader != previousClassLoader) {
                currentThread.setContextClassLoader(this.contextClassLoader);
            }
            CompletableFuture<?> completion = this.work.get();
            DeferredNotificationBuffer.flush();
            ChunkJobScheduler.get().releaseWorker(this);
            if (completion == null) {
                completion = CompletableFuture.completedFuture(null);
            }
            completion.whenComplete((value, throwable) -> ChunkJobScheduler.get().onComplete(this));
            handedOff = true;
        } finally {
            if (currentThread.getContextClassLoader() != previousClassLoader) {
                currentThread.setContextClassLoader(previousClassLoader);
            }
            ChunkJobScheduler.get().markCurrentWorkerIdle();
            if (began && !handedOff) {
                ChunkJobScheduler.get().onComplete(this);
            }
        }
    }

    private static Supplier<CompletableFuture<?>> asAsync(Runnable work) {
        return () -> {
            work.run();
            return CompletableFuture.completedFuture(null);
        };
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
