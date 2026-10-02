package net.fly.adrenaline.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.util.WorldgenPreparation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.server.WorldLoader;

public final class WorldCreationContextWaiter {

    private static CompletableFuture<WorldCreationContext> future;
    private static boolean worldLoadActive;
    private static boolean capturingPreload;

    private WorldCreationContextWaiter() {
    }

    public static WorldCreationContext get() {
        CompletableFuture<WorldCreationContext> current = future;
        return current != null && current.isDone() && !current.isCompletedExceptionally() ? current.getNow(null) : null;
    }

    public static void set(WorldCreationContext context) {
        future = CompletableFuture.completedFuture(context);
        prepare(context);
    }

    private static void prepare(WorldCreationContext context) {
        if (context.options().generateStructures()) {
            WorldgenPreparation.prepare(context.selectedDimensions().overworld(), context.worldgenLoadContext(), context.options().seed());
        } else {
            WorldgenPreparation.clear();
        }
    }

    public static boolean isPreloading() {
        return future != null && !future.isDone();
    }

    public static boolean canPreload() {
        return !worldLoadActive && future == null;
    }

    public static boolean isCapturingPreload() {
        return capturingPreload;
    }

    public static void preload(Minecraft minecraft) {
        if (!canPreload()) {
            return;
        }
        capturingPreload = true;
        try {
            CreateWorldScreen.openFresh(minecraft, minecraft.screen);
        } finally {
            capturingPreload = false;
        }
        CompletableFuture<WorldCreationContext> current = future;
        if (current == null) {
            return;
        }
        current.whenCompleteAsync((context, failure) -> {
            if (future == current) {
                if (failure == null) {
                    prepare(context);
                } else {
                    GlobalCommon.LOGGER.warn("Failed to prepare world creation context", failure);
                }
            }
        }, minecraft);
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
            future = (CompletableFuture<WorldCreationContext>) (CompletableFuture<?>) CompletableFuture.supplyAsync(
                () -> WorldLoader.load(initConfig, dataSupplier, resultFactory, backgroundExecutor, gameExecutor),
                backgroundExecutor
            ).thenCompose(current -> current);
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
