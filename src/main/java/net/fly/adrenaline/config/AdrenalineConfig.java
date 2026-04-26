package net.fly.adrenaline.config;

import net.fly.configlib.JsonConfigManager;

public class AdrenalineConfig {

    public static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    public static final int MIN_SPAWN_ZONE_RADIUS = 1;
    public static final int MAX_SPAWN_ZONE_RADIUS = 30;

    private static JsonConfigManager<Data> MANAGER;

    public static void init() {
        MANAGER = new JsonConfigManager<>("adrenaline.json", Data.class, Data::new, null);
        MANAGER.init();
    }

    public static Data get() {
        return MANAGER.get();
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

    public static boolean debugLoggingEnabled() {
        return get().debugLogging;
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
        public boolean fastLegacyRandom = true;
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
            this.fastLegacyRandom = other.fastLegacyRandom;
            this.debugLogging = other.debugLogging;
        }
    }
}
