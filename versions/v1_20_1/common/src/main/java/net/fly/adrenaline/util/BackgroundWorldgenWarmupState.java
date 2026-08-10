package net.fly.adrenaline.util;

import net.minecraft.server.MinecraftServer;

public final class BackgroundWorldgenWarmupState {

    public static final int SPAWN_ZONE_RADIUS = 6;

    private static volatile boolean active;
    private static volatile MinecraftServer server;
    private static volatile int spawnZoneRadius = SPAWN_ZONE_RADIUS;

    private BackgroundWorldgenWarmupState() {
    }

    public static void begin() {
        begin(SPAWN_ZONE_RADIUS);
    }

    public static void begin(int radius) {
        server = null;
        spawnZoneRadius = radius;
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

    public static int spawnZoneRadius() {
        return spawnZoneRadius;
    }

    public static void end() {
        active = false;
        server = null;
        spawnZoneRadius = SPAWN_ZONE_RADIUS;
    }
}
