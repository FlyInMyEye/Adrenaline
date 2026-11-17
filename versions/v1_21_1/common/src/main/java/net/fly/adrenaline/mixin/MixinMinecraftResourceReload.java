package net.fly.adrenaline.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.client.ClientResourceReloadState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftResourceReload {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$awaitInitialResources(CallbackInfo ci) {
        ClientResourceReloadState.setReady(false);
    }

    @Inject(method = "onResourceLoadFinished", at = @At("RETURN"))
    private void adrenaline$markResourcesReady(CallbackInfo ci) {
        ClientResourceReloadState.setReady(true);
    }

    @Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void adrenaline$awaitReloadedResources(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ClientResourceReloadState.setReady(false);
    }
}
