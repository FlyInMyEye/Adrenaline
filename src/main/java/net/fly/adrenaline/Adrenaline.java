package net.fly.adrenaline;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkWorkerPool;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Adrenaline.MODID)
public class Adrenaline {

    public static final String MODID = "adrenaline";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public Adrenaline() {
        AdrenalineConfig.init();

        int threads = AdrenalineConfig.resolvedWorkerThreads();
        ChunkWorkerPool.init(threads);
        LOGGER.info("Adrenaline worker pool initialized with {} threads", threads);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ChunkWorkerPool.shutdown();
        LOGGER.info("Adrenaline worker pool shut down");
    }
}
