package net.fly.adrenaline.config;

import net.fly.adrenaline.BuildConfig;
import net.fly.configlib.JsonConfigManager;
import net.minecraft.world.level.chunk.ChunkStatus;

public class AdrenalineConfig {

    public static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    public static final int MIN_SPAWN_ZONE_RADIUS = 1;
    public static final int MAX_SPAWN_ZONE_RADIUS = 30;
    public static final int DEFAULT_FEATURE_SAFETY_RADIUS = 1;
    public static final int MIN_FEATURE_SAFETY_RADIUS = 1;
    public static final int MAX_FEATURE_SAFETY_RADIUS = 8;

    private static final Data DEFAULTS = new Data();
    private static volatile JsonConfigManager<Data> MANAGER;

    public static void init() {
        JsonConfigManager<Data> manager = new JsonConfigManager<>("adrenaline.json", Data.class, Data::new, null);
        manager.init();
        MANAGER = manager;
    }

    public static Data get() {
        JsonConfigManager<Data> manager = MANAGER;
        return manager == null ? DEFAULTS : manager.get();
    }

    public static Data copy() {
        return new Data(get());
    }

    public static void save(Data data) {
        MANAGER.save(data);
    }

    public static void reload() {
        MANAGER.reloadIfChanged();
    }

    public static int resolvedGenerationWorkerThreads() {
        int v = get().generationWorkerThreads;
        return v == 0 ? Runtime.getRuntime().availableProcessors() : Math.max(1, v);
    }

    public static int resolvedSerializationWorkerThreads() {
        int v = get().serializationWorkerThreads;
        return v == 0 ? Runtime.getRuntime().availableProcessors() : Math.max(1, v);
    }

    public static int resolvedSpawnZoneRadius() {
        return Math.max(MIN_SPAWN_ZONE_RADIUS, Math.min(MAX_SPAWN_ZONE_RADIUS, get().spawnZoneRadius));
    }

    public static boolean parallelWorldgenEnabled() {
        return get().parallelWorldgen;
    }

    public static boolean prioritizeHigherStagesEnabled() {
        return get().prioritizeHigherStages;
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
        return get().worldgenOptimizations && get().initialSpawnOptimization;
    }

    public static boolean terrainFillOptimizationsEnabled() {
        return get().worldgenOptimizations && get().terrainFillOptimizations;
    }

    public static boolean surfaceOptimizationsEnabled() {
        return get().worldgenOptimizations && get().surfaceOptimizations;
    }

    public static boolean noiseChunkOptimizationsEnabled() {
        return get().worldgenOptimizations && get().noiseChunkOptimizations;
    }

    public static boolean materialRuleOptimizationsEnabled() {
        return get().worldgenOptimizations && get().materialRuleOptimizations;
    }

    public static boolean aquiferOptimizationsEnabled() {
        return get().worldgenOptimizations && get().aquiferOptimizations;
    }

    public static boolean beardifierOptimizationsEnabled() {
        return get().worldgenOptimizations && get().beardifierOptimizations;
    }

    public static boolean oreVeinOptimizationsEnabled() {
        return get().worldgenOptimizations && get().oreVeinOptimizations;
    }

    public static boolean parallelChunkSerializationEnabled() {
        return get().worldgenOptimizations;
    }

    public static boolean fastLegacyRandomEnabled() {
        return get().fastLegacyRandom;
    }

    public static boolean showCancelButton() {
        return get().showCancelButton;
    }

    public static boolean showThreadVisualizer() {
        return get().showThreadVisualizer;
    }

    public static StartBeforehandMode startBeforehandMode() {
        return get().startBeforehand;
    }

    public static boolean debugLoggingEnabled() {
        return BuildConfig.DEBUG && get().debugLogging;
    }

    public static class Data {
        public boolean worldgenOptimizations = true;
        public boolean terrainFillOptimizations = true;
        public boolean surfaceOptimizations = true;
        public boolean noiseChunkOptimizations = true;
        public boolean materialRuleOptimizations = true;
        public boolean aquiferOptimizations = true;
        public boolean beardifierOptimizations = true;
        public boolean oreVeinOptimizations = true;
        public boolean initialSpawnOptimization = true;
        public int generationWorkerThreads = 0;
        public int serializationWorkerThreads = 0;
        public int spawnZoneRadius = DEFAULT_SPAWN_ZONE_RADIUS;
        public boolean parallelWorldgen = true;
        public boolean prioritizeHigherStages = true;
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
        public boolean showCancelButton = true;
        public boolean showThreadVisualizer;
        public StartBeforehandMode startBeforehand = StartBeforehandMode.OFF;
        public boolean debugLogging = false;

        public Data() {
        }

        public Data(Data other) {
            this.worldgenOptimizations = other.worldgenOptimizations;
            this.terrainFillOptimizations = other.terrainFillOptimizations;
            this.surfaceOptimizations = other.surfaceOptimizations;
            this.noiseChunkOptimizations = other.noiseChunkOptimizations;
            this.materialRuleOptimizations = other.materialRuleOptimizations;
            this.aquiferOptimizations = other.aquiferOptimizations;
            this.beardifierOptimizations = other.beardifierOptimizations;
            this.oreVeinOptimizations = other.oreVeinOptimizations;
            this.initialSpawnOptimization = other.initialSpawnOptimization;
            this.generationWorkerThreads = other.generationWorkerThreads;
            this.serializationWorkerThreads = other.serializationWorkerThreads;
            this.spawnZoneRadius = other.spawnZoneRadius;
            this.parallelWorldgen = other.parallelWorldgen;
            this.prioritizeHigherStages = other.prioritizeHigherStages;
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
            this.showCancelButton = other.showCancelButton;
            this.showThreadVisualizer = other.showThreadVisualizer;
            this.startBeforehand = other.startBeforehand;
            this.debugLogging = other.debugLogging;
        }
    }

    public enum StartBeforehandMode {
        ON,
        OFF,
        BORING
    }
}
