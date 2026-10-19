package net.fly.adrenaline.config;

import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import java.nio.file.Path;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.fly.adrenaline.scheduler.AdaptiveWorldgenWorkers;
import net.fly.configlib.JsonConfigManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.LoggerFactory;

public class AdrenalineConfig {

    public static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    public static final int MIN_SPAWN_ZONE_RADIUS = 1;
    public static final int MAX_SPAWN_ZONE_RADIUS = 30;
    public static final int DEFAULT_FEATURE_SAFETY_RADIUS = 1;
    public static final int MIN_FEATURE_SAFETY_RADIUS = 1;
    public static final int MAX_FEATURE_SAFETY_RADIUS = 8;

    private static final Data DEFAULTS = new Data();
    private static JsonConfigManager<Data> manager;
    private static volatile Data data = DEFAULTS;
    private static volatile Data runtimeOverride;
    private static final ThreadLocal<Data> THREAD_OVERRIDE = new ThreadLocal<>();

    public static synchronized void init(Path configDirectory) {
        manager = new JsonConfigManager<>(
            configDirectory.resolve("adrenaline.json"),
            Data.class,
            Data::new,
            LoggerFactory.getLogger("adrenaline")
        );
        manager.init();
        Data loaded = manager.get();
        boolean changed = false;
        if (loaded == null) {
            loaded = new Data();
            changed = true;
        }
        if (loaded.stagePriority == null) {
            loaded.stagePriority = loaded.prioritizeHigherStages ? StagePriority.HIGHEST : StagePriority.FIFO;
            changed = true;
        }
        if (loaded.fastTerrainLoadingMode == null) {
            loaded.fastTerrainLoadingMode = loaded.fastTerrainLoading ? FastTerrainLoadingMode.ON : FastTerrainLoadingMode.OFF;
            changed = true;
        }
        if (normalizeSettings(loaded)) {
            changed = true;
        }
        data = loaded;
        if (changed) {
            manager.save(loaded);
        }
    }

    public static Data get() {
        if (!BuildConfig.DEBUG) {
            return data;
        }
        Data override = THREAD_OVERRIDE.get();
        if (override == null) {
            override = runtimeOverride;
        }
        return override == null ? data : override;
    }

    public static Data setThreadOverride(Data value) {
        Data previous = THREAD_OVERRIDE.get();
        if (value == null) {
            THREAD_OVERRIDE.remove();
        } else {
            THREAD_OVERRIDE.set(value);
        }
        return previous;
    }

    public static Data copy() {
        return new Data(get());
    }

    public static synchronized void save(Data value) {
        normalizeSettings(value);
        data = value;
        if (manager != null) {
            manager.save(value);
        }
        AdaptiveWorldgenWorkers.refreshSettings();
    }

    public static synchronized void reload() {
        if (manager == null || !manager.reloadIfChanged()) {
            return;
        }
        Data loaded = manager.get();
        if (loaded != null) {
            if (normalizeSettings(loaded)) {
                manager.save(loaded);
            }
            data = loaded;
        }
    }

    public static void setRuntimeOverride(Data value) {
        if (BuildConfig.DEBUG) {
            runtimeOverride = value;
        }
    }

    public static void clearRuntimeOverride() {
        if (BuildConfig.DEBUG) {
            runtimeOverride = null;
        }
    }

    public static boolean hasGenerationOverride() {
        return BuildConfig.DEBUG && (runtimeOverride != null || THREAD_OVERRIDE.get() != null);
    }

    public static int resolvedGenerationWorkerThreads() {
        int v = get().generationWorkerThreads;
        return v == 0 ? recommendedWorkerThreads() : Math.max(1, v);
    }

    public static int resolvedSerializationWorkerThreads() {
        int v = get().serializationWorkerThreads;
        return v == 0 ? recommendedWorkerThreads() : Math.max(1, v);
    }

