package net.fly.adrenaline.util;

import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

public final class WorldgenPreparation {

    private static long epoch;
    private static Prepared prepared;
    private static ChunkGenerator preparingGenerator;
    private static long preparingSeed;

    private WorldgenPreparation() {
    }

    public static synchronized void prepare(ChunkGenerator generator, RegistryAccess registryAccess, long seed) {
        if ((prepared != null && prepared.matches(generator, seed)) || (preparingGenerator == generator && preparingSeed == seed)) {
            return;
        }

        long preparationEpoch = ++epoch;
        prepared = null;
        preparingGenerator = generator;
        preparingSeed = seed;
        CompletableFuture.runAsync(() -> {
            try {
                NoiseGeneratorSettings settings = generator instanceof NoiseBasedChunkGenerator noiseGenerator
                    ? noiseGenerator.generatorSettings().value()
                    : NoiseGeneratorSettings.dummy();
                RandomState randomState = RandomState.create(settings, registryAccess.lookupOrThrow(Registries.NOISE), seed);
                ChunkGeneratorStructureState structureState = generator.createState(registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
                structureState.ensureStructuresGenerated();
                synchronized (WorldgenPreparation.class) {
                    if (epoch == preparationEpoch) {
                        prepared = new Prepared(generator, seed, structureState);
                    }
                }
            } finally {
                synchronized (WorldgenPreparation.class) {
                    if (epoch == preparationEpoch) {
                        preparingGenerator = null;
                    }
                }
            }
        }, Util.backgroundExecutor());
    }

    public static synchronized ChunkGeneratorStructureState take(ChunkGenerator generator, long seed) {
        Prepared current = prepared;
        if (current != null && current.matches(generator, seed)) {
            prepared = null;
            return current.structureState;
        }
        if (current != null && current.generator == generator) {
            prepared = null;
        }
        if (preparingGenerator == generator) {
            epoch++;
            preparingGenerator = null;
        }
        return null;
    }

    public static synchronized void clear() {
        epoch++;
        prepared = null;
        preparingGenerator = null;
    }

    private record Prepared(ChunkGenerator generator, long seed, ChunkGeneratorStructureState structureState) {

        private boolean matches(ChunkGenerator candidateGenerator, long candidateSeed) {
            return this.generator == candidateGenerator && this.seed == candidateSeed;
        }
    }
}
