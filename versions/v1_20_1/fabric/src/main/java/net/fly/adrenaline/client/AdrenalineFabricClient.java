package net.fly.adrenaline.client;

import java.util.Collection;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class AdrenalineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientResourceReloadState.setReady(false);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation("adrenaline", "client_ready");
            }

            @Override
            public Collection<ResourceLocation> getFabricDependencies() {
                return List.of(ResourceReloadListenerKeys.MODELS);
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                ClientResourceReloadState.setReady(true);
            }
        });
        ModernFixCompatController modernFixController = new ModernFixCompatController();
        BackgroundWorldgenWarmup worldgenWarmup = new BackgroundWorldgenWarmup();
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            modernFixController.tick(minecraft);
            worldgenWarmup.tick(minecraft);
        });
    }
}
