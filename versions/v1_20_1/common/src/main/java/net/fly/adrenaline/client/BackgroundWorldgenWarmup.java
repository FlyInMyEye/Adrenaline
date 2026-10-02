package net.fly.adrenaline.client;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.fly.adrenaline.util.WarmupResourceLoading;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

public final class BackgroundWorldgenWarmup {

    private static final String LEVEL_PREFIX = "adrenaline_warmup_";
    private static final char[] SPINNER = {'-', '\\', '|', '/'};
    private static final ThreadLocal<Boolean> WARMUP_CALL = ThreadLocal.withInitial(() -> false);

    private static boolean started;
    private static boolean running;
    private static String levelId;
    private static volatile IntegratedServer server;
    private static CompletableFuture<WorldStem> loading;

    public void tick(Minecraft minecraft) {
        if (!ClientResourceReloadState.isReady()) {
            return;
        }
        Screen screen = minecraft.screen;
        if (!AdrenalineConfig.prepareWorldCreationContext()) {
            WorldCreationContextWaiter.consume();
        }
        AdrenalineConfig.WarmupMode warmupMode = AdrenalineConfig.warmupMode();
        boolean completed = server != null && (server.isReady() || server.isShutdown());
        if (running && (warmupMode == AdrenalineConfig.WarmupMode.OFF || completed || screen instanceof ConnectScreen)) {
            stop(minecraft);
        }
        if (!started && warmupMode != AdrenalineConfig.WarmupMode.OFF && screen instanceof TitleScreen && minecraft.getSingleplayerServer() == null && !BackgroundWorldSave.isRunning()) {
            started = true;
            start(minecraft);
        }
        if ((!running || server != null) && AdrenalineConfig.prepareWorldCreationContext() && minecraft.getSingleplayerServer() == null && !BackgroundWorldSave.isRunning() && (screen instanceof TitleScreen || screen instanceof SelectWorldScreen) && WorldCreationContextWaiter.canPreload()) {
            WorldCreationContextWaiter.preload(minecraft);
        }
    }

    public static boolean isRunning() {
        return running || WorldCreationContextWaiter.isPreloading();
    }

    public static boolean isBlocking() {
        return running && AdrenalineConfig.warmupMode() == AdrenalineConfig.WarmupMode.ON;
    }

    public static String branding() {
        char spinner = SPINNER[(int) (System.currentTimeMillis() / 150L % SPINNER.length)];
        return "Adrenaline warmup " + spinner;
    }

    public static void beforeWorldLoad(Minecraft minecraft, String requestedLevelId) {
        if (running && levelId.equals(requestedLevelId)) {
            WARMUP_CALL.set(true);
        } else if (running) {
            stop(minecraft);
        }
    }

    public static boolean isWarmupCall() {
        return WARMUP_CALL.get();
    }

    public static boolean isWarmupLevel(String requestedLevelId) {
        return requestedLevelId.startsWith(LEVEL_PREFIX);
    }

    public static void detachWorldLoad(IntegratedServer detachedServer) {
        server = detachedServer;
        BackgroundWorldgenWarmupState.attach(detachedServer);
        WARMUP_CALL.remove();
    }

    public static void stop(Minecraft minecraft) {
        if (!running) {
            return;
        }

        running = false;
        BackgroundWorldgenWarmupState.end();
        WARMUP_CALL.remove();
        IntegratedServer detachedServer = server;
        server = null;
        boolean pendingLoad = loading != null;
        loading = null;
        if (detachedServer != null) {
            detachedServer.halt(false);
        }
        if (!pendingLoad) {
            WorldDeletion.deleteAsync(minecraft, detachedServer, levelId);
        }
    }

    public static void exitWorldLoad(String exitedLevelId) {
        if (levelId != null && levelId.equals(exitedLevelId)) {
            WARMUP_CALL.remove();
        }
    }

    private static void start(Minecraft minecraft) {
        running = true;
        levelId = LEVEL_PREFIX + UUID.randomUUID().toString().replace("-", "");
        server = null;

        LevelSettings settings = new LevelSettings(
            "Adrenaline Warmup",
            GameType.SURVIVAL,
            false,
            Difficulty.NORMAL,
            false,
            new GameRules(),
            WorldDataConfiguration.DEFAULT
        );
        String warmupLevelId = levelId;
        LevelStorageSource.LevelStorageAccess access;
        try {
            access = minecraft.getLevelSource().validateAndCreateAccess(warmupLevelId);
        } catch (Exception failure) {
            GlobalCommon.LOGGER.warn("Failed to create warmup world", failure);
            stop(minecraft);
            return;
        }
        PackRepository packs = ServerPacksSource.createPackRepository(access);
        WorldOptions options = WorldOptions.defaultWithRandomSeed();
        CompletableFuture<WorldStem> current = WarmupResourceLoading.load(
            new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs, settings.getDataConfiguration(), false, false), Commands.CommandSelection.INTEGRATED, 2),
            context -> {
                WorldDimensions.Complete dimensions = WorldPresets.createNormalWorldDimensions(context.datapackWorldgen())
                    .bake(context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
                return new WorldLoader.DataLoadOutput<>(
                    new PrimaryLevelData(settings, options, dimensions.specialWorldProperty(), dimensions.lifecycle()),
                    dimensions.dimensionsRegistryAccess()
                );
            },
            WorldStem::new,
            Util.backgroundExecutor(),
            minecraft
        );
        loading = current;
        current.whenCompleteAsync((stem, failure) -> {
            if (loading != current || !minecraft.isRunning()) {
                closeLoad(access, stem);
                WorldDeletion.deleteAsync(minecraft, null, warmupLevelId);
                return;
            }
            if (minecraft.screen instanceof ConnectScreen || minecraft.getSingleplayerServer() != null || AdrenalineConfig.warmupMode() == AdrenalineConfig.WarmupMode.OFF) {
                stop(minecraft);
                closeLoad(access, stem);
                WorldDeletion.deleteAsync(minecraft, null, warmupLevelId);
                return;
            }
            loading = null;
            if (failure != null) {
                GlobalCommon.LOGGER.warn("Failed to load warmup world", failure);
                closeLoad(access, stem);
                stop(minecraft);
                return;
            }
            BackgroundWorldgenWarmupState.begin();
            try {
                minecraft.doWorldLoad(warmupLevelId, access, packs, stem, true);
            } catch (RuntimeException launchFailure) {
                GlobalCommon.LOGGER.warn("Failed to start warmup server", launchFailure);
                if (server == null) {
                    server = minecraft.getSingleplayerServer();
                    if (server != null) {
                        minecraft.clearLevel(minecraft.screen);
                    } else {
                        closeLoad(access, stem);
                    }
                }
                stop(minecraft);
                return;
            } finally {
                WARMUP_CALL.remove();
            }
            if (server == null) {
                closeLoad(access, stem);
                stop(minecraft);
            } else if (AdrenalineConfig.prepareWorldCreationContext()) {
                WorldCreationContextWaiter.preload(minecraft);
            }
        }, minecraft);
    }

    private static void closeLoad(LevelStorageSource.LevelStorageAccess access, WorldStem stem) {
        if (stem != null) {
            stem.close();
        }
        try {
            access.close();
        } catch (Exception failure) {
            GlobalCommon.LOGGER.warn("Failed to close warmup world", failure);
        }
    }

}
