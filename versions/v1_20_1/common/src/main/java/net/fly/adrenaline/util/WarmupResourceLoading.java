package net.fly.adrenaline.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.minecraft.server.WorldLoader;

public final class WarmupResourceLoading {

    private static final ThreadLocal<Boolean> MINIMAL = ThreadLocal.withInitial(() -> false);
    private static final Executor APPLY_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Adrenaline data reload");
        thread.setDaemon(true);
        return thread;
    });

    private WarmupResourceLoading() {
    }

    public static boolean isMinimal() {
        return MINIMAL.get();
    }

    public static Executor applyExecutor() {
        return APPLY_EXECUTOR;
    }

    public static <D, R> CompletableFuture<R> load(
        WorldLoader.InitConfig config,
        WorldLoader.WorldDataSupplier<D> supplier,
        WorldLoader.ResultFactory<D, R> factory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            MINIMAL.set(true);
            try {
                return WorldLoader.load(config, supplier, factory, backgroundExecutor, APPLY_EXECUTOR);
            } finally {
                MINIMAL.remove();
            }
        }, backgroundExecutor).thenCompose(future -> future).thenApplyAsync(result -> result, gameExecutor);
    }
}
