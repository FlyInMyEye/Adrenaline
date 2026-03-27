package net.fly.adrenaline;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ForkJoinPool;

@Mod(Adrenaline.MODID)
public class Adrenaline {

    public static final String MODID = "adrenaline";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public Adrenaline() {
        AdrenalineConfig.init();
        LOGGER.info("Adrenaline initialized, worldgen parallelism: {}", ForkJoinPool.commonPool().getParallelism());
        MinecraftForge.EVENT_BUS.register(this);
    }
}
