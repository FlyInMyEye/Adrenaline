package net.fly.adrenaline;

import net.fabricmc.api.ModInitializer;

public final class AdrenalineFabric implements ModInitializer {
    public static final String MODID = "adrenaline";

    @Override
    public void onInitialize() {
        AdrenalineCommon.init();
        System.out.println("Adrenaline initialized on Fabric");
    }
}
