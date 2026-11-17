package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NeoForgeLoadingOverlay.class, remap = false)
public class MixinNeoForgeLoadingOverlay {

    @Shadow
    private long fadeOutStart;

    @Inject(method = "render", at = @At("HEAD"))
    private void adrenaline$keepStartupOverlayDuringWarmup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.fadeOutStart >= 0L && BackgroundWorldgenWarmup.shouldKeepStartupOverlay(Minecraft.getInstance())) {
            this.fadeOutStart = Util.getMillis();
        }
    }
}
