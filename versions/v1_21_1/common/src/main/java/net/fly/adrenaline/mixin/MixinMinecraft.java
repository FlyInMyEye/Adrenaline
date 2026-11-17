package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.WorldDeletion;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.fly.adrenaline.util.WorldgenChunkPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow private IntegratedServer singleplayerServer;
    @Shadow private boolean isLocalServer;

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void adrenaline$resetWorldLoadCancellation(LevelStorageSource.LevelStorageAccess storageAccess, PackRepository packRepository,
            WorldStem worldStem, boolean newWorld, CallbackInfo ci) {
        if (newWorld && AdrenalineConfig.showChunkPreview()) {
            WorldgenChunkPreview.begin();
        } else {
            WorldgenChunkPreview.end();
        }
        ChunkJobScheduler.get().resume();
        EarlyWorldEntry.reset();
        WorldLoadCancellation.reset(storageAccess.getLevelId(), newWorld);
    }

    @Inject(method = "destroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;close()V"))
    private void adrenaline$finishWorldCleanupBeforeShutdown(CallbackInfo ci) {
        WorldDeletion.awaitCompletion();
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z"), cancellable = true)
    private void adrenaline$cancelWhileWaitingForServer(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;getConnection()Lnet/minecraft/server/network/ServerConnectionListener;"), cancellable = true)
    private void adrenaline$cancelBeforeConnecting(CallbackInfo ci) {
        this.adrenaline$cancelWorldLoad(ci);
    }

    @Unique
    private void adrenaline$cancelWorldLoad(CallbackInfo ci) {
        if (!WorldLoadCancellation.isRequested()) {
            return;
        }
        if (WorldLoadCancellation.isClientHandled()) {
            ci.cancel();
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        String levelToDelete = WorldLoadCancellation.levelToDelete();
        IntegratedServer server = this.singleplayerServer;
        this.singleplayerServer = null;
        this.isLocalServer = false;
        if (server != null) {
            server.execute(() -> server.halt(false));
        }
        minecraft.disconnect(new TitleScreen());
        if (levelToDelete != null) {
            WorldDeletion.deleteAsync(minecraft, server, levelToDelete);
        }
        ci.cancel();
    }
}
