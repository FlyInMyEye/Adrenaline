package net.fly.adrenaline.compatdata;

import java.util.List;
import net.fly.adrenaline.client.FastloadCompatController;
import net.fly.adrenaline.client.ModernFixCompatController;
import net.fly.adrenaline.mixin.MixinMinecraftServer;

public final class IncompatibilityRegistry {

    private static final List<IncompatibleData> INCOMPATIBILITIES = List.of(
        new IncompatibleData("modernfix", new String[0], new ModernFixCompatController()),
        new IncompatibleData("fastload", new String[]{MixinMinecraftServer.class.getName()}, new FastloadCompatController())
    );

    private IncompatibilityRegistry() {
    }

    public static List<IncompatibleData> all() {
        return INCOMPATIBILITIES;
    }
}
