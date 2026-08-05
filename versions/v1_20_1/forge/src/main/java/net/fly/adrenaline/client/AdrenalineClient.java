package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;

public final class AdrenalineClient {

    private static final ModernFixCompatController MODERN_FIX_CONTROLLER = new ModernFixCompatController();
    private static final BackgroundWorldgenWarmup WORLDGEN_WARMUP = new BackgroundWorldgenWarmup();

    private AdrenalineClient() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new AdrenalineConfigScreen(screen))
        );

        MinecraftForge.EVENT_BUS.addListener(AdrenalineClient::onClientTick);
        if (BuildConfig.DEBUG) {
            MinecraftForge.EVENT_BUS.register(new BuildWarningOverlay());
            MinecraftForge.EVENT_BUS.register(new WorldgenStatsOverlay());
        }

    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MODERN_FIX_CONTROLLER.tick(minecraft);
        WORLDGEN_WARMUP.tick(minecraft);
    }
}
