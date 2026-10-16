package net.fly.adrenaline.worldgen;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class SurfaceBiomePredicate implements Predicate<ResourceKey<Biome>> {

    private final Predicate<ResourceKey<Biome>> original;
    private ResourceKey<Biome> lastKey;
    private boolean lastResult;

    public SurfaceBiomePredicate(Predicate<ResourceKey<Biome>> original) {
        this.original = original;
    }

    @Override
    public boolean test(ResourceKey<Biome> key) {
        if (key != null && key == this.lastKey) {
            return this.lastResult;
        }
        boolean result = this.original.test(key);
        this.lastKey = key;
        this.lastResult = result;
        return result;
    }
}
