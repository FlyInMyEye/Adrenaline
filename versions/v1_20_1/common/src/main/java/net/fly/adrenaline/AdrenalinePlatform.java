package net.fly.adrenaline;

import java.nio.file.Path;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

public final class AdrenalinePlatform {
    private static Path configDirectory;
    private static Predicate<String> modLoaded = modId -> false;
    private static CreateWorldSpawnHook createWorldSpawnHook = (level, levelData) -> false;

    private AdrenalinePlatform() {
    }

    public static void initialize(Path configPath, Predicate<String> modLoadedPredicate) {
        configDirectory = configPath;
        modLoaded = modLoadedPredicate;
    }

    public static Path configDirectory() {
        return configDirectory;
    }

    public static boolean isModLoaded(String modId) {
        return modLoaded.test(modId);
    }

    public static void setCreateWorldSpawnHook(CreateWorldSpawnHook hook) {
        createWorldSpawnHook = hook;
    }

    public static boolean onCreateWorldSpawn(ServerLevel level, ServerLevelData levelData) {
        return createWorldSpawnHook.onCreateWorldSpawn(level, levelData);
    }

    @FunctionalInterface
    public interface CreateWorldSpawnHook {
        boolean onCreateWorldSpawn(ServerLevel level, ServerLevelData levelData);
    }
}
