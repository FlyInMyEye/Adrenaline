package net.fly.adrenaline.util;

import java.util.ArrayList;
import java.util.List;

public final class DeferredNotificationBuffer {

    private static final ThreadLocal<List<Runnable>> BUFFER = ThreadLocal.withInitial(ArrayList::new);

    private DeferredNotificationBuffer() {
    }

    public static void enqueue(Runnable notification) {
        BUFFER.get().add(notification);
    }

    public static void flush() {
        List<Runnable> pending = BUFFER.get();
        for (Runnable r : pending) {
            r.run();
        }
        pending.clear();
    }
}
