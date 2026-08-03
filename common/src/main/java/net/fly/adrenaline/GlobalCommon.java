package net.fly.adrenaline;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class GlobalCommon {
    public static final Logger LOGGER = LogManager.getLogger("adrenaline");
    private static final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(2);

    private GlobalCommon() {
    }

    public static ExecutorService workerPool() {
        return WORKER_POOL;
    }
}
