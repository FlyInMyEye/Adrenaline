package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class AdrenalineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModernFixCompatController modernFixController = new ModernFixCompatController();
        BackgroundWorldgenWarmup worldgenWarmup = new BackgroundWorldgenWarmup();
        IncompatibleModsController incompatibleModsController = new IncompatibleModsController();
        List<String> incompatibleMods = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("fastload")) {
            incompatibleMods.add("Fastload");
        }
        if (FabricLoader.getInstance().isModLoaded("fastquit")) {
            incompatibleMods.add("FastQuit");
        }
        incompatibleModsController.setIncompatibleMods(incompatibleMods);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            incompatibleModsController.tick(minecraft);
            modernFixController.tick(minecraft);
            worldgenWarmup.tick(minecraft);
        });
    }
}
