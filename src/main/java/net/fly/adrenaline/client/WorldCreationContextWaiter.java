package net.fly.adrenaline.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.server.WorldLoader;

public final class WorldCreationContextWaiter {

    private static CompletableFuture<WorldCreationContext> future;
    private static boolean preloading;
    private static boolean worldLoadActive;

    private WorldCreationContextWaiter() {
    }

    public static WorldCreationContext get() {
        CompletableFuture<WorldCreationContext> current = future;
        return current != null && current.isDone() && !current.isCompletedExceptionally() ? current.getNow(null) : null;
    }

    public static void set(WorldCreationContext context) {
        future = CompletableFuture.completedFuture(context);
    }

    public static boolean isPreloading() {
        return preloading;
    }

    public static boolean canPreload() {
        return !worldLoadActive && !preloading && get() == null;
    }

    public static void preload(Minecraft minecraft) {
        if (preloading || future != null && !future.isCompletedExceptionally()) {
            return;
        }
        future = null;
        preloading = true;
        try {
            CreateWorldScreen.openFresh(minecraft, minecraft.screen);
        } finally {
            preloading = false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <D, R> CompletableFuture<R> load(
        WorldLoader.InitConfig initConfig,
        WorldLoader.WorldDataSupplier<D> dataSupplier,
        WorldLoader.ResultFactory<D, R> resultFactory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        if (future == null || future.isCompletedExceptionally()) {
            future = (CompletableFuture<WorldCreationContext>) (CompletableFuture<?>) WorldLoader.load(
                initConfig,
                dataSupplier,
                resultFactory,
                backgroundExecutor,
                gameExecutor
            );
        }
        return (CompletableFuture<R>) (CompletableFuture<?>) future;
    }

    public static void consume() {
        future = null;
    }

    public static void beginWorldLoad() {
        worldLoadActive = true;
    }

    public static void finishWorldLoad() {
        worldLoadActive = false;
    }
}
