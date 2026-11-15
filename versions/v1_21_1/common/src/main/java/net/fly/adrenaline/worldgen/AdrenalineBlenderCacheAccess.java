package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.blending.Blender;

public interface AdrenalineBlenderCacheAccess {
    Blender adrenaline$getCachedBlender();

    void adrenaline$cacheBlender(Blender blender);
}
