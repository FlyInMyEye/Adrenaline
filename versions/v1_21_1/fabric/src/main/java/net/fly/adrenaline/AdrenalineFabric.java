package net.fly.adrenaline;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.scheduler.ChunkJob;
import net.fly.adrenaline.util.WorldgenStageStats;

public final class AdrenalineFabric implements ModInitializer {
    public static final String MODID = "adrenaline";

    @Override
    public void onInitialize() {
        AdrenalineCommon.init(FabricLoader.getInstance().getConfigDir());
        if (BuildConfig.DEBUG) {
            ServerLifecycleEvents.SERVER_STARTING.register(server -> WorldgenStageStats.reset());
        }
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (AdrenalineConfig.parallelWorldgenEnabled()) {
                ChunkJob.updatePriorityFoci(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ChunkJobScheduler.clearServer(server);
        });
    }
}
