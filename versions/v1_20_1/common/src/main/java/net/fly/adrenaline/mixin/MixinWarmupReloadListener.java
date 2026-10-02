package net.fly.adrenaline.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fly.adrenaline.util.WarmupResourceLoading;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SimplePreparableReloadListener.class, ServerFunctionLibrary.class})
public class MixinWarmupReloadListener {

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void adrenaline$skipWarmupData(
        PreparableReloadListener.PreparationBarrier barrier,
        ResourceManager resources,
        ProfilerFiller prepareProfiler,
        ProfilerFiller applyProfiler,
        Executor prepareExecutor,
        Executor applyExecutor,
        CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        Object listener = this;
        if (WarmupResourceLoading.isMinimal() && (listener instanceof RecipeManager || listener instanceof ServerAdvancementManager || listener instanceof ServerFunctionLibrary)) {
            cir.setReturnValue(barrier.wait(null).thenAcceptAsync(ignored -> {}, applyExecutor));
        }
    }
}
