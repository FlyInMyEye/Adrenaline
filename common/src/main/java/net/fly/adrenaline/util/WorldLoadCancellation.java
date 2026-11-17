package net.fly.adrenaline.util;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldLoadCancellation {

    private static final AtomicBoolean REQUESTED = new AtomicBoolean();
    private static volatile String levelId;
    private static volatile boolean deleteOnCancel;
    private static volatile boolean clientHandled;

    private WorldLoadCancellation() {
    }

    public static void reset(String activeLevelId, boolean newWorld) {
        REQUESTED.set(false);
        levelId = activeLevelId;
        deleteOnCancel = newWorld;
        clientHandled = false;
    }

    public static void request() {
        REQUESTED.set(true);
    }

    public static boolean isRequested() {
        return REQUESTED.get();
    }

    public static void markClientHandled() {
        clientHandled = true;
    }

    public static boolean isClientHandled() {
        return clientHandled;
    }

    public static String levelToDelete() {
        return REQUESTED.get() && deleteOnCancel ? levelId : null;
    }

    public static boolean isNewWorld() {
        return deleteOnCancel;
    }

}
