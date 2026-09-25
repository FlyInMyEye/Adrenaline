package net.fly.adrenaline.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class DebugChunkGeneration implements AutoCloseable {

    private static final ThreadLocal<DebugChunkGeneration> ACTIVE = new ThreadLocal<>();
    private final ServerLevel level;
    private final AdrenalineConfig.Data previous;
    private final Map<Long, ProtoChunk> chunks = new HashMap<>();
    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState generatorState;

    private DebugChunkGeneration(ServerLevel level, ChunkGenerator source, AdrenalineConfig.Data settings) {
        this.level = level;
        this.previous = AdrenalineConfig.setThreadOverride(settings);
        try {
            RegistryAccess registries = level.registryAccess();
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            Tag encoded = ChunkGenerator.CODEC.encodeStart(ops, source).getOrThrow(false, message -> {});
            this.generator = (NoiseBasedChunkGenerator) ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow(false, message -> {});
            this.randomState = RandomState.create(this.generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), level.getSeed());
            this.generatorState = this.generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, level.getSeed());
            ACTIVE.set(this);
        } catch (RuntimeException | Error error) {
            AdrenalineConfig.setThreadOverride(this.previous);
            throw error;
        }
    }

    public static Result generate(ServerLevel level, ChunkPos pos, boolean optimized) {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Chunk generation command is already active");
        }
        ChunkGenerator source = level.getChunkSource().getGenerator();
        if (!(source instanceof NoiseBasedChunkGenerator)) {
            throw new IllegalArgumentException("Chunk commands require a noise-based generator");
        }
        AdrenalineConfig.Data settings = AdrenalineConfig.copy();
        settings.worldgenOptimizations = optimized;
        settings.parallelWorldgen = false;
        settings.saveChunksAfterWorldCreation = false;
        if (!optimized) {
            settings.worldgenOptimizations = false;
            settings.fastLegacyRandom = false;
        }
        try (DebugChunkGeneration generation = new DebugChunkGeneration(level, source, settings)) {
            ProtoChunk chunk = generation.generate(pos.x, pos.z, ChunkStatus.NOISE);
            int[] noise = new int[level.getHeight() * 256];
            BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
            int next = 0;
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        noise[next++] = Block.getId(chunk.getBlockState(block));
                    }
                }
            }
            int radius = AdrenalineConfig.resolvedFeatureSafetyRadius();
            for (int z = pos.z - radius; z <= pos.z + radius; z++) {
                for (int x = pos.x - radius; x <= pos.x + radius; x++) {
                    generation.generate(x, z, ChunkStatus.FEATURES);
                }
            }
            generation.generator.spawnOriginalMobs(new WorldGenRegion(level, List.of(chunk), ChunkStatus.SPAWN, -1));
            return new Result(chunk, noise);
        }
    }

    private ProtoChunk generate(int x, int z, ChunkStatus target) {
        if (!this.level.getServer().isRunning()) {
            throw new IllegalStateException("Server is stopping");
        }
        long key = ChunkPos.asLong(x, z);
        ProtoChunk chunk = this.chunks.computeIfAbsent(key, ignored -> new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY,
            this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), null));
        if (this.chunks.size() > 4096) {
            throw new IllegalStateException("Chunk generation dependency area exceeds the debug limit");
        }
        if (chunk.getStatus().isOrAfter(target)) {
            return chunk;
        }
        if (target == ChunkStatus.STRUCTURE_STARTS) {
            if (this.level.getServer().getWorldData().worldGenOptions().generateStructures()) {
                this.generator.createStructures(this.level.registryAccess(), this.generatorState, this.level.structureManager(), chunk,
                    this.level.getStructureManager());
            }
            chunk.setStatus(target);
            return chunk;
        }
        this.generate(x, z, target.getParent());
        int radius = Math.max(0, target.getRange());
        List<ChunkAccess> region = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                ChunkStatus required = distance == 0 ? target.getParent()
                    : ChunkStatus.getStatusAroundFullChunk(ChunkStatus.getDistance(target) + distance);
                region.add(this.generate(x + dx, z + dz, required));
            }
        }
        target.generate(Runnable::run, this.level, this.generator, this.level.getStructureManager(), this.level.getChunkSource().getLightEngine(),
            ignored -> { throw new IllegalStateException("Unexpected live chunk conversion"); }, region).join()
            .left().orElseThrow(() -> new IllegalStateException("Debug chunk generation failed"));
        return chunk;
    }

    public static DebugChunkGeneration current(ServerLevel level) {
        DebugChunkGeneration current = ACTIVE.get();
        return current != null && current.level == level ? current : null;
    }

    public RandomState randomState() {
        return this.randomState;
    }

    public ChunkGenerator generator() {
        return this.generator;
    }

    public ChunkGeneratorStructureState generatorState() {
        return this.generatorState;
    }

    @Override
    public void close() {
        ACTIVE.remove();
        AdrenalineConfig.setThreadOverride(this.previous);
    }

    public record Result(ProtoChunk chunk, int[] noise) {
    }
}
