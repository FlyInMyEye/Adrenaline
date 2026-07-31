package net.fly.adrenaline.client;

import java.util.UUID;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BackgroundWorldgenWarmup {

    private static final String LEVEL_PREFIX = "adrenaline_warmup_";
    private static final char[] SPINNER = {'-', '\\', '|', '/'};
    private static final ThreadLocal<Boolean> WARMUP_CALL = ThreadLocal.withInitial(() -> false);

    private static boolean started;
    private static boolean running;
    private static String levelId;
    private static volatile IntegratedServer server;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!AdrenalineConfig.prepareWorldCreationContext() && WorldCreationContextWaiter.get() != null) {
            WorldCreationContextWaiter.consume();
        }
        AdrenalineConfig.WarmupMode warmupMode = AdrenalineConfig.warmupMode();
        boolean completed = server != null && server.isReady();
        if (running && (warmupMode == AdrenalineConfig.WarmupMode.OFF || completed || screen instanceof ConnectScreen)) {
            stop(minecraft);
        }
        if (!started && warmupMode != AdrenalineConfig.WarmupMode.OFF && screen instanceof TitleScreen && minecraft.getSingleplayerServer() == null) {
            started = true;
            if (AdrenalineConfig.prepareWorldCreationContext()) {
                WorldCreationContextWaiter.preload(minecraft);
            }
            start(minecraft);
            if (warmupMode == AdrenalineConfig.WarmupMode.ON && running) {
                minecraft.managedBlock(() -> !running || server != null && (server.isReady() || server.isShutdown()));
                stop(minecraft);
            }
        }
        if (started && !running && AdrenalineConfig.prepareWorldCreationContext() && minecraft.getSingleplayerServer() == null && (screen instanceof TitleScreen || screen instanceof SelectWorldScreen) && WorldCreationContextWaiter.canPreload()) {
            WorldCreationContextWaiter.preload(minecraft);
        }
    }

    public static boolean isRunning() {
        return running || WorldCreationContextWaiter.isPreloading();
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
        if (detachedServer != null) {
            detachedServer.halt(false);
        }
        WorldDeletion.deleteAsync(minecraft, detachedServer, levelId);
    }

    public static void exitWorldLoad(String exitedLevelId) {
        if (levelId != null && levelId.equals(exitedLevelId)) {
            WARMUP_CALL.remove();
        }
    }

    private static void start(Minecraft minecraft) {
        running = true;
        BackgroundWorldgenWarmupState.begin();
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
        minecraft.createWorldOpenFlows().createFreshLevel(
            levelId,
            settings,
            WorldOptions.defaultWithRandomSeed(),
            WorldPresets::createNormalWorldDimensions
        );
        if (running && server == null) {
            running = false;
            BackgroundWorldgenWarmupState.end();
            WorldDeletion.deleteAsync(minecraft, null, levelId);
        }
    }

}
