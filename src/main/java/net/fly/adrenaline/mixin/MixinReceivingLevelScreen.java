package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class MixinReceivingLevelScreen {

    @Shadow
    private boolean loadingPacketsReceived;

    @Shadow
    private boolean oneTickSkipped;

    @Shadow
    public abstract void onClose();

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 1)
    private Component adrenaline$replaceTerrainLoadingText(Component message) {
        return AdrenalineConfig.fastTerrainLoadingMode() == AdrenalineConfig.FastTerrainLoadingMode.OFF ? message : Component.translatable("gui.adrenaline.loading.getting_spawnpoint");
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void adrenaline$bypassTerrainLoading(CallbackInfo ci) {
        AdrenalineConfig.FastTerrainLoadingMode mode = AdrenalineConfig.fastTerrainLoadingMode();
        if (mode == AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return;
        }
        if (mode == AdrenalineConfig.FastTerrainLoadingMode.EXTREME) {
            this.onClose();
            ci.cancel();
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