    public static int recommendedWorkerThreads() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, threads - (threads + 6) / 6);
    }

    public static boolean saveChunksAfterWorldCreation() {
        return get().saveChunksAfterWorldCreation;
    }

    public static int incrementalSaveInterval() {
        int interval = get().incrementalSaveInterval;
        return interval == 0 || interval == 32 || interval == 64 || interval == 128 || interval == 256 || interval == 512 ? interval : 128;
    }

    public static boolean skipSavingScreenAfterExit() {
        return get().skipSavingScreenAfterExit && !OptimizationTakeoverRegistry.isControlled(Optimization.BACKGROUND_SAVE);
    }

    public static int resolvedSpawnZoneRadius() {
        return Math.max(MIN_SPAWN_ZONE_RADIUS, Math.min(MAX_SPAWN_ZONE_RADIUS, get().spawnZoneRadius));
    }

    public static boolean parallelWorldgenEnabled() {
        return get().parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN);
    }

    public static StagePriority stagePriority() {
        return get().stagePriority();
    }

    public static boolean parallelChunkStatusEnabled(ChunkStatus status) {
        if (status == null) {
            return true;
        }

        Data config = get();
        if (status == ChunkStatus.STRUCTURE_STARTS) {
            return config.parallelizeStructureStarts;
        }
        if (status == ChunkStatus.STRUCTURE_REFERENCES) {
            return config.parallelizeStructureReferences;
        }
        if (status == ChunkStatus.BIOMES) {
            return config.parallelizeBiomes;
        }
        if (status == ChunkStatus.NOISE) {
            return config.parallelizeNoise;
        }
        if (status == ChunkStatus.SURFACE) {
            return config.parallelizeSurface;
        }
        if (status == ChunkStatus.CARVERS) {
            return config.parallelizeCarvers;
        }
        if (status == ChunkStatus.FEATURES) {
            return config.parallelizeFeatures;
        }
        if (status == ChunkStatus.INITIALIZE_LIGHT) {
            return config.parallelizeInitializeLight;
        }
        if (status == ChunkStatus.LIGHT) {
            return config.parallelizeLight;
        }
        if (status == ChunkStatus.SPAWN) {
            return config.parallelizeSpawn;
        }
        if (status == ChunkStatus.FULL) {
            return config.parallelizeFull;
        }
        return true;
    }

    public static int resolvedFeatureSafetyRadius() {
        if (!parallelWorldgenEnabled()) {
            return DEFAULT_FEATURE_SAFETY_RADIUS;
        }
        int radius = get().featureSafetyRadius;
        if (radius != 0) {
            return Math.max(MIN_FEATURE_SAFETY_RADIUS, Math.min(MAX_FEATURE_SAFETY_RADIUS, radius));
        }
        return switch (get().featureCompatibility) {
            case "increased" -> 3;
            case "max" -> 7;
            default -> DEFAULT_FEATURE_SAFETY_RADIUS;
        };
    }

    public static int internalSpawnPreparationRadius() {
        return resolvedSpawnZoneRadius() + resolvedFeatureSafetyRadius() - 1;
    }

    public static boolean initialSpawnOptimizationEnabled() {
        return get().initialSpawnOptimization && !OptimizationTakeoverRegistry.isControlled(Optimization.INITIAL_SPAWN);
    }

    public static boolean terrainFillOptimizationsEnabled() {
        return get().terrainFillOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.TERRAIN_FILL);
    }

    public static boolean inlineTerrainFillTasks() {
        return BuildConfig.DEBUG && THREAD_OVERRIDE.get() != null
            || terrainFillOptimizationsEnabled() || parallelWorldgenEnabled() && OptimizationTakeoverRegistry.isControlled(Optimization.TERRAIN_FILL);
    }

    public static boolean surfaceOptimizationsEnabled() {
        return get().surfaceOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.SURFACE);
    }

    public static boolean surfaceNoiseOptimizationsEnabled() {
        Data data = get();
        return data.surfaceNoiseOptimizations && !approximateNativePerlinEnabled()
            && !OptimizationTakeoverRegistry.isControlled(Optimization.SURFACE_NOISE);
    }

    public static boolean noiseChunkOptimizationsEnabled() {
        return get().noiseChunkOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK);
    }

    public static boolean biomeFiddleOptimizationsEnabled() {
        Data data = get();
        return data.biomeFiddleOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.BIOME_FIDDLE);
    }

    public static boolean materialRuleOptimizationsEnabled() {
        return get().materialRuleOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.MATERIAL_RULE);
    }

    public static boolean aquiferOptimizationsEnabled() {
        return get().aquiferOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.AQUIFER);
    }

    public static boolean beardifierOptimizationsEnabled() {
        return get().beardifierOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.BEARDIFIER);
    }

    public static boolean oreVeinOptimizationsEnabled() {
        return get().oreVeinOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.ORE_VEIN);
    }

    public static boolean nativePerlinBatchingEnabled() {
        return AdrenalineNatives.isAvailable() && get().nativePerlinBatching;
    }

    public static boolean approximateNativePerlinEnabled() {
        return nativePerlinBatchingEnabled() && get().approximateNativePerlin;
    }

    public static boolean nativeDensityEvaluationEnabled() {
        return AdrenalineNatives.isAvailable() && get().nativeDensityEvaluation;
    }

    public static boolean nativeAquiferBatchingEnabled() {
        return aquiferOptimizationsEnabled() && AdrenalineNatives.isAvailable() && get().nativeAquiferBatching;
    }

    public static boolean parallelChunkSerializationEnabled() {
        return get().parallelChunkSerialization;
    }

    public static boolean biomeSearchOptimizationsEnabled() {
        return get().biomeSearchOptimizations;
    }

    public static boolean biomeClimateOptimizationsEnabled() {
        return get().biomeClimateOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK);
    }

    public static boolean densityMappingOptimizationsEnabled() {
        return get().densityMappingOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK);
    }

    public static boolean blendingOptimizationsEnabled() {
        return get().blendingOptimizations;
    }

    public static boolean heightmapOptimizationsEnabled() {
        return get().heightmapOptimizations;
    }

    public static boolean palettePackingOptimizationsEnabled() {
        return get().palettePackingOptimizations;
    }

    public static boolean shapeCacheOptimizationsEnabled() {
        return get().shapeCacheOptimizations;
    }

    public static boolean nativeSurfaceRulesEnabled() {
        return surfaceOptimizationsEnabled() && AdrenalineNatives.isAvailable() && get().nativeSurfaceRules;
    }

    public static boolean fastLegacyRandomEnabled() {
        return get().fastLegacyRandom && !OptimizationTakeoverRegistry.isControlled(Optimization.FAST_LEGACY_RANDOM);
    }

    public static boolean showCancelButton() {
        return get().showCancelButton;
    }

    public static boolean showChunkPreview() {
        return get().showChunkPreview;
    }

    public static boolean showThreadVisualizer() {
        return BuildConfig.DEBUG && get().showThreadVisualizer;
    }

    public static WarmupMode warmupMode() {
        return get().warmupMode();
    }

    public static boolean prepareWorldCreationContext() {
        return get().prepareWorldCreationContext;
    }

    public static FastTerrainLoadingMode fastTerrainLoadingMode() {
        return get().fastTerrainLoadingMode();
    }

    public static StartBeforehandMode startBeforehandMode() {
        return get().startBeforehand;
    }

    public static boolean debugLoggingEnabled() {
        return BuildConfig.DEBUG && get().debugLogging;
    }

    public static boolean forceEasterEgg() {
        return BuildConfig.DEBUG && get().forceEasterEgg;
    }

    private static boolean normalizeSettings(Data value) {
        boolean changed = false;
        if (value.settingsVersion == 0) {
            value.surfaceNoiseOptimizations = value.surfaceOptimizations;
            value.biomeSearchOptimizations = value.noiseChunkOptimizations;
            value.biomeFiddleOptimizations = value.noiseChunkOptimizations;
            value.biomeClimateOptimizations = value.noiseChunkOptimizations;
            value.densityMappingOptimizations = value.noiseChunkOptimizations;
            value.settingsVersion = 1;
            changed = true;
        }
        if (value.worldgenOptimizations != null) {
            if (!value.worldgenOptimizations) {
                value.terrainFillOptimizations = false;
                value.surfaceOptimizations = false;
                value.noiseChunkOptimizations = false;
                value.materialRuleOptimizations = false;
                value.aquiferOptimizations = false;
                value.beardifierOptimizations = false;
                value.oreVeinOptimizations = false;
                value.initialSpawnOptimization = false;
                value.parallelChunkSerialization = false;
                value.blendingOptimizations = false;
                value.heightmapOptimizations = false;
                value.surfaceNoiseOptimizations = false;
                value.biomeSearchOptimizations = false;
                value.biomeFiddleOptimizations = false;
                value.biomeClimateOptimizations = false;
                value.densityMappingOptimizations = false;
            }
            value.worldgenOptimizations = null;
            changed = true;
        }
        if (value.nativeSettingsVersion == 0) {
            value.nativePerlinBatching = true;
            value.approximateNativePerlin = false;
            value.nativeDensityEvaluation = true;
            value.nativeAquiferBatching = true;
            value.nativeSettingsVersion = 1;
            changed = true;
        }
        return changed;
    }

    public static class Data {
        public Boolean worldgenOptimizations;
        public boolean flatCacheOptimizations = true;
        public boolean regionScanOptimizations = true;
        public boolean biomeCodecCache = true;
        public boolean sectionTerrainOptimizations = true;
        public boolean surfaceNoiseOptimizations = true;
        public boolean biomeSearchOptimizations = true;
        public boolean biomeFiddleOptimizations = true;
        public boolean biomeClimateOptimizations = true;
        public boolean densityMappingOptimizations = true;
        public boolean blendingOptimizations = true;
        public boolean heightmapOptimizations = true;
        public boolean parallelChunkSerialization = true;
        public boolean palettePackingOptimizations = true;
        public boolean shapeCacheOptimizations = true;
        public boolean nativeSurfaceRules = true;
        public boolean surfaceColumnOptimizations = true;
        public boolean terrainFillOptimizations = true;
        public boolean surfaceOptimizations = true;
        public boolean noiseChunkOptimizations = true;
        public boolean materialRuleOptimizations = true;
        public boolean aquiferOptimizations = true;
        public boolean beardifierOptimizations = true;
        public boolean oreVeinOptimizations = true;
        public boolean initialSpawnOptimization = true;
        public int generationWorkerThreads = 0;
        public boolean adaptiveGenerationWorkers = true;
        public int serializationWorkerThreads = 0;
        public boolean saveChunksAfterWorldCreation = true;
        public int incrementalSaveInterval = 128;
        public boolean skipSavingScreenAfterExit = true;
        public int spawnZoneRadius = DEFAULT_SPAWN_ZONE_RADIUS;
        public boolean parallelWorldgen = true;
        public boolean prioritizeHigherStages = true;
        public StagePriority stagePriority;
        public String featureCompatibility = "classic";
        public int featureSafetyRadius;
        public boolean parallelizeStructureStarts = true;
        public boolean parallelizeStructureReferences = true;
        public boolean parallelizeBiomes = true;
        public boolean parallelizeNoise = true;
        public boolean parallelizeSurface = true;
        public boolean parallelizeCarvers = true;
        public boolean parallelizeFeatures = true;
        public boolean parallelizeInitializeLight = true;
        public boolean parallelizeLight = true;
        public boolean parallelizeSpawn = true;
        public boolean parallelizeFull = true;
        public boolean fastLegacyRandom = true;
        public boolean nativePerlinBatching = true;
        public boolean approximateNativePerlin = false;
        public boolean nativeDensityEvaluation = true;
        public boolean nativeAquiferBatching = true;
        public int nativeSettingsVersion = 1;
        public int settingsVersion;
        public boolean showCancelButton = true;
        public boolean showChunkPreview = true;
        public boolean showThreadVisualizer;
        public boolean warmupOnStartup = true;
        public boolean blockingWarmupOnStartup = true;
        public boolean prepareWorldCreationContext = true;
        public boolean fastTerrainLoading = true;
        public FastTerrainLoadingMode fastTerrainLoadingMode;
        public StartBeforehandMode startBeforehand = StartBeforehandMode.OFF;
        public boolean debugLogging = false;
        public boolean forceEasterEgg = false;

        public Data() {
        }

        public Data(Data other) {
            this.worldgenOptimizations = other.worldgenOptimizations;
            this.flatCacheOptimizations = other.flatCacheOptimizations;
            this.regionScanOptimizations = other.regionScanOptimizations;
            this.biomeCodecCache = other.biomeCodecCache;
            this.sectionTerrainOptimizations = other.sectionTerrainOptimizations;
            this.surfaceNoiseOptimizations = other.surfaceNoiseOptimizations;
            this.biomeSearchOptimizations = other.biomeSearchOptimizations;
            this.biomeFiddleOptimizations = other.biomeFiddleOptimizations;
            this.biomeClimateOptimizations = other.biomeClimateOptimizations;
            this.densityMappingOptimizations = other.densityMappingOptimizations;
            this.blendingOptimizations = other.blendingOptimizations;
            this.heightmapOptimizations = other.heightmapOptimizations;
            this.parallelChunkSerialization = other.parallelChunkSerialization;
            this.palettePackingOptimizations = other.palettePackingOptimizations;
            this.shapeCacheOptimizations = other.shapeCacheOptimizations;
            this.nativeSurfaceRules = other.nativeSurfaceRules;
            this.surfaceColumnOptimizations = other.surfaceColumnOptimizations;
            this.terrainFillOptimizations = other.terrainFillOptimizations;
            this.surfaceOptimizations = other.surfaceOptimizations;
            this.noiseChunkOptimizations = other.noiseChunkOptimizations;
            this.materialRuleOptimizations = other.materialRuleOptimizations;
            this.aquiferOptimizations = other.aquiferOptimizations;
            this.beardifierOptimizations = other.beardifierOptimizations;
            this.oreVeinOptimizations = other.oreVeinOptimizations;
            this.initialSpawnOptimization = other.initialSpawnOptimization;
            this.generationWorkerThreads = other.generationWorkerThreads;
            this.adaptiveGenerationWorkers = other.adaptiveGenerationWorkers;
            this.serializationWorkerThreads = other.serializationWorkerThreads;
            this.saveChunksAfterWorldCreation = other.saveChunksAfterWorldCreation;
            this.incrementalSaveInterval = other.incrementalSaveInterval;
            this.skipSavingScreenAfterExit = other.skipSavingScreenAfterExit;
            this.spawnZoneRadius = other.spawnZoneRadius;
            this.parallelWorldgen = other.parallelWorldgen;
            this.prioritizeHigherStages = other.prioritizeHigherStages;
            this.stagePriority = other.stagePriority;
            this.featureCompatibility = other.featureCompatibility;
            this.featureSafetyRadius = other.featureSafetyRadius;
            this.parallelizeStructureStarts = other.parallelizeStructureStarts;
            this.parallelizeStructureReferences = other.parallelizeStructureReferences;
            this.parallelizeBiomes = other.parallelizeBiomes;
            this.parallelizeNoise = other.parallelizeNoise;
            this.parallelizeSurface = other.parallelizeSurface;
            this.parallelizeCarvers = other.parallelizeCarvers;
            this.parallelizeFeatures = other.parallelizeFeatures;
            this.parallelizeInitializeLight = other.parallelizeInitializeLight;
            this.parallelizeLight = other.parallelizeLight;
            this.parallelizeSpawn = other.parallelizeSpawn;
            this.parallelizeFull = other.parallelizeFull;
            this.fastLegacyRandom = other.fastLegacyRandom;
            this.nativePerlinBatching = other.nativePerlinBatching;
            this.approximateNativePerlin = other.approximateNativePerlin;
            this.nativeDensityEvaluation = other.nativeDensityEvaluation;
            this.nativeAquiferBatching = other.nativeAquiferBatching;
            this.nativeSettingsVersion = other.nativeSettingsVersion;
            this.settingsVersion = other.settingsVersion;
            this.showCancelButton = other.showCancelButton;
            this.showChunkPreview = other.showChunkPreview;
            this.showThreadVisualizer = other.showThreadVisualizer;
            this.warmupOnStartup = other.warmupOnStartup;
            this.blockingWarmupOnStartup = other.blockingWarmupOnStartup;
            this.prepareWorldCreationContext = other.prepareWorldCreationContext;
            this.fastTerrainLoading = other.fastTerrainLoading;
            this.fastTerrainLoadingMode = other.fastTerrainLoadingMode;
            this.startBeforehand = other.startBeforehand;
            this.debugLogging = other.debugLogging;
            this.forceEasterEgg = other.forceEasterEgg;
        }

        public void disableWorldgenOptimizations() {
            this.biomeCodecCache = false;
            this.regionScanOptimizations = false;
            this.flatCacheOptimizations = false;
            this.sectionTerrainOptimizations = false;
            this.terrainFillOptimizations = false;
            this.surfaceOptimizations = false;
            this.noiseChunkOptimizations = false;
            this.materialRuleOptimizations = false;
            this.aquiferOptimizations = false;
            this.beardifierOptimizations = false;
            this.oreVeinOptimizations = false;
            this.initialSpawnOptimization = false;
            this.parallelChunkSerialization = false;
            this.blendingOptimizations = false;
            this.heightmapOptimizations = false;
            this.surfaceNoiseOptimizations = false;
            this.biomeSearchOptimizations = false;
            this.biomeFiddleOptimizations = false;
            this.biomeClimateOptimizations = false;
            this.densityMappingOptimizations = false;
            this.palettePackingOptimizations = false;
            this.shapeCacheOptimizations = false;
            this.nativeSurfaceRules = false;
            this.surfaceColumnOptimizations = false;
            this.nativePerlinBatching = false;
            this.nativeDensityEvaluation = false;
            this.nativeAquiferBatching = false;
            this.fastLegacyRandom = false;
        }

        public WarmupMode warmupMode() {
            if (!this.warmupOnStartup) {
                return WarmupMode.OFF;
            }
            return this.blockingWarmupOnStartup ? WarmupMode.ON : WarmupMode.NON_BLOCK;
        }

        public void setWarmupMode(WarmupMode mode) {
            this.warmupOnStartup = mode != WarmupMode.OFF;
            this.blockingWarmupOnStartup = mode == WarmupMode.ON;
        }

        public FastTerrainLoadingMode fastTerrainLoadingMode() {
            return this.fastTerrainLoadingMode == null ? (this.fastTerrainLoading ? FastTerrainLoadingMode.ON : FastTerrainLoadingMode.OFF) : this.fastTerrainLoadingMode;
        }

        public void setFastTerrainLoadingMode(FastTerrainLoadingMode mode) {
            this.fastTerrainLoadingMode = mode;
            this.fastTerrainLoading = mode != FastTerrainLoadingMode.OFF;
        }

        public StagePriority stagePriority() {
            return this.stagePriority == null ? (this.prioritizeHigherStages ? StagePriority.HIGHEST : StagePriority.FIFO) : this.stagePriority;
        }

        public void setStagePriority(StagePriority priority) {
            this.stagePriority = priority;
            this.prioritizeHigherStages = priority != StagePriority.FIFO;
        }
    }

    public enum WarmupMode {
        ON,
        NON_BLOCK,
        OFF
    }

    public enum FastTerrainLoadingMode {
        OFF,
        ON,
        EXTREME
    }

    public enum StartBeforehandMode {
        ON,
        OFF,
        BORING
    }

    public enum StagePriority {
        FIFO,
        HIGHEST,
        NEAREST
    }
}
