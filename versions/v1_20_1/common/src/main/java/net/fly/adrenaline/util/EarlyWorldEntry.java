package net.fly.adrenaline.util;

public final class EarlyWorldEntry {

    private static volatile boolean requested;
    private static volatile boolean fullChunkReady;

    private EarlyWorldEntry() {
    }

    public static void reset() {
        requested = false;
        fullChunkReady = false;
    }

    public static void request() {
        requested = true;
    }

    public static boolean isRequested() {
        return requested;
    }

    public static void markFullChunkReady() {
        fullChunkReady = true;
    }

    public static boolean isFullChunkReady() {
        return fullChunkReady;
    }

    public static boolean canEnter() {
        return requested && fullChunkReady;
    }
}
