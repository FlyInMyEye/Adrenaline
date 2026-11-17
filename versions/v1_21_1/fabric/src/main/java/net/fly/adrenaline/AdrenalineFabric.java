package net.fly.adrenaline;

import java.nio.file.Path;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fly.adrenaline.util.DebugChunkCommands;
import net.fly.adrenaline.AdrenalinePlatform.ModInfo;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class AdrenalineFabric implements ModInitializer {
    public static final String MODID = "adrenaline";

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        AdrenalinePlatform.initialize(loader.getConfigDir(), loader::isModLoaded, modId -> loader.getModContainer(modId).map(container -> {
            var origin = container.getOrigin();
            String fileName = switch (origin.getKind()) {
                case PATH -> origin.getPaths().isEmpty() ? "" : origin.getPaths().get(0).getFileName().toString();
                case NESTED -> Path.of(origin.getParentSubLocation()).getFileName().toString();
                default -> "";
            };
            return new ModInfo(modId, container.getMetadata().getName(), fileName);
        }).orElse(null));
        AdrenalineCommon.init(AdrenalinePlatform.configDirectory());
        if (BuildConfig.DEBUG) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DebugChunkCommands.register(dispatcher));
        }
    }
}
