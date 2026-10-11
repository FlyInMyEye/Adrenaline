package net.fly.adrenaline.client;

import java.nio.file.Path;
import net.fly.adrenaline.mixin.MixinMinecraftFrameInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
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
        Thread serverThread = detachedServer.getRunningThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.isSameThread() && minecraft.isRunning() && serverThread.isAlive()) {
            Screen previous = minecraft.screen;
            Screen waiting = new GenericDirtMessageScreen(Component.translatable("menu.savingLevel"));
            minecraft.setScreen(waiting);
            try {
                minecraft.managedBlock(() -> {
                    if (!serverThread.isAlive()) {
                        return true;
                    }
                    ((MixinMinecraftFrameInvoker) minecraft).adrenaline$runTick(false);
                    return false;
                });
            } finally {
                if (minecraft.screen == waiting) {
                    minecraft.setScreen(previous);
                }
            }
        }
        boolean interrupted = false;
        while (serverThread.isAlive()) {
            try {
                serverThread.join();
            } catch (InterruptedException failure) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (server == detachedServer) {
            server = null;
            worldPath = null;
        }
    }
}
