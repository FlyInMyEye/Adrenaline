package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.mixin.MixinHeightmapAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FastHeightmap {

    private FastHeightmap() {
    }

    public static void setRawHeight(Heightmap heightmap, int x, int z, int height) {
        ((MixinHeightmapAccessor) heightmap).adrenaline$setHeight(x, z, height);
    }
}
