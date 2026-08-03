package net.fly.adrenaline.util;

import net.minecraft.server.MinecraftServer;

public final class BackgroundWorldgenWarmupState {

    public static final int SPAWN_ZONE_RADIUS = 6;

    private static volatile boolean active;
    private static volatile MinecraftServer server;

    private BackgroundWorldgenWarmupState() {
    }

    public static void begin() {
        server = null;
        active = true;
    }

    public static void attach(MinecraftServer detachedServer) {
        server = detachedServer;
    }

    public static boolean isServer(MinecraftServer candidate) {
        if (active && server == null) {
            server = candidate;
        }
        return active && server == candidate;
    }

    public static void end() {
        active = false;
        server = null;
    }
}
