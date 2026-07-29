package net.fly.adrenaline.mixin;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fly.adrenaline.client.WorldCreationContextWaiter;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public class MixinCreateWorldScreen {

    @Inject(method = "queueLoadScreen", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$suppressPreloadMessage(Minecraft minecraft, Component message, CallbackInfo ci) {
        if (WorldCreationContextWaiter.isPreloading()) {
            ci.cancel();
        }
    }

    @Redirect(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void adrenaline$suppressPreloadScreen(Minecraft minecraft, Screen screen) {
        if (!WorldCreationContextWaiter.isPreloading()) {
            minecraft.setScreen(screen);
        }
    }

    @Inject(method = "openFresh", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$useCachedContext(Minecraft minecraft, Screen parent, CallbackInfo ci) {
        WorldCreationContext context = AdrenalineConfig.prepareWorldCreationContext() ? WorldCreationContextWaiter.get() : null;
        if (context != null) {
            minecraft.setScreen(MixinCreateWorldScreenInvoker.adrenaline$create(minecraft, parent, context, Optional.of(WorldPresets.NORMAL), OptionalLong.empty()));
            ci.cancel();
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$cacheFreshContext(Minecraft minecraft, Screen parent, WorldCreationContext context, Optional<ResourceKey<WorldPreset>> preset, OptionalLong seed, CallbackInfo ci) {
        if (AdrenalineConfig.prepareWorldCreationContext() && seed.isEmpty()) {
            WorldCreationContextWaiter.set(context);
        }
    }

    @Redirect(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/WorldLoader;load(Lnet/minecraft/server/WorldLoader$InitConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;Lnet/minecraft/server/WorldLoader$ResultFactory;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static <D, R> CompletableFuture<R> adrenaline$shareContextLoad(
        WorldLoader.InitConfig initConfig,
        WorldLoader.WorldDataSupplier<D> dataSupplier,
        WorldLoader.ResultFactory<D, R> resultFactory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        if (AdrenalineConfig.prepareWorldCreationContext()) {
            return WorldCreationContextWaiter.load(initConfig, dataSupplier, resultFactory, backgroundExecutor, gameExecutor);
        }
        return WorldLoader.load(initConfig, dataSupplier, resultFactory, backgroundExecutor, gameExecutor);
    }
}
