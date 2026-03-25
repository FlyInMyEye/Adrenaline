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

    public static void reload() {
        MANAGER.reloadIfChanged();
    }

    public static int resolvedWorkerThreads() {
        int v = get().workerThreads;
        return v == 0 ? Runtime.getRuntime().availableProcessors() : v;
    }

    public static class Data {
        public int workerThreads = 0;
        public List<String> stageBlacklist = new ArrayList<>();
        public List<String> featureBlacklist = new ArrayList<>();
    }
}
