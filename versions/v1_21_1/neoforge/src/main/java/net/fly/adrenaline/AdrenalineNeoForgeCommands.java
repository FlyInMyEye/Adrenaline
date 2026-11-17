package net.fly.adrenaline;

import net.fly.adrenaline.util.DebugChunkCommands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "adrenaline")
public final class AdrenalineNeoForgeCommands {
    private AdrenalineNeoForgeCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        if (BuildConfig.DEBUG) {
            DebugChunkCommands.register(event.getDispatcher());
        }
    }
}
