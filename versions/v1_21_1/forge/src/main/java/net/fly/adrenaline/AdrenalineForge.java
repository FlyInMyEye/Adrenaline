package net.fly.adrenaline;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("adrenaline")
public final class AdrenalineForge {
    public static final String MODID = "adrenaline";

    public AdrenalineForge() {
        AdrenalinePlatform.initialize(FMLPaths.CONFIGDIR.get(), modId -> ModList.get().isLoaded(modId), modId -> ModList.get().getMods().stream()
            .filter(info -> info.getModId().equals(modId))
            .map(info -> new AdrenalinePlatform.ModInfo(modId, info.getDisplayName(), info.getOwningFile().getFile().getFileName()))
            .findFirst().orElse(null));
        AdrenalinePlatform.setCreateWorldSpawnHook(ForgeEventFactory::onCreateWorldSpawn);
        AdrenalineCommon.init(AdrenalinePlatform.configDirectory());
    }
}
