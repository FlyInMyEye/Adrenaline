package net.fly.adrenaline;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("adrenaline")
public final class AdrenalineForge {
    public static final String MODID = "adrenaline";
    private static final Logger LOGGER = LogManager.getLogger(MODID);

    public AdrenalineForge() {
        AdrenalineCommon.init();
        LOGGER.info("Adrenaline initialized on Forge");
    }
}
