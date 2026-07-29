package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class MixinReceivingLevelScreen {

    @Shadow
    private boolean loadingPacketsReceived;

    @Shadow
    private boolean oneTickSkipped;

    @Shadow
    public abstract void onClose();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void adrenaline$enterWhenCenterChunkArrives(CallbackInfo ci) {
        if (!AdrenalineConfig.fastTerrainLoading()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.loadingPacketsReceived || !this.oneTickSkipped || minecraft.player == null || minecraft.level == null) {
            return;
        }
        BlockPos position = minecraft.player.blockPosition();
        if (minecraft.level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
            this.onClose();
            ci.cancel();
        }
    }
}
