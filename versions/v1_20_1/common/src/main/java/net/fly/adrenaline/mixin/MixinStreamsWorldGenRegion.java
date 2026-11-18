package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.StreamsClimateCache;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldGenRegion.class)
public abstract class MixinStreamsWorldGenRegion {

    @WrapMethod(method = "getUncachedNoiseBiome")
    private Holder<Biome> adrenaline$cacheClimateColumns(int x, int y, int z, Operation<Holder<Biome>> original) {
        if (!AdrenalineConfig.biomeClimateOptimizationsEnabled()) {
            return original.call(x, y, z);
        }
        StreamsClimateCache cache = StreamsClimateCache.forQuery();
        boolean previous = cache.setActive(true);
        try {
            return original.call(x, y, z);
        } finally {
            cache.setActive(previous);
        }
    }
}
