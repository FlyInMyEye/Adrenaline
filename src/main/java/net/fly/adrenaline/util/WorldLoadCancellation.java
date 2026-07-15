package net.fly.adrenaline.util;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldLoadCancellation {

    private static final AtomicBoolean REQUESTED = new AtomicBoolean();

    private WorldLoadCancellation() {
    }

    public static void reset() {
        REQUESTED.set(false);
    }

    public static void request() {
        REQUESTED.set(true);
    }

    public static boolean isRequested() {
        return REQUESTED.get();
    }

}
