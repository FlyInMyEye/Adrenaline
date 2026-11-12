package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = "adrenaline", value = Dist.CLIENT)
public final class AdrenalineNeoForgeClientCommands {
    private AdrenalineNeoForgeClientCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        if (BuildConfig.DEBUG) {
            WorldgenStatsOverlay.registerCommands(event.getDispatcher(), source -> message -> source.sendSuccess(() -> message, false));
        }
    }
}
