package net.fly.adrenaline.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class AdrenalineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernFixCompatController modernFixController = new ModernFixCompatController();
        BackgroundWorldgenWarmup worldgenWarmup = new BackgroundWorldgenWarmup();
        FastloadCompatController fastloadController = new FastloadCompatController();
        fastloadController.setShouldShow(FabricLoader.getInstance().isModLoaded("fastload"));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            modernFixController.tick(minecraft);
            worldgenWarmup.tick(minecraft);
            fastloadController.tick(minecraft);
        });
    }
}
