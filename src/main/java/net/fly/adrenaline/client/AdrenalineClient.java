package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;

public final class AdrenalineClient {

    private AdrenalineClient() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new AdrenalineConfigScreen(screen))
        );

        MinecraftForge.EVENT_BUS.register(new ModernFixCompatController());
        MinecraftForge.EVENT_BUS.register(new BackgroundWorldgenWarmup());
        if (BuildConfig.DEBUG) {
            MinecraftForge.EVENT_BUS.register(new BuildWarningOverlay());
            MinecraftForge.EVENT_BUS.register(new WorldgenStatsOverlay());
        }

        FastloadCompatController fastloadCompatController = new FastloadCompatController();
        fastloadCompatController.setShouldShow(IncompatibilityRegistry.isLoaded("fastload"));
        MinecraftForge.EVENT_BUS.register(fastloadCompatController);
    }
}
