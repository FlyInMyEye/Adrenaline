package net.fly.adrenaline.scheduler;

import java.util.concurrent.ForkJoinPool;

public final class ChunkWorkerPool {

    private static ForkJoinPool INSTANCE;

    private ChunkWorkerPool() {
    }

    public static synchronized void init(int threads) {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
        }
        INSTANCE = new ForkJoinPool(threads);
    }

    public static void submit(Runnable task) {
        ForkJoinPool pool = INSTANCE;
        if (pool == null) {
            task.run();
            return;
        }
        pool.execute(task);
    }

    public static synchronized void shutdown() {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
            INSTANCE = null;
        }
    }
}
