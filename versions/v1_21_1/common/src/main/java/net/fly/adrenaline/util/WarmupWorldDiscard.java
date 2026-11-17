package net.fly.adrenaline.util;

import java.lang.ref.WeakReference;
import net.minecraft.server.MinecraftServer;

public final class WarmupWorldDiscard {

    private static final ThreadLocal<Boolean> DISCARDING = new ThreadLocal<>();
    private static volatile WeakReference<MinecraftServer> server = new WeakReference<>(null);

    private WarmupWorldDiscard() {
    }

    public static void mark(MinecraftServer warmupServer) {
        server = new WeakReference<>(warmupServer);
    }

    public static boolean isServer(MinecraftServer candidate) {
        return candidate != null && (server.get() == candidate || BackgroundWorldgenWarmupState.isServer(candidate));
    }

    public static boolean isDiscarding() {
        return Boolean.TRUE.equals(DISCARDING.get());
    }

    public static void runShutdown(MinecraftServer candidate, Runnable shutdown) {
        boolean previous = isDiscarding();
        if (isServer(candidate)) {
            DISCARDING.set(true);
        } else {
            DISCARDING.remove();
        }
        try {
            shutdown.run();
        } finally {
            if (previous) {
                DISCARDING.set(true);
            } else {
                DISCARDING.remove();
            }
        }
    }
}
