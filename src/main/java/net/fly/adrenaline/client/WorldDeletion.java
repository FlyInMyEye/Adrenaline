package net.fly.adrenaline.client;

import net.fly.adrenaline.Adrenaline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelStorageSource;

public final class WorldDeletion {

    private WorldDeletion() {
    }

    public static void deleteAsync(Minecraft minecraft, IntegratedServer server, String levelId) {
        Thread thread = new Thread(() -> delete(minecraft, server, levelId), "Adrenaline world cleanup");
        thread.setDaemon(true);
        thread.start();
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
            Adrenaline.LOGGER.warn("Failed to delete world {}", levelId, exception);
        }
    }
}
