package net.fly.adrenaline.mixin;

import java.util.function.BooleanSupplier;
import net.fly.adrenaline.scheduler.AdaptiveWorldgenWorkers;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1100)
public abstract class MixinMinecraftServer {

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$startAdaptiveWorkers(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.start((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void adrenaline$stopAdaptiveWorkers(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.stop((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void adrenaline$beginAdaptiveWorkerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        AdaptiveWorldgenWorkers.beginTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void adrenaline$finishAdaptiveWorkerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        AdaptiveWorldgenWorkers.endTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void adrenaline$advanceAdaptiveWorkerTick(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.advanceTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"))
    private void adrenaline$advanceAdaptivePreparationTick(CallbackInfo ci) {
        AdaptiveWorldgenWorkers.advancePreparationTick((MinecraftServer) (Object) this);
    }
}
