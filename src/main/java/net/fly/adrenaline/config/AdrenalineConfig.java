package net.fly.adrenaline.config;

import net.fly.configlib.JsonConfigManager;

public class AdrenalineConfig {

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

    public static int resolvedWorkerThreads() {
        int v = get().workerThreads;
        return v == 0 ? Runtime.getRuntime().availableProcessors() : Math.max(1, v);
    }

    public static int resolvedSpawnZoneRadius() {
        int v = get().spawnZoneRadius;
        return v == 0 ? 11 : Math.max(12, Math.min(30, v));
    }

    public static boolean parallelWorldgenEnabled() {
        return get().parallelWorldgen;
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

    public static boolean parallelChunkSerializationEnabled() {
        return parallelWorldgenEnabled();
    }

    public static boolean fastLegacyRandomEnabled() {
        return get().fastLegacyRandom;
    }

    public static boolean preloadProblematicClassesEnabled() {
        return get().preloadProblematicClasses;
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
        public int workerThreads = 0;
        public int spawnZoneRadius = 0;
        public boolean parallelWorldgen = true;
        public boolean fastLegacyRandom = true;
        public boolean preloadProblematicClasses = true;
        public boolean debugLogging = false;

        public Data() {
        }

        public Data(Data other) {
            this.worldgenOptimizations = other.worldgenOptimizations;
            this.terrainFillOptimizations = other.terrainFillOptimizations;
            this.surfaceOptimizations = other.surfaceOptimizations;
            this.noiseChunkOptimizations = other.noiseChunkOptimizations;
            this.materialRuleOptimizations = other.materialRuleOptimizations;
            this.workerThreads = other.workerThreads;
            this.spawnZoneRadius = other.spawnZoneRadius;
            this.parallelWorldgen = other.parallelWorldgen;
            this.fastLegacyRandom = other.fastLegacyRandom;
            this.preloadProblematicClasses = other.preloadProblematicClasses;
            this.debugLogging = other.debugLogging;
        }
    }
}
