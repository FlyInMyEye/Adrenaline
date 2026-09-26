package net.fly.adrenaline.worldgen;

import java.util.IdentityHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class DensityMapCache implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final IdentityHashMap<DensityFunction, DensityFunction> mapped = new IdentityHashMap<>();

    public DensityMapCache(DensityFunction.Visitor delegate) {
        this.delegate = delegate;
    }

    public DensityFunction get(DensityFunction original) {
        return this.mapped.get(original);
    }

    public void put(DensityFunction original, DensityFunction result) {
        this.mapped.put(original, result);
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        return this.delegate.apply(function);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return this.delegate.visitNoise(noise);
    }
}
