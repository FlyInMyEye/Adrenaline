package net.fly.adrenaline;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.natives.NativeHardwareInfo;
import net.fly.adrenaline.natives.NativeRuntimeStats;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
        if (BuildConfig.DEBUG) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("adrenaline")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("stats")
                        .then(Commands.literal("on").executes(context -> setWorldgenStats(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setWorldgenStats(context.getSource(), false))))
                    .then(Commands.literal("natives")
                        .then(Commands.literal("stats")
                            .executes(context -> {
                                context.getSource().sendSuccess(() -> Component.literal(NativeRuntimeStats.summary()), false);
                                return 1;
                            }))
                        .then(Commands.literal("hardware")
                            .executes(context -> {
                                for (String line : NativeHardwareInfo.lines()) {
                                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                                }
                                return 1;
                            })))
            ));
        }
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ChunkJob.clearPriorityFoci());
        GlobalCommon.LOGGER.info("Adrenaline initialized on Fabric");
    }

    private static int setWorldgenStats(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        WorldgenStageStats.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled ? "message.adrenaline.stats.enabled" : "message.adrenaline.stats.disabled"), false);
        return 1;
    }
}
