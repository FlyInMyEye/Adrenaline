package net.fly.adrenaline;

public final class AdrenalineCommon {
    public static final String MOD_ID = "adrenaline";
    public static final String TARGET_VERSION = "[1.21, 1.21.2)";

    private AdrenalineCommon() {
    }

    public static void init() {
        GlobalCommon.workerPool();
        System.out.println(MOD_ID + " initialized for " + TARGET_VERSION);
    }
}
