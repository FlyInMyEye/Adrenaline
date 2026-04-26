package net.fly.adrenaline.compatdata;

import java.util.List;
import net.fly.adrenaline.client.ModernFixCompatController;

public final class IncompatibilityRegistry {

    private static final List<IncompatibleData> INCOMPATIBILITIES = List.of(
        new IncompatibleData("modernfix", new String[0], new ModernFixCompatController())
    );

    private IncompatibilityRegistry() {
    }

    public static List<IncompatibleData> all() {
        return INCOMPATIBILITIES;
    }
}
