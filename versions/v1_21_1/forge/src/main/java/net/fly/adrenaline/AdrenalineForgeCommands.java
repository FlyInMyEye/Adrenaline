package net.fly.adrenaline;

import net.fly.adrenaline.util.DebugChunkCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "adrenaline")
public final class AdrenalineForgeCommands {
    private AdrenalineForgeCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        if (BuildConfig.DEBUG) {
            DebugChunkCommands.register(event.getDispatcher());
        }
    }
}
