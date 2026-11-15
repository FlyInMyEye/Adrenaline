package net.fly.adrenaline.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class MixinPlatform {
    public static final String DEBUG_MIXIN = "net.fly.adrenaline.mixin.MixinDebugScreenOverlay";

    private MixinPlatform() {
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String mixinOwnerName(String mixinClassName) {
        String resource = mixinClassName.replace('.', '/') + ".class";
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.findPath(resource).isPresent()) {
                return container.getMetadata().getName();
            }
        }
        return mixinClassName;
    }
}
