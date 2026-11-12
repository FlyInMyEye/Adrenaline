package net.fly.adrenaline;

import net.fabricmc.api.ClientModInitializer;
import net.fly.adrenaline.client.WorldgenStatsOverlay;

public final class AdrenalineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (BuildConfig.DEBUG) {
            WorldgenStatsOverlay.initialize();
        }
    }
}
