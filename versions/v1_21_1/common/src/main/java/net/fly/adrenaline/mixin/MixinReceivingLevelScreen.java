package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
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
    public abstract void onClose();

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 1)
    private Component adrenaline$replaceTerrainLoadingText(Component message) {
        return AdrenalineConfig.fastTerrainLoadingMode() == AdrenalineConfig.FastTerrainLoadingMode.OFF ? message : Component.translatable("gui.adrenaline.loading.getting_spawnpoint");
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.TERRAIN_LOADING)
    private void adrenaline$bypassTerrainLoading(CallbackInfo ci) {
        if (AdrenalineConfig.fastTerrainLoadingMode() == AdrenalineConfig.FastTerrainLoadingMode.EXTREME) {
            this.onClose();
            ci.cancel();
        }
    }
}
