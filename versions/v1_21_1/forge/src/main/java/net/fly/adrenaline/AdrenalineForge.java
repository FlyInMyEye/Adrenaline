package net.fly.adrenaline;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("adrenaline")
public final class AdrenalineForge {
    public static final String MODID = "adrenaline";

    public AdrenalineForge() {
        AdrenalineCommon.init(FMLPaths.CONFIGDIR.get());
    }
}
