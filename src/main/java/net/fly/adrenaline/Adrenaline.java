package net.fly.adrenaline;

import net.fly.adrenaline.client.AdrenalineConfigScreen;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.ClassPreloader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Adrenaline.MODID)
public class Adrenaline {

    public static final String MODID = "adrenaline";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public Adrenaline() {
        AdrenalineConfig.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new AdrenalineConfigScreen(screen))
            );
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (AdrenalineConfig.get().preloadProblematicClasses) {
            ClassPreloader.preloadKnownProblematicClasses();
        }
        LOGGER.info("Adrenaline initialized, worldgen parallelism: {}", ChunkJobScheduler.get().parallelism());
        if (AdrenalineConfig.debugLoggingEnabled()) {
            LOGGER.info("Config: enabled={}, worldgenOptimizations={}, terrainFill={}, surface={}, noiseChunk={}, materialRules={}, parallelWorldgen={}, parallelChunkSerialization={}, chunkIoCache={}, fastLegacyRandom={}, preloadProblematicClasses={}, workerThreads={}, spawnZoneRadius={}", AdrenalineConfig.get().enabled, AdrenalineConfig.get().worldgenOptimizations, AdrenalineConfig.get().terrainFillOptimizations, AdrenalineConfig.get().surfaceOptimizations, AdrenalineConfig.get().noiseChunkOptimizations, AdrenalineConfig.get().materialRuleOptimizations, AdrenalineConfig.get().parallelWorldgen, AdrenalineConfig.get().parallelChunkSerialization, AdrenalineConfig.get().chunkIoCache, AdrenalineConfig.get().fastLegacyRandom, AdrenalineConfig.get().preloadProblematicClasses, AdrenalineConfig.get().workerThreads, AdrenalineConfig.get().spawnZoneRadius);
        }
    }
}
