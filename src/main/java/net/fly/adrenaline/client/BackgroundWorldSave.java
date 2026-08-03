package net.fly.adrenaline.client;

import java.nio.file.Path;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public final class BackgroundWorldSave {

    private static volatile IntegratedServer server;
    private static volatile Path worldPath;

    private BackgroundWorldSave() {
    }

    public static void detach(IntegratedServer detachedServer) {
        server = detachedServer;
        worldPath = detachedServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        detachedServer.halt(false);
    }

    public static boolean isRunning() {
        IntegratedServer detachedServer = server;
        return detachedServer != null && detachedServer.getRunningThread().isAlive();
    }

    public static boolean isSaving(Path path) {
        Path savingPath = worldPath;
        return isRunning() && savingPath != null && savingPath.equals(path.toAbsolutePath().normalize());
    }

    public static boolean isSaving(String levelId) {
        Path savingPath = worldPath;
        return isRunning() && savingPath != null && savingPath.getFileName().toString().equals(levelId);
    }

    public static boolean isTracked(String levelId) {
        Path savingPath = worldPath;
        return savingPath != null && savingPath.getFileName().toString().equals(levelId);
    }

    public static void awaitCompletion() {
        IntegratedServer detachedServer = server;
        if (detachedServer == null) {
            return;
        }
        detachedServer.halt(true);
        if (server == detachedServer) {
            server = null;
            worldPath = null;
        }
    }
}
