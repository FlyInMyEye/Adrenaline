package net.fly.adrenaline.mixin;

import java.util.concurrent.ConcurrentHashMap;
import net.fly.adrenaline.worldgen.AdrenalineBlenderCacheAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegion implements AdrenalineBlenderCacheAccess {

    @Unique
    private final ConcurrentHashMap<Thread, Blender> adrenaline$cachedBlenders = new ConcurrentHashMap<>();

    @Override
    public Blender adrenaline$getCachedBlender() {
        return this.adrenaline$cachedBlenders.get(Thread.currentThread());
    }

    @Override
    public void adrenaline$cacheBlender(Blender blender) {
        if (blender == null) {
            this.adrenaline$cachedBlenders.remove(Thread.currentThread());
        } else {
            this.adrenaline$cachedBlenders.put(Thread.currentThread(), blender);
        }
    }

}
