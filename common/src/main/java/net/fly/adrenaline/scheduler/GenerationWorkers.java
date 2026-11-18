package net.fly.adrenaline.scheduler;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

final class GenerationWorkers {

    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int WAITING = 2;

    private final boolean debug;
    private volatile WorkerPool pool;

    GenerationWorkers(int threads, boolean debug) {
        this.debug = debug;
        this.pool = this.createPool(threads);
    }

    int parallelism() {
        return this.pool.executor.getParallelism();
    }

    ForkJoinPool executor() {
        return this.pool.executor;
    }

    void resize(int threads) {
        WorkerPool current = this.pool;
        WorkerPool replacement = this.createPool(threads);
        this.pool = replacement;
        current.executor.shutdown();
    }

    int[] snapshot(boolean waitingWork) {
        if (!this.debug) {
            return new int[0];
        }
        int processors = Runtime.getRuntime().availableProcessors();
        int[] snapshot = new int[processors];
        WorkerPool current = this.pool;
        int configuredWorkers = Math.min(current.executor.getParallelism(), processors);
        int active = 0;
        for (GenerationWorkerThread worker : current.workers) {
            if (worker.executionDepth > 0 && worker.getState() == Thread.State.RUNNABLE) {
                active++;
            }
        }
        Arrays.fill(snapshot, 0, configuredWorkers, waitingWork ? WAITING : IDLE);
        Arrays.fill(snapshot, 0, Math.min(active, configuredWorkers), ACTIVE);
        Arrays.fill(snapshot, configuredWorkers, processors, -1);
        return snapshot;
    }

    void markCurrentWorker(int state) {
        if (!this.debug) {
            return;
        }
        Thread thread = Thread.currentThread();
        if (thread instanceof GenerationWorkerThread worker) {
            worker.setState(state);
        }
    }

    private WorkerPool createPool(int threads) {
        Set<GenerationWorkerThread> workers = this.debug ? ConcurrentHashMap.newKeySet() : null;
        ForkJoinPool executor = new ForkJoinPool(threads,
            pool -> new GenerationWorkerThread(pool, workers), null, false);
        return new WorkerPool(executor, workers);
    }

    private record WorkerPool(ForkJoinPool executor, Set<GenerationWorkerThread> workers) {
    }

    private static final class GenerationWorkerThread extends ForkJoinWorkerThread {

        private final Set<GenerationWorkerThread> workers;
        private volatile int executionDepth;

        private GenerationWorkerThread(ForkJoinPool pool, Set<GenerationWorkerThread> workers) {
            super(pool);
            this.workers = workers;
        }

        @Override
        protected void onStart() {
            super.onStart();
            if (this.workers != null) {
                this.workers.add(this);
            }
        }

        @Override
        protected void onTermination(Throwable exception) {
            if (this.workers != null) {
                this.workers.remove(this);
            }
            super.onTermination(exception);
        }

        private void setState(int state) {
            if (this.workers != null) {
                if (state == ACTIVE) {
                    this.executionDepth++;
                } else if (this.executionDepth > 0) {
                    this.executionDepth--;
                }
            }
        }
    }
}
