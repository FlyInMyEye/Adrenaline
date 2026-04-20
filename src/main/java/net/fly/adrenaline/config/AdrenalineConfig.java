package net.fly.adrenaline.config;

import net.fly.configlib.JsonConfigManager;

import java.util.ArrayList;
import java.util.List;

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

    public static boolean enabled() {
        return get().enabled;
    }

    public static boolean parallelWorldgenEnabled() {
        return enabled() && get().parallelWorldgen;
    }

    public static boolean terrainFillOptimizationsEnabled() {
        return enabled() && get().worldgenOptimizations && get().terrainFillOptimizations;
    }

    public static boolean surfaceOptimizationsEnabled() {
        return enabled() && get().worldgenOptimizations && get().surfaceOptimizations;
    }

    public static boolean noiseChunkOptimizationsEnabled() {
        return enabled() && get().worldgenOptimizations && get().noiseChunkOptimizations;
    }

    public static boolean materialRuleOptimizationsEnabled() {
        return enabled() && get().worldgenOptimizations && get().materialRuleOptimizations;
    }

    public static boolean parallelChunkSerializationEnabled() {
        return enabled() && get().parallelChunkSerialization;
    }

    public static boolean chunkIoCacheEnabled() {
        return enabled() && get().chunkIoCache;
    }

    public static boolean fastLegacyRandomEnabled() {
        return enabled() && get().fastLegacyRandom;
    }

    public static boolean preloadProblematicClassesEnabled() {
        return enabled() && get().preloadProblematicClasses;
    }

    public static class Data {
        public boolean enabled = true;
        public boolean worldgenOptimizations = true;
        public boolean terrainFillOptimizations = true;
        public boolean surfaceOptimizations = true;
        public boolean noiseChunkOptimizations = true;
        public boolean materialRuleOptimizations = true;
        public int workerThreads = 0;
        public boolean parallelWorldgen = true;
        public boolean parallelChunkSerialization = true;
        public boolean chunkIoCache = true;
        public boolean fastLegacyRandom = true;
        public boolean preloadProblematicClasses = true;
        public List<String> stageBlacklist = new ArrayList<>();
        public List<String> featureBlacklist = new ArrayList<>();

        public Data() {
        }

        public Data(Data other) {
            this.enabled = other.enabled;
            this.worldgenOptimizations = other.worldgenOptimizations;
            this.terrainFillOptimizations = other.terrainFillOptimizations;
            this.surfaceOptimizations = other.surfaceOptimizations;
            this.noiseChunkOptimizations = other.noiseChunkOptimizations;
            this.materialRuleOptimizations = other.materialRuleOptimizations;
            this.workerThreads = other.workerThreads;
            this.parallelWorldgen = other.parallelWorldgen;
            this.parallelChunkSerialization = other.parallelChunkSerialization;
            this.chunkIoCache = other.chunkIoCache;
            this.fastLegacyRandom = other.fastLegacyRandom;
            this.preloadProblematicClasses = other.preloadProblematicClasses;
            this.stageBlacklist = new ArrayList<>(other.stageBlacklist);
            this.featureBlacklist = new ArrayList<>(other.featureBlacklist);
        }
    }
}
