package net.fly.adrenaline;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(AdrenalineNeoForge.MODID)
public final class AdrenalineNeoForge {
    public static final String MODID = "adrenaline";

    public AdrenalineNeoForge() {
        AdrenalineCommon.init(FMLPaths.CONFIGDIR.get());
    }
}
