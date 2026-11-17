package net.fly.adrenaline;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.fml.loading.FMLPaths;

@Mod(AdrenalineNeoForge.MODID)
public final class AdrenalineNeoForge {
    public static final String MODID = "adrenaline";

    public AdrenalineNeoForge() {
        AdrenalinePlatform.initialize(FMLPaths.CONFIGDIR.get(), modId -> ModList.get().isLoaded(modId), modId -> ModList.get().getMods().stream()
            .filter(info -> info.getModId().equals(modId))
            .map(info -> new AdrenalinePlatform.ModInfo(modId, info.getDisplayName(), info.getOwningFile().getFile().getFileName()))
            .findFirst().orElse(null));
        AdrenalinePlatform.setCreateWorldSpawnHook(EventHooks::onCreateWorldSpawn);
        AdrenalineCommon.init(AdrenalinePlatform.configDirectory());
    }
}
