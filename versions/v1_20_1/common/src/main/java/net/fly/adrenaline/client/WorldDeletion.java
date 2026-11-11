package net.fly.adrenaline.client;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.fly.adrenaline.GlobalCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelStorageSource;

public final class WorldDeletion {

    private static final Set<Thread> PENDING = ConcurrentHashMap.newKeySet();

    private WorldDeletion() {
    }

    public static void deleteAsync(Minecraft minecraft, IntegratedServer server, String levelId) {
        runAsync(() -> delete(minecraft, server, levelId));
    }

    public static void recoverWarmupWorlds(Minecraft minecraft) {
        runAsync(() -> {
            try (Stream<Path> directories = Files.list(minecraft.getLevelSource().getBaseDir())) {
                directories.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(BackgroundWorldgenWarmup::isStaleWarmupLevel)
                    .forEach(levelId -> delete(minecraft, null, levelId));
            } catch (Exception failure) {
                GlobalCommon.LOGGER.warn("Failed to clean up leftover warmup worlds", failure);
            }
        });
    }

    private static void runAsync(Runnable action) {
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } finally {
                PENDING.remove(Thread.currentThread());
            }
        }, "Adrenaline world cleanup");
        thread.setDaemon(true);
        PENDING.add(thread);
        try {
            thread.start();
        } catch (RuntimeException | Error failure) {
            PENDING.remove(thread);
            throw failure;
        }
    }

    public static void awaitCompletion() {
        boolean interrupted = false;
        while (!PENDING.isEmpty()) {
            for (Thread thread : PENDING) {
                try {
                    thread.join();
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void delete(Minecraft minecraft, IntegratedServer server, String levelId) {
        if (server != null && Thread.currentThread() != server.getRunningThread()) {
            try {
                server.getRunningThread().join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(levelId)) {
            access.deleteLevel();
        } catch (Exception exception) {
            GlobalCommon.LOGGER.warn("Failed to delete world {}", levelId, exception);
        }
    }
}
