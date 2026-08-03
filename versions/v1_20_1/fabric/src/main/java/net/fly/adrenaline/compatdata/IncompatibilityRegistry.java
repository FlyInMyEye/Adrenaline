package net.fly.adrenaline.compatdata;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fly.adrenaline.mixin.MixinMinecraftServer;

public final class IncompatibilityRegistry {
    private static final List<IncompatibleData> INCOMPATIBILITIES = List.of(
        new IncompatibleData("modernfix", new String[0]),
        new IncompatibleData("fastload", new String[]{MixinMinecraftServer.class.getName()})
    );

    private IncompatibilityRegistry() {
    }

    public static List<IncompatibleData> all() {
        return INCOMPATIBILITIES;
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
