package net.fly.adrenaline.compatdata;

import java.util.List;
import net.minecraftforge.fml.loading.LoadingModList;

public final class IncompatibilityRegistry {

    private static final List<IncompatibleData> INCOMPATIBILITIES = List.of(
        new IncompatibleData("modernfix", new String[0])
    );

    private IncompatibilityRegistry() {
    }

    public static List<IncompatibleData> all() {
        return INCOMPATIBILITIES;
    }

    public static boolean isLoaded(String modId) {
        return LoadingModList.get() != null && LoadingModList.get().getModFileById(modId) != null;
    }
}
