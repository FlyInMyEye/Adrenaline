package net.fly.adrenaline.client;

public final class WorldLoadStatus {

    private WorldLoadStatus() {
    }

    public static String text() {
        return BackgroundWorldSave.isRunning() ? "Adrenaline saving world..." : null;
    }
}
