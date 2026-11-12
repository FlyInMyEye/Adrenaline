package net.fly.adrenaline.scheduler;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

final class GenerationWorkers {

    static final int IDLE = 0;
    static final int ACTIVE = 1;
    static final int WAITING = 2;

    private final boolean debug;
    private volatile AtomicIntegerArray states;
    private volatile ForkJoinPool pool;

    GenerationWorkers(int threads, boolean debug) {
        this.debug = debug;
        this.pool = this.createPool(threads);
    }

    int parallelism() {
        return this.pool.getParallelism();
    }

    ForkJoinPool executor() {
        return this.pool;
    }

    void resize(int threads) {
        ForkJoinPool current = this.pool;
        ForkJoinPool replacement = this.createPool(threads);
        this.pool = replacement;
        current.shutdown();
    }

    int[] snapshot(boolean waitingWork) {
        if (!this.debug) {
            return new int[0];
        }
        int processors = Runtime.getRuntime().availableProcessors();
        int[] snapshot = new int[processors];
        AtomicIntegerArray currentStates = this.states;
        int configuredWorkers = Math.min(this.pool.getParallelism(), processors);
        for (int i = 0; i < configuredWorkers; i++) {
            int state = i < currentStates.length() ? currentStates.get(i) : IDLE;
            snapshot[i] = state == IDLE && waitingWork ? WAITING : state;
        }
        for (int i = configuredWorkers; i < processors; i++) {
            snapshot[i] = -1;
        }
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

    private ForkJoinPool createPool(int threads) {
        AtomicIntegerArray workerStates = this.debug ? new AtomicIntegerArray(threads) : null;
        AtomicInteger nextWorker = new AtomicInteger();
        this.states = workerStates;
        return new ForkJoinPool(threads, pool -> {
            int index = nextWorker.getAndIncrement();
            return new GenerationWorkerThread(pool, workerStates, index);
        }, null, false);
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
            if (this.states != null && this.index < this.states.length()) {
                this.states.set(this.index, state);
            }
        }
    }
}
