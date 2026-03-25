package net.fly.adrenaline;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Adrenaline.MODID)
public class Adrenaline {

    public static final String MODID = "adrenaline";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public Adrenaline() {
        LOGGER.info("{} loaded!", MODID);
    }
}
