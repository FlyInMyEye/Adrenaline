package net.fly.adrenaline.util;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.config.AdrenalineConfig;

public final class WorldgenReference {
    private static volatile long result;
    private static volatile long[] allocation;

    private WorldgenReference() {
    }

    public static long measure() {
        if (!BuildConfig.DEBUG || !WorldgenStageStats.isEnabled() || AdrenalineConfig.hasGenerationOverride()) {
            return 0L;
        }
        long startedNanos = System.nanoTime();
        long state = 0x243f6a8885a308d3L;
        for (int block = 0; block < 8; block++) {
            long[] values = new long[8192];
            for (int i = 0; i < values.length; i++) {
                state = Long.rotateLeft(state, 17) * 0x9e3779b97f4a7c15L + i;
                values[i] = state;
            }
            for (int pass = 0; pass < 16; pass++) {
                for (int i = 0; i < values.length; i++) {
                    state = Long.rotateLeft(state ^ values[i], 23) * 0xbf58476d1ce4e5b9L;
                    values[i] = state;
                }
            }
            allocation = values;
        }
        result = state;
        return System.nanoTime() - startedNanos;
    }
}
