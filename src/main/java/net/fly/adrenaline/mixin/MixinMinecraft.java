package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.fly.adrenaline.client.WorldDeletion;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    private IntegratedServer singleplayerServer;

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void adrenaline$resetWorldLoadCancellation(
        String levelId,
        LevelStorageSource.LevelStorageAccess storageAccess,
        PackRepository packRepository,
        WorldStem worldStem,
        boolean newWorld,
        CallbackInfo ci
    ) {
        BackgroundWorldgenWarmup.beforeWorldLoad((Minecraft) (Object) this, levelId);
        ChunkJobScheduler.get().resume();
        EarlyWorldEntry.reset();
        WorldLoadCancellation.reset(levelId, newWorld);
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z"), cancellable = true)
    private void adrenaline$cancelWhileWaitingForServer(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;getConnection()Lnet/minecraft/server/network/ServerConnectionListener;"), cancellable = true)
    private void adrenaline$cancelBeforeConnecting(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    @Redirect(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;clearLevel()V"))
    private void adrenaline$preserveScreenDuringWarmup(Minecraft minecraft) {
        if (!BackgroundWorldgenWarmup.isWarmupCall()) {
            minecraft.clearLevel();
        }
    }

    @Inject(
        method = "doWorldLoad",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReference;get()Ljava/lang/Object;",
            ordinal = 0
        ),
        cancellable = true
    )
    private void adrenaline$detachWarmupServer(
        String levelId,
        LevelStorageSource.LevelStorageAccess storageAccess,
        PackRepository packRepository,
        WorldStem worldStem,
        boolean newWorld,
        CallbackInfo ci
    ) {
        if (BackgroundWorldgenWarmup.isWarmupCall()) {
            BackgroundWorldgenWarmup.detachWorldLoad(this.singleplayerServer);
            this.singleplayerServer = null;
            ci.cancel();
        }
    }

    @Inject(method = "doWorldLoad", at = @At("RETURN"))
    private void adrenaline$exitWorldLoad(
        String levelId,
        LevelStorageSource.LevelStorageAccess storageAccess,
        PackRepository packRepository,
        WorldStem worldStem,
        boolean newWorld,
        CallbackInfo ci
    ) {
        BackgroundWorldgenWarmup.exitWorldLoad(levelId);
    }

    private void adrenaline$cancelWorldLoad(CallbackInfo ci) {
        if (!WorldLoadCancellation.isRequested()) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        String levelToDelete = WorldLoadCancellation.levelToDelete();
        IntegratedServer server = minecraft.getSingleplayerServer();
        this.singleplayerServer = null;
        minecraft.setScreen(new TitleScreen());
        if (levelToDelete != null) {
            WorldDeletion.deleteAsync(minecraft, server, levelToDelete);
        }
        ci.cancel();
    }
}
