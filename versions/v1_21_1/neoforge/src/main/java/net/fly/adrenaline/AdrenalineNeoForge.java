package net.fly.adrenaline;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AdrenalineNeoForge.MODID)
public final class AdrenalineNeoForge {
    public static final String MODID = "adrenaline";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AdrenalineNeoForge() {
        AdrenalineCommon.init();
        LOGGER.info("Adrenaline initialized on NeoForge");
    }
}
