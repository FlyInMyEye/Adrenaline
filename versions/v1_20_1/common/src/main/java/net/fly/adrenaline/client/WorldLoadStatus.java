package net.fly.adrenaline.client;

public final class WorldLoadStatus {

    private WorldLoadStatus() {
    }

    public static String text() {
        if (BackgroundWorldgenWarmup.isRunning()) {
            return BackgroundWorldgenWarmup.branding();
        }
        if (BackgroundWorldSave.isRunning()) {
            return "Adrenaline saving world...";
        }
        return null;
    }
}
