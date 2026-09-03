package net.fly.adrenaline;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.natives.NativeHardwareInfo;
import net.fly.adrenaline.natives.NativeRuntimeStats;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
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
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        event.getDispatcher().register(
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
        );
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ChunkJob.clearPriorityFoci();
    }

    private static int setWorldgenStats(CommandSourceStack source, boolean enabled) {
        WorldgenStageStats.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled ? "message.adrenaline.stats.enabled" : "message.adrenaline.stats.disabled"), false);
        return 1;
    }
}
