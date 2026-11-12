package net.fly.adrenaline;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class AdrenalineFabric implements ModInitializer {
    public static final String MODID = "adrenaline";

    @Override
    public void onInitialize() {
        AdrenalineCommon.init(FabricLoader.getInstance().getConfigDir());
    }
}
