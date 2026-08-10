package net.fly.adrenaline.client;

import com.mojang.datafixers.util.Either;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.fly.adrenaline.util.WorldgenDifferenceState;
import net.fly.adrenaline.util.WorldgenBenchmarkHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class WorldgenBenchmark {

    private static final String LEVEL_PREFIX = "adrenaline_debug_";
    private static final int BENCHMARK_DIAMETER = 11;
    private static final int BENCHMARK_CHUNKS = BENCHMARK_DIAMETER * BENCHMARK_DIAMETER;
    private static final ThreadLocal<Boolean> BENCHMARK_CALL = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger COMPLETED_CHUNKS = new AtomicInteger();

    private static volatile Status status = new Status(Phase.IDLE, 0, 0, null, null);
    private static volatile boolean running;
    private static volatile boolean cancelled;
    private static Minecraft minecraft;
    private static AdrenalineConfig.Data optimizedConfig;
    private static int spawnZoneRadius;
    private static long seed;
    private static long passStartedNanos;
    private static String levelId;
    private static IntegratedServer server;
    private static Pass pass;
    private static PassResult vanillaResult;

    private WorldgenBenchmark() {
    }

    public static synchronized boolean start(Minecraft client, AdrenalineConfig.Data settings) {
        if (!BuildConfig.DEBUG || running || client.level != null || client.getSingleplayerServer() != null
            || BackgroundWorldgenWarmup.isRunning() || BackgroundWorldSave.isRunning()) {
            return false;
        }

        minecraft = client;
        optimizedConfig = new AdrenalineConfig.Data(settings);
        spawnZoneRadius = Math.max(AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS, Math.min(AdrenalineConfig.MAX_SPAWN_ZONE_RADIUS, settings.spawnZoneRadius));
        seed = WorldOptions.randomSeed();
        vanillaResult = null;
        cancelled = false;
        running = true;
        WorldgenBenchmarkHooks.setPreparedHandler(WorldgenBenchmark::onServerPrepared);
        startPass(Pass.VANILLA);
        return true;
    }

    public static synchronized void cancel() {
        if (!running || status.phase() == Phase.CLEANING || status.phase() == Phase.CANCELLING) {
            return;
        }
        cancelled = true;
        status = new Status(Phase.CANCELLING, COMPLETED_CHUNKS.get(), status.totalChunks(), null, null);
        IntegratedServer currentServer = server;
        if (currentServer != null) {
            finishPass(null, new CancellationException());
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static Status status() {
        Status current = status;
        if (current.phase() == Phase.WARMING || current.phase() == Phase.GENERATING
            || current.phase() == Phase.COMPARING_NOISE || current.phase() == Phase.COMPARING_FULL) {
            return new Status(current.phase(), COMPLETED_CHUNKS.get(), current.totalChunks(), current.report(), current.error());
        }
        return current;
    }

    public static boolean isAdrenalinePass() {
        return pass == Pass.ADRENALINE;
    }

    public static boolean isBenchmarkLevel(String requestedLevelId) {
        return BuildConfig.DEBUG && requestedLevelId.startsWith(LEVEL_PREFIX);
    }

    public static void beforeWorldLoad(String requestedLevelId) {
        if (running && requestedLevelId.equals(levelId)) {
            BENCHMARK_CALL.set(true);
        }
    }

    public static boolean isBenchmarkCall() {
        return BuildConfig.DEBUG && BENCHMARK_CALL.get();
    }

    public static void detachWorldLoad(IntegratedServer detachedServer) {
        server = detachedServer;
        BackgroundWorldgenWarmupState.attach(detachedServer);
        BENCHMARK_CALL.remove();
        if (cancelled) {
            finishPass(null, new CancellationException());
        }
    }

    public static void exitWorldLoad(String exitedLevelId) {
        if (levelId != null && levelId.equals(exitedLevelId)) {
            BENCHMARK_CALL.remove();
        }
    }

    public static void onServerPrepared(MinecraftServer candidate) {
        if (!running || candidate != server) {
            return;
        }

        try {
            PassResult result = runPass(candidate, System.nanoTime() - passStartedNanos, WorldgenBenchmarkHooks.generationNanos());
            minecraft.execute(() -> finishPass(result, null));
        } catch (Throwable throwable) {
            minecraft.execute(() -> finishPass(null, throwable));
        }
    }

    public static synchronized boolean enterDifferenceWorld() {
        Report report = status.report();
        if (!BuildConfig.DEBUG || running || report == null || report.fullDifferences().isEmpty() || minecraft == null
            || minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            return false;
        }

        WorldgenBenchmarkHooks.setPreparedHandler(candidate -> prepareDifferenceWorld(candidate, report));
        LevelSettings settings = new LevelSettings(
            "Adrenaline Differences " + report.seed(),
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(),
            WorldDataConfiguration.DEFAULT
        );
        try {
            minecraft.createWorldOpenFlows().createFreshLevel(
                "adrenaline_differences_" + UUID.randomUUID().toString().replace("-", ""),
                settings,
                new WorldOptions(report.seed(), true, false),
                WorldPresets::createNormalWorldDimensions
            );
            return true;
        } catch (Throwable throwable) {
            WorldgenBenchmarkHooks.clearPreparedHandler();
            GlobalCommon.LOGGER.warn("Failed to create worldgen difference world", throwable);
            return false;
        }
    }

    private static void prepareDifferenceWorld(MinecraftServer minecraftServer, Report report) {
        WorldgenBenchmarkHooks.clearPreparedHandler();
        ServerLevel level = minecraftServer.overworld();
        Map<Long, WorldgenDifferenceState.Difference> differences = new HashMap<>(report.fullDifferences().size());
        for (BlockDifference difference : report.fullDifferences()) {
            differences.put(BlockPos.asLong(difference.x(), difference.y(), difference.z()), new WorldgenDifferenceState.Difference(difference.vanillaStateId(), difference.adrenalineStateId()));
        }
        WorldgenDifferenceState.activate(minecraftServer, differences);
        int baseX = baseX(report.seed());
        int baseZ = baseZ(report.seed());
        generateRegion(minecraftServer, level, baseX, baseZ, BENCHMARK_DIAMETER, ChunkStatus.FULL);
        int centerX = (baseX + BENCHMARK_DIAMETER / 2) * 16 + 8;
        int centerZ = (baseZ + BENCHMARK_DIAMETER / 2) * 16 + 8;
        int centerY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) + 1;
        level.setDefaultSpawnPos(new BlockPos(centerX, centerY, centerZ), 0.0F);
        BlockState marker = Blocks.PURPLE_GLAZED_TERRACOTTA.defaultBlockState();
        for (BlockDifference difference : report.fullDifferences()) {
            BlockPos pos = new BlockPos(difference.x(), difference.y(), difference.z());
            level.setBlock(pos, marker, 2);
        }
    }

    private static void startPass(Pass nextPass) {
        WorldgenBenchmarkHooks.beginPass();
        passStartedNanos = System.nanoTime();
        pass = nextPass;
        server = null;
        COMPLETED_CHUNKS.set(0);
        status = new Status(nextPass == Pass.VANILLA ? Phase.STARTING_VANILLA : Phase.STARTING_ADRENALINE, 0, 0, null, null);
        AdrenalineConfig.setRuntimeOverride(nextPass == Pass.VANILLA ? vanillaConfig(optimizedConfig) : new AdrenalineConfig.Data(optimizedConfig));
        BackgroundWorldgenWarmupState.begin(spawnZoneRadius);
        levelId = LEVEL_PREFIX + nextPass.name().toLowerCase() + "_" + UUID.randomUUID().toString().replace("-", "");

        LevelSettings settings = new LevelSettings(
            "Adrenaline Worldgen Debug",
            GameType.SPECTATOR,
            false,
            Difficulty.PEACEFUL,
            false,
            new GameRules(),
            WorldDataConfiguration.DEFAULT
        );
        minecraft.createWorldOpenFlows().createFreshLevel(
            levelId,
            settings,
            new WorldOptions(seed, true, false),
            WorldPresets::createNormalWorldDimensions
        );
        if (running && server == null) {
            finishPass(null, new IllegalStateException("Temporary server did not start"));
        }
    }

    private static PassResult runPass(MinecraftServer minecraftServer, long totalNanos, long generationNanos) {
        if (cancelled) {
            throw new CancellationException();
        }
        ServerLevel level = minecraftServer.overworld();
        int baseX = baseX(seed);
        int baseZ = baseZ(seed);

        COMPLETED_CHUNKS.set(0);
        status = new Status(Phase.COMPARING_NOISE, 0, BENCHMARK_CHUNKS, null, null);
        BlockSnapshot noiseSnapshot = capture(generateRegion(minecraftServer, level, baseX, baseZ, BENCHMARK_DIAMETER, ChunkStatus.NOISE));
        COMPLETED_CHUNKS.set(0);
        status = new Status(Phase.COMPARING_FULL, 0, BENCHMARK_CHUNKS, null, null);
        BlockSnapshot fullSnapshot = capture(generateRegion(minecraftServer, level, baseX, baseZ, BENCHMARK_DIAMETER, ChunkStatus.FULL));
        return new PassResult(totalNanos, generationNanos, noiseSnapshot, fullSnapshot);
    }

    private static int baseX(long worldSeed) {
        return 768 + Math.floorMod((int) (worldSeed >>> 32), 128);
    }

    private static int baseZ(long worldSeed) {
        return 768 + Math.floorMod((int) worldSeed, 128);
    }

    private static List<ChunkAccess> generateRegion(MinecraftServer minecraftServer, ServerLevel level, int baseX, int baseZ, int diameter, ChunkStatus chunkStatus) {
        ServerChunkCache chunkSource = level.getChunkSource();
        List<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> futures = new ArrayList<>(diameter * diameter);
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = chunkSource.getChunkFuture(baseX + x, baseZ + z, chunkStatus, true);
                future.whenComplete((value, throwable) -> COMPLETED_CHUNKS.incrementAndGet());
                futures.add(future);
            }
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        minecraftServer.managedBlock(() -> all.isDone() || cancelled || !minecraftServer.isRunning());
        if (cancelled || !minecraftServer.isRunning()) {
            throw new CancellationException();
        }
        all.join();
        List<ChunkAccess> chunks = new ArrayList<>(futures.size());
        for (CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future : futures) {
            chunks.add(future.join().left().orElseThrow(() -> new IllegalStateException("Chunk generation failed")));
        }
        return chunks;
    }

    private static BlockSnapshot capture(List<ChunkAccess> chunks) {
        int sectionsPerChunk = chunks.get(0).getSections().length;
        int statesPerChunk = sectionsPerChunk * 4096;
        int[] states = new int[statesPerChunk * chunks.size()];
        int[] chunkXs = new int[chunks.size()];
        int[] chunkZs = new int[chunks.size()];
        int index = 0;
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            ChunkAccess chunk = chunks.get(chunkIndex);
            chunkXs[chunkIndex] = chunk.getPos().x;
            chunkZs[chunkIndex] = chunk.getPos().z;
            for (LevelChunkSection section : chunk.getSections()) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            states[index++] = Block.getId(section.getBlockState(x, y, z));
                        }
                    }
                }
            }
        }
        return new BlockSnapshot(states, statesPerChunk, sectionsPerChunk, chunks.get(0).getMinSection(), chunkXs, chunkZs);
    }

    private static synchronized void finishPass(PassResult result, Throwable throwable) {
        if (!running || status.phase() == Phase.CLEANING) {
            return;
        }
        status = new Status(Phase.CLEANING, COMPLETED_CHUNKS.get(), status.totalChunks(), null, null);
        IntegratedServer completedServer = server;
        String completedLevelId = levelId;
        if (completedServer != null) {
            completedServer.halt(false);
        }

        Thread cleanup = new Thread(() -> {
            if (completedServer != null) {
                try {
                    completedServer.getRunningThread().join();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            BackgroundWorldgenWarmupState.end();
            WorldDeletion.delete(minecraft, null, completedLevelId);
            minecraft.execute(() -> afterCleanup(result, throwable));
        }, "Adrenaline benchmark cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private static synchronized void afterCleanup(PassResult result, Throwable throwable) {
        server = null;
        if (cancelled || throwable instanceof CancellationException) {
            AdrenalineConfig.clearRuntimeOverride();
            WorldgenBenchmarkHooks.clearPreparedHandler();
            running = false;
            status = new Status(Phase.CANCELLED, 0, 0, null, null);
            return;
        }
        if (throwable != null || result == null) {
            AdrenalineConfig.clearRuntimeOverride();
            WorldgenBenchmarkHooks.clearPreparedHandler();
            running = false;
            String message = throwable == null ? "Unknown benchmark failure" : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            status = new Status(Phase.FAILED, 0, 0, null, message);
            return;
        }
        if (pass == Pass.VANILLA) {
            vanillaResult = result;
            startPass(Pass.ADRENALINE);
            return;
        }

        Report report = compare(vanillaResult, result);
        writeDifferenceReport(report);
        vanillaResult = null;
        AdrenalineConfig.clearRuntimeOverride();
        WorldgenBenchmarkHooks.clearPreparedHandler();
        running = false;
        status = new Status(Phase.COMPLETE, BENCHMARK_CHUNKS, BENCHMARK_CHUNKS, report, null);
    }

    private static Report compare(PassResult vanilla, PassResult adrenaline) {
        MatchResult noise = compare(vanilla.noiseSnapshot(), adrenaline.noiseSnapshot());
        MatchResult full = compare(vanilla.fullSnapshot(), adrenaline.fullSnapshot());
        double totalSpeedPercent = adrenaline.totalNanos() == 0L ? 0.0D : (double) vanilla.totalNanos() * 100.0D / (double) adrenaline.totalNanos();
        double generationSpeedPercent = adrenaline.generationNanos() == 0L ? 0.0D : (double) vanilla.generationNanos() * 100.0D / (double) adrenaline.generationNanos();
        return new Report(
            seed,
            spawnZoneRadius,
            BENCHMARK_CHUNKS,
            noise.totalBlocks(),
            noise.matchingBlocks(),
            noise.exactChunks(),
            full.matchingBlocks(),
            full.exactChunks(),
            vanilla.totalNanos(),
            vanilla.generationNanos(),
            adrenaline.totalNanos(),
            adrenaline.generationNanos(),
            totalSpeedPercent,
            generationSpeedPercent,
            noise.differences(),
            full.differences(),
            "logs/adrenaline-worldgen-differences-" + seed + ".txt"
        );
    }

    private static void writeDifferenceReport(Report report) {
        Path path = minecraft.gameDirectory.toPath().resolve(report.differenceReport());
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("Seed: " + report.seed());
                writer.newLine();
                writer.write("Spawn zone radius: " + report.spawnZoneRadius());
                writer.newLine();
                writer.write("NOISE differences: " + report.noiseDifferences().size());
                writer.newLine();
                writer.write("FULL differences: " + report.fullDifferences().size());
                writer.newLine();
                writer.newLine();
                writeDifferences(writer, "NOISE", report.noiseDifferences());
                writer.newLine();
                writeDifferences(writer, "FULL", report.fullDifferences());
            }
            GlobalCommon.LOGGER.info("Worldgen benchmark differences written to {}", path);
        } catch (IOException exception) {
            GlobalCommon.LOGGER.warn("Failed to write worldgen benchmark differences to {}", path, exception);
        }
    }

    private static void writeDifferences(BufferedWriter writer, String stage, List<BlockDifference> differences) throws IOException {
        writer.write("[" + stage + "]");
        writer.newLine();
        for (BlockDifference difference : differences) {
            writer.write(difference.x() + "," + difference.y() + "," + difference.z() + ": "
                + stateName(difference.vanillaStateId()) + " -> " + stateName(difference.adrenalineStateId()));
            writer.newLine();
        }
    }

    public static String stateName(int stateId) {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(stateId);
        if (state == null) {
            return Integer.toString(stateId);
        }
        String name = state.getBlock().getName().getString();
        if (state.getValues().isEmpty()) {
            return name;
        }
        String properties = state.getValues().entrySet().stream()
            .map(WorldgenBenchmark::propertyName)
            .collect(Collectors.joining(","));
        return name + "[" + properties + "]";
    }

    private static String propertyName(Map.Entry<Property<?>, Comparable<?>> entry) {
        return entry.getKey().getName() + "=" + entry.getValue();
    }

    private static MatchResult compare(BlockSnapshot vanilla, BlockSnapshot adrenaline) {
        int[] expected = vanilla.states();
        int[] actual = adrenaline.states();
        int totalBlocks = Math.max(expected.length, actual.length);
        int matchingBlocks = 0;
        int sharedLength = Math.min(expected.length, actual.length);
        List<BlockDifference> differences = new ArrayList<>();
        for (int i = 0; i < sharedLength; i++) {
            if (expected[i] == actual[i]) {
                matchingBlocks++;
            } else {
                differences.add(vanilla.difference(i, expected[i], actual[i]));
            }
        }

        int statesPerChunk = Math.max(vanilla.statesPerChunk(), adrenaline.statesPerChunk());
        int exactChunks = 0;
        for (int chunk = 0; chunk < BENCHMARK_CHUNKS; chunk++) {
            int start = chunk * statesPerChunk;
            int end = Math.min(start + statesPerChunk, totalBlocks);
            boolean exact = end - start == statesPerChunk && end <= expected.length && end <= actual.length;
            for (int i = start; exact && i < end; i++) {
                exact = expected[i] == actual[i];
            }
            if (exact) {
                exactChunks++;
            }
        }

        return new MatchResult(totalBlocks, matchingBlocks, exactChunks, List.copyOf(differences));
    }

    private static AdrenalineConfig.Data vanillaConfig(AdrenalineConfig.Data source) {
        AdrenalineConfig.Data vanilla = new AdrenalineConfig.Data(source);
        vanilla.worldgenOptimizations = false;
        vanilla.parallelWorldgen = false;
        vanilla.fastLegacyRandom = false;
        vanilla.saveChunksAfterWorldCreation = false;
        return vanilla;
    }

    public enum Phase {
        IDLE,
        STARTING_VANILLA,
        STARTING_ADRENALINE,
        WARMING,
        GENERATING,
        COMPARING_NOISE,
        COMPARING_FULL,
        CLEANING,
        CANCELLING,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    private enum Pass {
        VANILLA,
        ADRENALINE
    }

    public record Status(Phase phase, int completedChunks, int totalChunks, Report report, String error) {
    }

    public record Report(long seed, int spawnZoneRadius, int chunks, int totalBlocks, int noiseMatchingBlocks, int noiseExactChunks, int fullMatchingBlocks, int fullExactChunks, long vanillaTotalNanos, long vanillaGenerationNanos, long adrenalineTotalNanos, long adrenalineGenerationNanos, double totalSpeedPercent, double generationSpeedPercent, List<BlockDifference> noiseDifferences, List<BlockDifference> fullDifferences, String differenceReport) {
    }

    public record BlockDifference(int x, int y, int z, int vanillaStateId, int adrenalineStateId) {
    }

    private record BlockSnapshot(int[] states, int statesPerChunk, int sectionsPerChunk, int minSection, int[] chunkXs, int[] chunkZs) {

        private BlockDifference difference(int index, int vanillaStateId, int adrenalineStateId) {
            int chunkIndex = index / this.statesPerChunk;
            int withinChunk = index % this.statesPerChunk;
            int sectionIndex = withinChunk / 4096;
            int withinSection = withinChunk % 4096;
            int localY = withinSection >> 8;
            int localZ = withinSection >> 4 & 15;
            int localX = withinSection & 15;
            return new BlockDifference(
                this.chunkXs[chunkIndex] * 16 + localX,
                (this.minSection + sectionIndex) * 16 + localY,
                this.chunkZs[chunkIndex] * 16 + localZ,
                vanillaStateId,
                adrenalineStateId
            );
        }
    }

    private record MatchResult(int totalBlocks, int matchingBlocks, int exactChunks, List<BlockDifference> differences) {
    }

    private record PassResult(long totalNanos, long generationNanos, BlockSnapshot noiseSnapshot, BlockSnapshot fullSnapshot) {
    }
}
