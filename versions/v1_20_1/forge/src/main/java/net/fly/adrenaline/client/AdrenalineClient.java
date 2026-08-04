package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;

public final class AdrenalineClient {

    private static final ModernFixCompatController MODERN_FIX_CONTROLLER = new ModernFixCompatController();
    private static final BackgroundWorldgenWarmup WORLDGEN_WARMUP = new BackgroundWorldgenWarmup();
    private static final IncompatibleModsController INCOMPATIBLE_MODS_CONTROLLER = new IncompatibleModsController();

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

        List<String> incompatibleMods = new ArrayList<>();
        if (IncompatibilityRegistry.isLoaded("fastload")) {
            incompatibleMods.add("Fastload");
        }
        if (IncompatibilityRegistry.isLoaded("fastquit")) {
            incompatibleMods.add("FastQuit");
        }
        INCOMPATIBLE_MODS_CONTROLLER.setIncompatibleMods(incompatibleMods);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        INCOMPATIBLE_MODS_CONTROLLER.tick(minecraft);
        MODERN_FIX_CONTROLLER.tick(minecraft);
        WORLDGEN_WARMUP.tick(minecraft);
    }
}
