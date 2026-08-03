package net.fly.adrenaline;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;

public final class AdrenalineFabric implements ModInitializer {
    public static final String MODID = "adrenaline";

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        AdrenalinePlatform.initialize(loader.getConfigDir(), loader::isModLoaded);
        AdrenalineCommon.init(AdrenalinePlatform.configDirectory());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GlobalCommon.LOGGER.info("Adrenaline initialized, worldgen parallelism: {}", ChunkJobScheduler.get().parallelism());
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                GlobalCommon.LOGGER.info("Config: worldgenOptimizations={}, terrainFill={}, surface={}, noiseChunk={}, materialRules={}, aquifer={}, beardifier={}, oreVeins={}, parallelWorldgen={}, featureSafetyRadius={}, fastLegacyRandom={}, generationWorkerThreads={}, serializationWorkerThreads={}, spawnZoneRadius={}", AdrenalineConfig.get().worldgenOptimizations, AdrenalineConfig.get().terrainFillOptimizations, AdrenalineConfig.get().surfaceOptimizations, AdrenalineConfig.get().noiseChunkOptimizations, AdrenalineConfig.get().materialRuleOptimizations, AdrenalineConfig.get().aquiferOptimizations, AdrenalineConfig.get().beardifierOptimizations, AdrenalineConfig.get().oreVeinOptimizations, AdrenalineConfig.get().parallelWorldgen, AdrenalineConfig.resolvedFeatureSafetyRadius(), AdrenalineConfig.get().fastLegacyRandom, AdrenalineConfig.get().generationWorkerThreads, AdrenalineConfig.get().serializationWorkerThreads, AdrenalineConfig.get().spawnZoneRadius);
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(ChunkJob::updatePriorityFoci);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ChunkJob.clearPriorityFoci());
        GlobalCommon.LOGGER.info("Adrenaline initialized on Fabric");
    }
}
