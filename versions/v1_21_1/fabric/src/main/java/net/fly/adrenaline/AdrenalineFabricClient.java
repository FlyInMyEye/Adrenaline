package net.fly.adrenaline;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fly.adrenaline.client.WorldgenStatsOverlay;

public final class AdrenalineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (BuildConfig.DEBUG) {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                WorldgenStatsOverlay.registerCommands(dispatcher, source -> source::sendFeedback));
        }
    }
}
