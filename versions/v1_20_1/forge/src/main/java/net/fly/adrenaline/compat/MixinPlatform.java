package net.fly.adrenaline.compat;

import java.nio.file.Files;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

public final class MixinPlatform {
    public static final String DEBUG_MIXIN = "net.fly.adrenaline.mixin.MixinGui";

    private MixinPlatform() {
    }

    public static boolean isModLoaded(String modId) {
        LoadingModList modList = LoadingModList.get();
        return modList != null && modList.getModFileById(modId) != null;
    }

    public static String mixinOwnerName(String mixinClassName) {
        LoadingModList modList = LoadingModList.get();
        if (modList == null) {
            return mixinClassName;
        }
        String[] resource = (mixinClassName.replace('.', '/') + ".class").split("/");
        for (ModFileInfo file : modList.getModFiles()) {
            if (Files.isRegularFile(file.getFile().findResource(resource)) && !file.getMods().isEmpty()) {
                return file.getMods().get(0).getDisplayName();
            }
        }
        return mixinClassName;
    }
}
