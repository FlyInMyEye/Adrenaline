package net.fly.adrenaline.util;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

public final class WorldgenBenchmarkHooks {

    private static volatile Consumer<MinecraftServer> preparedHandler;
    private static volatile MinecraftServer preparedServer;
    private static volatile long generationStartedNanos;
    private static volatile long generationNanos;
    private static volatile boolean dispatched;

    private WorldgenBenchmarkHooks() {
    }

    public static void setPreparedHandler(Consumer<MinecraftServer> handler) {
        preparedServer = null;
        generationStartedNanos = 0L;
        generationNanos = 0L;
        dispatched = false;
        preparedHandler = handler;
    }

    public static void beginPass() {
        preparedServer = null;
        generationStartedNanos = 0L;
        generationNanos = 0L;
        dispatched = false;
    }

    public static void clearPreparedHandler() {
        preparedHandler = null;
        preparedServer = null;
    }

    public static void onServerPrepared(MinecraftServer server) {
        preparedServer = server;
        dispatchPreparedHandler();
    }

    public static void onSpawnGenerationStarted() {
        if (preparedHandler != null) {
            generationStartedNanos = System.nanoTime();
        }
    }

    public static void onSpawnGenerationFinished() {
        long startedNanos = generationStartedNanos;
        if (preparedHandler != null && startedNanos != 0L) {
            generationNanos = System.nanoTime() - startedNanos;
            dispatchPreparedHandler();
        }
    }

    public static long generationNanos() {
        return generationNanos;
    }

    private static synchronized void dispatchPreparedHandler() {
        Consumer<MinecraftServer> handler = preparedHandler;
        MinecraftServer server = preparedServer;
        if (dispatched || handler == null || server == null || generationNanos == 0L) {
            return;
        }
        dispatched = true;
        server.execute(() -> handler.accept(server));
    }
}
