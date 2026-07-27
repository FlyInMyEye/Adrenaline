package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void adrenaline$resetWorldLoadCancellation(CallbackInfo ci) {
        ChunkJobScheduler.get().resume();
        EarlyWorldEntry.reset();
        WorldLoadCancellation.reset();
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z"), cancellable = true)
    private void adrenaline$cancelWhileWaitingForServer(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;getConnection()Lnet/minecraft/server/network/ServerConnectionListener;"), cancellable = true)
    private void adrenaline$cancelBeforeConnecting(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    private void adrenaline$cancelWorldLoad(CallbackInfo ci) {
        if (!WorldLoadCancellation.isRequested()) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        minecraft.clearLevel(new TitleScreen());
        ci.cancel();
    }
}
