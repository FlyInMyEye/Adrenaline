package net.fly.adrenaline;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.Logger;

@Mod(Adrenaline.MODID)
public class Adrenaline {

    public static final String MODID = "adrenaline";
    public static final Logger LOGGER = GlobalCommon.LOGGER;

    public Adrenaline() {
        AdrenalinePlatform.initialize(FMLPaths.CONFIGDIR.get(), modId -> ModList.get().isLoaded(modId));
        AdrenalinePlatform.setCreateWorldSpawnHook(ForgeEventFactory::onCreateWorldSpawn);
        AdrenalineCommon.init(AdrenalinePlatform.configDirectory());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Adrenaline initialized, worldgen parallelism: {}", ChunkJobScheduler.get().parallelism());
        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            LOGGER.info("Config: worldgenOptimizations={}, terrainFill={}, surface={}, noiseChunk={}, materialRules={}, aquifer={}, beardifier={}, oreVeins={}, parallelWorldgen={}, featureSafetyRadius={}, fastLegacyRandom={}, generationWorkerThreads={}, serializationWorkerThreads={}, spawnZoneRadius={}", AdrenalineConfig.get().worldgenOptimizations, AdrenalineConfig.get().terrainFillOptimizations, AdrenalineConfig.get().surfaceOptimizations, AdrenalineConfig.get().noiseChunkOptimizations, AdrenalineConfig.get().materialRuleOptimizations, AdrenalineConfig.get().aquiferOptimizations, AdrenalineConfig.get().beardifierOptimizations, AdrenalineConfig.get().oreVeinOptimizations, AdrenalineConfig.get().parallelWorldgen, AdrenalineConfig.resolvedFeatureSafetyRadius(), AdrenalineConfig.get().fastLegacyRandom, AdrenalineConfig.get().generationWorkerThreads, AdrenalineConfig.get().serializationWorkerThreads, AdrenalineConfig.get().spawnZoneRadius);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ChunkJob.updatePriorityFoci(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ChunkJob.clearPriorityFoci();
    }
}
