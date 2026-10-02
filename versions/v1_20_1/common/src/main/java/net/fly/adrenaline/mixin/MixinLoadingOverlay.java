package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public class MixinLoadingOverlay {

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private long fadeOutStart;

    @Inject(method = "render", at = @At("HEAD"))
    private void adrenaline$keepStartupOverlayDuringWarmup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!this.fadeIn && this.fadeOutStart >= 0L && BackgroundWorldgenWarmup.isBlocking()) {
            this.fadeOutStart = Util.getMillis();
        }
    }
}
