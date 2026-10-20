package net.fly.adrenaline.worldgen;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class NativeSurfacePrograms {

    private final ConcurrentHashMap<Thread, Object> workers = new ConcurrentHashMap<>();
    private volatile Entry entry;

    public Object worker(Supplier<Object> factory) {
        return this.workers.computeIfAbsent(Thread.currentThread(), thread -> factory.get());
    }

    public NativeSurfaceProgram get(SurfaceRules.RuleSource source, Object original, Registry<Biome> registry, WorldGenerationContext generation) {
        return this.get(source, original, registry, generation, null);
    }

    public NativeSurfaceProgram get(SurfaceRules.RuleSource source, Object original, Registry<Biome> registry, WorldGenerationContext generation, Object context) {
        Entry current = this.entry;
        if (current != null && current.matches(source, registry, generation)) {
            return current.program;
        }
        return this.compile(source, original, registry, generation, context);
    }

    private synchronized NativeSurfaceProgram compile(SurfaceRules.RuleSource source, Object original, Registry<Biome> registry, WorldGenerationContext generation, Object context) {
        Entry current = this.entry;
        if (current == null || !current.matches(source, registry, generation)) {
            current = new Entry(source, registry, generation.getMinGenY(), generation.getGenDepth(), AdrenalineConfig.nativeSurfaceNoiseEnabled(),
                NativeSurfaceProgram.compile(source, original, registry, generation, context));
            this.entry = current;
        }
        return current.program;
    }

    private record Entry(SurfaceRules.RuleSource source, Registry<Biome> registry, int minY, int height, boolean nativeNoise, NativeSurfaceProgram program) {
        private boolean matches(SurfaceRules.RuleSource source, Registry<Biome> registry, WorldGenerationContext generation) {
            return this.source == source && this.registry == registry && this.minY == generation.getMinGenY() && this.height == generation.getGenDepth() && this.nativeNoise == AdrenalineConfig.nativeSurfaceNoiseEnabled();
        }
    }
}
