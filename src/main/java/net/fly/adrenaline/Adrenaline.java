package net.fly.adrenaline;

import net.fly.adrenaline.client.AdrenalineConfigScreen;
import net.fly.adrenaline.client.FastloadCompatController;
import net.fly.adrenaline.client.ModernFixCompatController;
import net.fly.adrenaline.client.WorldgenStatsOverlay;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
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

            MinecraftForge.EVENT_BUS.register(new ModernFixCompatController());
            MinecraftForge.EVENT_BUS.register(new WorldgenStatsOverlay());

            FastloadCompatController fastloadCompatController = new FastloadCompatController();
            fastloadCompatController.setShouldShow(IncompatibilityRegistry.isLoaded("fastload"));
            MinecraftForge.EVENT_BUS.register(fastloadCompatController);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Adrenaline initialized, worldgen parallelism: {}", ChunkJobScheduler.get().parallelism());
        if (AdrenalineConfig.debugLoggingEnabled()) {
            LOGGER.info("Config: worldgenOptimizations={}, terrainFill={}, surface={}, noiseChunk={}, materialRules={}, aquifer={}, beardifier={}, oreVeins={}, parallelWorldgen={}, featureSafetyRadius={}, fastLegacyRandom={}, generationWorkerThreads={}, serializationWorkerThreads={}, spawnZoneRadius={}", AdrenalineConfig.get().worldgenOptimizations, AdrenalineConfig.get().terrainFillOptimizations, AdrenalineConfig.get().surfaceOptimizations, AdrenalineConfig.get().noiseChunkOptimizations, AdrenalineConfig.get().materialRuleOptimizations, AdrenalineConfig.get().aquiferOptimizations, AdrenalineConfig.get().beardifierOptimizations, AdrenalineConfig.get().oreVeinOptimizations, AdrenalineConfig.get().parallelWorldgen, AdrenalineConfig.resolvedFeatureSafetyRadius(), AdrenalineConfig.get().fastLegacyRandom, AdrenalineConfig.get().generationWorkerThreads, AdrenalineConfig.get().serializationWorkerThreads, AdrenalineConfig.get().spawnZoneRadius);
        }
    }
}
