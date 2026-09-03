package net.fly.adrenaline;

import java.nio.file.Path;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;

public final class AdrenalineCommon {
    public static final String MOD_ID = "adrenaline";
    public static final String TARGET_VERSION = "[1.20, 1.21)";

    private AdrenalineCommon() {
    }

    public static void init(Path configDirectory) {
        AdrenalineNatives.initialize();
        AdrenalineConfig.init(configDirectory);
        GlobalCommon.workerPool();
        GlobalCommon.LOGGER.info("{} initialized for {}", MOD_ID, TARGET_VERSION);
    }
}
