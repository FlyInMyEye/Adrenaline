package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

@Mod.EventBusSubscriber(modid = "adrenaline", value = Dist.CLIENT)
public final class AdrenalineForgeClientCommands {
    private AdrenalineForgeClientCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        if (BuildConfig.DEBUG) {
            WorldgenStatsOverlay.registerCommands(event.getDispatcher(), source -> message -> source.sendSuccess(() -> message, false));
        }
    }
}
