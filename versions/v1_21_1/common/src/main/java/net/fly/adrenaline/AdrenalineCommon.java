package net.fly.adrenaline;

import java.nio.file.Path;
import net.fly.adrenaline.compat.ModBlacklist;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.compatdata.ModernFixCompat;
import net.fly.adrenaline.natives.AdrenalineNatives;

public final class AdrenalineCommon {
    public static final String MOD_ID = "adrenaline";
    public static final String TARGET_VERSION = "[1.21, 1.21.2)";

    private AdrenalineCommon() {
    }

    public static void init(Path configDirectory) {
        AdrenalineConfig.init(configDirectory);
        ModBlacklist.initialize();
        ModernFixCompat.registerOverrideOwners();
        AdrenalineNatives.initialize();
        GlobalCommon.LOGGER.info("Adrenaline initialized for {}", TARGET_VERSION);
    }
}
